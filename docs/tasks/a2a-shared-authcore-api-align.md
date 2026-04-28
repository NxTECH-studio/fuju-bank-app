# A2a: shared 層を AuthCore 実 API に合わせる

## メタ情報

- **Phase**: 1（最優先・直列）
- **並行起動**: ✅ ローカル AuthCore (`fuju-system-authentication` を `:8080` で起動) で単独着手可能
- **依存**: A1（BuildKonfig 化）が望ましいが必須ではない
- **同期点**: AuthCore リポジトリの `docs/api-summary.md` / `docs/openapi.yaml` を一次ソースとして参照（変更しない）

## 概要

既存 `AuthApi.kt` / `AuthDto.kt` は `/sessions` + body refresh_token 前提で書かれており、AuthCore の実 API (`/v1/auth/login` + HttpOnly cookie refresh) と完全に不整合。UI を載せる前にここを実態に合わせて全面改修する。

## 背景・目的

- AuthCore 実 API: `POST /v1/auth/login` body `{ identifier, password }` → 200 `{ access_token, token_type, expires_in }` + `Set-Cookie: refresh_token=...` (HttpOnly) または MFA 必要時 `{ pre_token, mfa_required: true, ... }`
- Refresh Token は HttpOnly cookie 配送。**ボディには含まれない**。Ktor `HttpCookies` plugin + 永続 cookie storage が必須。
- `identifier` はメール **or 公開ID (4-16 文字英数字)**。

## 影響範囲

- ファイル（書き換え）:
  - `shared/.../data/remote/api/AuthApi.kt`
  - `shared/.../data/remote/dto/AuthDto.kt`
  - `shared/.../data/repository/AuthRepository.kt`
  - `shared/.../auth/TokenStorage.kt`（責務再整理）
  - `shared/.../auth/TokenStorageFactory.{android,ios}.kt`（必要なら）
  - `shared/.../network/HttpClientFactory.kt`（共通: HttpCookies plugin install）
  - `shared/.../network/HttpClientFactory.{android,ios}.kt`（actual: 永続 CookiesStorage）
  - `shared/.../data/remote/ApiErrorCode.kt`（AuthCore のエラーコード追加）
  - `shared/.../di/AuthModule.kt`
- 新規:
  - `shared/.../auth/PersistentCookiesStorage.{android,ios}.kt`（expect/actual or 直接 actual）
  - `shared/.../data/remote/dto/MfaDto.kt`
  - `shared/.../data/repository/LoginResult.kt`（sealed class）
- テスト:
  - `shared/.../data/repository/AuthRepositoryTest.kt`（書き換え）

## 実装ステップ

### Step 1: DTO 改修

```kotlin
// AuthDto.kt
@Serializable
data class LoginRequest(val identifier: String, val password: String)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,  // "Bearer"
    @SerialName("expires_in") val expiresIn: Long,    // 900 (秒)
)

@Serializable
data class PreTokenResponse(
    @SerialName("pre_token") val preToken: String,
    @SerialName("mfa_required") val mfaRequired: Boolean,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Long,    // 600
)

// MfaDto.kt
@Serializable
data class MfaVerifyRequest(
    val code: String? = null,
    @SerialName("recovery_code") val recoveryCode: String? = null,
)
```

`LoginResult` (sealed):
```kotlin
sealed class LoginResult {
    data class Authenticated(val accessToken: String, val expiresIn: Long) : LoginResult()
    data class NeedsMfa(val preToken: String, val expiresIn: Long) : LoginResult()
}
```

### Step 2: AuthApi 改修

```kotlin
class AuthApi(
    private val client: HttpClient,
    private val authCoreBaseUrl: String,
) {
    suspend fun login(identifier: String, password: String): NetworkResult<LoginRawResponse> = ...
        // POST /v1/auth/login
        // 200 のレスポンスボディに pre_token があるかで分岐 (LoginRawResponse は両 DTO を Either で持つ)
    suspend fun mfaVerify(preToken: String, code: String?, recoveryCode: String?): NetworkResult<TokenResponse> = ...
        // POST /v1/auth/mfa/verify with Authorization: Bearer <preToken>
    suspend fun refresh(): NetworkResult<TokenResponse> = ...
        // POST /v1/auth/refresh (cookie が自動で乗る)
    suspend fun logout(): NetworkResult<Unit> = ...
        // POST /v1/auth/logout
}
```

`LoginRawResponse` は `JsonElement` で受けて `mfa_required` キー有無で sealed branch 振り分けが楽。

### Step 3: HttpCookies plugin 導入

`HttpClientFactory.kt` (commonMain) で plugin を install する設定 hook を `expect` で公開:
```kotlin
expect fun createCookiesStorage(): CookiesStorage  // Ktor の interface
```

`androidMain`:
```kotlin
class EncryptedSharedPrefsCookiesStorage(context: Context) : CookiesStorage { ... }
actual fun createCookiesStorage(): CookiesStorage =
    EncryptedSharedPrefsCookiesStorage(applicationContext)
```
`iosMain`: Keychain backed:
```kotlin
class KeychainCookiesStorage : CookiesStorage { ... }
```

