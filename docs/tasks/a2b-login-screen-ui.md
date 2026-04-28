# A2b: ログイン画面 UI（iOS / Android）

## メタ情報

- **Phase**: 1
- **並行起動**: ❌ A2a (shared 層改修) merge 後に着手
- **依存**: A2a / backend B2（本番疎通のみ）
- **同期点**: backend B2 で確定する `POST /users/me` の I/O サンプルに合わせる

## 概要

shared 層が AuthCore 実 API に揃った状態で、iOS / Android のログイン UI を実装する。MFA 分岐 / lazy provisioning (`POST /users/me`) / セッション復元まで。

## 背景・目的

- 現状 `ContentView.swift` / `App.kt` は smoke test のみ。ログインを通さないと残りの機能 UI が成立しない。
- AuthCore のログインフローは「メール or 公開ID + password」 → 200 で `LoginResult.Authenticated | LoginResult.NeedsMfa` の sealed 分岐。

## 影響範囲

- iOS 新規:
  - `iosApp/iosApp/Features/Auth/LoginView.swift`
  - `iosApp/iosApp/Features/Auth/LoginViewModel.swift`
  - `iosApp/iosApp/Features/Auth/MfaVerifyView.swift`
  - `iosApp/iosApp/Features/Auth/MfaVerifyViewModel.swift`
  - `iosApp/iosApp/App/AppRoot.swift`（セッション分岐用ルート）
- Android 新規:
  - `composeApp/.../features/auth/LoginScreen.kt`
  - `composeApp/.../features/auth/LoginViewModel.kt`
  - `composeApp/.../features/auth/MfaVerifyScreen.kt`
  - `composeApp/.../features/auth/MfaVerifyViewModel.kt`
  - `composeApp/.../App.kt`（NavHost or 簡易 sealed 分岐）
- shared 新規:
  - `shared/.../session/SessionStore.kt`
  - `shared/.../data/remote/api/UserMeApi.kt`（`POST /users/me` / `GET /users/me`）
  - `shared/.../data/repository/UserRepository.kt`（拡張: `provisionMe()` / `getMe()`）
- 既存編集: `ContentView.swift` / `App.kt` から smoke test ボタンを削除

## 実装ステップ

### Step 1: SessionStore (shared)

```kotlin
sealed class SessionState {
    object Unauthenticated : SessionState()
    data class MfaPending(val preToken: String) : SessionState()
    data class Authenticated(val userId: String) : SessionState()
}

class SessionStore {
    private val _state = MutableStateFlow<SessionState>(SessionState.Unauthenticated)
    val state: StateFlow<SessionState> = _state.asStateFlow()
    fun setAuthenticated(userId: String) { ... }
    fun setMfaPending(preToken: String) { ... }
    fun clear() { ... }
    suspend fun bootstrap(authRepository: AuthRepository, userRepository: UserRepository)
        // アプリ起動時に呼ぶ。TokenStorage に access があれば検証→なければ refresh 試行→ダメなら Unauthenticated
}
```

`SessionModule.kt` で Koin に登録。

### Step 2: UserMeApi + Repository 拡張 (shared)

```kotlin
class UserMeApi(private val client: HttpClient, private val bankBaseUrl: String) {
    suspend fun upsertMe(name: String? = null, publicKey: String? = null): NetworkResult<User>
    suspend fun getMe(): NetworkResult<User>
}
```

`UserRepository` に `provisionMe()` / `getMe()` を追加。

### Step 3: iOS UI

`LoginView`:
- `TextField` で identifier (placeholder「メールアドレス または 公開ID」)
- `SecureField` で password
- ログインボタン → `viewModel.login()`
- ローディング / エラー表示

`LoginViewModel`:
- `@Published var error: String?`
- `func login()` → `authRepository.login()` → success の場合:
  - `Authenticated` → `userRepository.provisionMe()` (bank 側 lazy provision のみ) → SessionStore.setAuthenticated(userId)
  - **AuthCore のプロフィール (public_id / icon_url) は A3 のホーム画面で `GET /v1/user/profile` から別途取得する**（A2b では取らない、責務分離）
  - `NeedsMfa` → SessionStore.setMfaPending(preToken)

`MfaVerifyView`:
- TOTP 6 桁 OR Recovery Code 切替タブ
- 確認ボタン → `viewModel.verify()` → SessionStore.setAuthenticated

`AppRoot`:
- `SessionStore.state` を SwiftUI で observe (KMP の StateFlow → Swift 側 Combine 変換ヘルパが必要、Skie or 自前)
- Unauthenticated → LoginView / MfaPending → MfaVerifyView / Authenticated → HomeView (A3)

### Step 4: Android UI (Compose)

`LoginScreen` / `LoginViewModel` を MVVM で同等に実装。`SessionStore.state` を `collectAsState()` で受ける。

`composeApp/App.kt` の Material3 構造を維持しつつ、`when (sessionState)` で 3 画面分岐。

### Step 5: エラーハンドリング

- `INVALID_CREDENTIALS` → 「メールアドレス/公開ID または パスワードが間違っています」
- `ACCOUNT_LOCKED` → 「ログインが多すぎます。しばらく待ってから再試行してください」
- `RATE_LIMIT_EXCEEDED` → `Retry-After` ヘッダを表示
- `TOTP_CODE_INVALID` / `RECOVERY_CODE_INVALID` → MFA 画面側で個別表示
- ネットワーク失敗 → リトライボタン

### Step 6: smoke test ボタン削除

`ContentView.swift` / `App.kt` の `// TODO: remove after smoke test` 一式を削除。

## 検証チェックリスト

- [ ] iOS / Android で identifier=メール / 公開ID 両方でログイン成功
- [ ] MFA 有効ユーザでログイン → MFA 画面 → 認証成功
- [ ] ログイン直後に `POST /users/me` が叩かれている (HTTP ログ確認)
- [ ] アプリ再起動後にセッションが復元される (cookie + access_token 永続化)
- [ ] `INVALID_CREDENTIALS` / `ACCOUNT_LOCKED` / `RATE_LIMIT_EXCEEDED` の UI 表示
- [ ] ログアウト → cookie 削除 → 再ログイン要求

## 後続タスク

A3 / A4 / A6 がこの SessionStore.Authenticated を受けて自分の機能を実装する。