`HttpClient` 生成時に:
```kotlin
install(HttpCookies) {
    storage = createCookiesStorage()
}
```

注意:
- cookie の `Path=/v1/auth` で AuthCore の他エンドポイントには付かないが、bank API ドメインは別なので汚染なし
- AuthCore の Refresh Cookie は `Max-Age=2592000` + `Expires=<RFC1123>` 併記（AuthCore PR #17 で persist 対応済）。Ktor の `HttpCookies` は両方解釈するので追加実装不要
- iOS の `URLSession` 系 cookie storage を使うと OS 側 cookie jar と混ざるため、**Ktor 独自の `CookiesStorage` 実装**（KeychainCookiesStorage）で分離する

### Step 4: TokenStorage 再整理 + AuthTokenRefresher 改修

- `TokenStorage` の責務: access_token / expires_at（任意で `mfa_verified` flag、`current_user_id` ULID）
- refresh_token は持たない（cookie storage に委譲）
- 書き換え後の API: `saveAccess(token, expiresAt)` / `loadAccess(): String?` / `clear()`

**`AuthTokenRefresher` interface 変更**: 既存 (`shared/.../network/AuthTokenRefresher.kt`) は `refresh(refreshToken: String): RefreshedTokens?` で body refresh 前提。cookie 化に伴い signature を変更:
```kotlin
fun interface AuthTokenRefresher {
    suspend fun refresh(): String?  // 新 access token または null（refresh 不能時）
}
```
- `RefreshedTokens` data class は `refreshToken` フィールドを削除（または class 自体を削除）
- Ktor の `Auth` plugin の `refreshTokens` ブロックは `BearerTokens(accessToken = ..., refreshToken = "")` で空文字を渡す（plugin の signature 上必須なため。実用上は cookie 経由の refresh なので未使用）
- `AuthRepository.refresh()` 内では `authApi.refresh()` (cookie 自動送信) を呼んで新 access を保存し、`AuthTokenRefresher.refresh()` の戻り値として返す

**MFA_REQUIRED の扱い**: 既存実装と同じく refresh で解決しないエラー（`MFA_REQUIRED` / `TOKEN_REVOKED` 等）は `null` を返し、SessionStore を `Unauthenticated` にしてログイン画面へ。

### Step 5: AuthRepository 改修

```kotlin
class AuthRepository(private val api: AuthApi, private val tokenStorage: TokenStorage) {
    suspend fun login(identifier: String, password: String): NetworkResult<LoginResult> = ...
    suspend fun verifyMfa(preToken: String, code: String? = null, recoveryCode: String? = null): NetworkResult<Unit> = ...
        // 成功時 access を保存
    suspend fun refresh(): NetworkResult<Unit>
    suspend fun logout(): NetworkResult<Unit>
}
```

### Step 6: ApiErrorCode に追加

AuthCore のエラーコード（README §3.3 抜粋）を `ApiErrorCode` enum に追加:
- `INVALID_CREDENTIALS`, `ACCOUNT_LOCKED`, `MFA_REQUIRED`, `TOTP_CODE_INVALID`, `RECOVERY_CODE_INVALID`, `RATE_LIMIT_EXCEEDED`, `TOKEN_EXPIRED`, `TOKEN_INVALID`, `MFA_NOT_ENABLED`, `MFA_ALREADY_ENABLED`

**`mfa_verified` の semantics 注意** (api-summary §4.4):
- `mfa_verified` は **per-token / per-session** のフラグ。「**`false` を MFA 未設定と解釈してはいけない**」(api-summary 明記)
- 同じユーザーでも別 token family なら値が異なりうる（social login 経由は MFA 未通過なので false）
- bank の `/ledger/transfer` 等 MFA 必須エンドポイントが `MFA_REQUIRED` (403) を返したら、**ログアウトせず** MFA verify 画面に再誘導する設計が望ましい（pre_token は持っていないので一度ログアウトして MFA 必須でログインし直すか、別途 step-up flow を実装）。MVP は単純に再ログインで OK。

### Step 7: テスト書き換え

- MockEngine で `Set-Cookie: refresh_token=...; Path=/v1/auth; HttpOnly` を返してログインフロー検証
- 続く `/v1/auth/refresh` 呼び出しで cookie が自動で乗ることを検証
- MFA 分岐: pre_token を受けて `/mfa/verify` で TokenResponse を返すパスを検証

## 検証チェックリスト

- [ ] `./gradlew :shared:allTests` 緑
- [ ] ローカル AuthCore (`docker compose up` from `fuju-system-authentication`) で:
  - register → login → access_token 取得
  - refresh で cookie 自動送信 → 新 access_token
  - logout で cookie 削除
- [ ] MFA 有効ユーザで pre_token → mfa/verify → access_token のフローが通る
- [ ] cookie がアプリ再起動後も永続化されている（Android: EncryptedSharedPrefs / iOS: Keychain）

## PR description テンプレート

```
## 同期通知（A2b 着手者向け）
- AuthApi.login() は LoginResult sealed を返すように変更
- TokenStorage から refresh_token は消えた（cookie 委譲）
- ApiErrorCode に MFA 系を追加
```
