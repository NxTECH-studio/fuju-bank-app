# Fuju Bank App

「感情を担保とする中央銀行」 fuju-bank のクライアントアプリ。Kotlin Multiplatform +
Compose Multiplatform で Android / iOS を単一コードベースから提供し、銀行層バックエンド
[fuju-bank-backend](https://github.com/NxTECH-studio/fuju-bank-backend) のフロントエンド層
に相当します。

鑑賞者がアート作品の前で滞留し・視線を向けた時間を、作品に「魂を削った」作家（User）へ
ふじゅ〜として還元する、というコンセプトを支える **作家向け HUD / 一般鑑賞者向け体験クライアント**
として設計されています。

## 3 層アーキテクチャでの位置づけ

本リポジトリは fuju-bank プロダクトの **3 層目（デモ SNS / 作家 HUD）** に属します。
銀行（1 層目）とマイニング（2 層目）から供給される残高・取引・リアルタイム着金通知を
受け取って表示するのが主責務です。

| 層 | リポジトリ | 責務 |
|---|---|---|
| 1 層目 **銀行** | [fuju-bank-backend](https://github.com/NxTECH-studio/fuju-bank-backend) | 発行・記帳・決済・配信の中央台帳（Rails 8.1 API） |
| 2 層目 **マイニング** | （別リポジトリ） | ブラウザ内 MediaPipe で視線・滞留をエッジ解析、重み付け計算 |
| 3 層目 **デモ SNS / 作家 HUD** | **本リポジトリ** | タイムライン滞留でマイニング、作家 HUD へ push 通知を受信 |

小数点の重み付け計算はマイニング層が担い、切り捨てた整数値だけが銀行に渡ります。
クライアント側は小数の計算には関与せず、銀行 API が返す整数（`bigint`）の残高・取引量を
そのまま表示します。

## 主な画面・機能（予定）

| 画面 / 機能 | 説明 |
|---|---|
| ログイン / サインアップ | AuthCore が発行する JWT を取得し、以降の API 呼び出しで `Authorization: Bearer <jwt>` として付与 |
| 残高ダッシュボード | `GET /users/:id` の `balance_fuju` を表示 |
| 取引履歴 | `GET /users/:id/transactions` の mint / transfer 統合ビューを表示 |
| リアルタイム着金通知 | ActionCable `UserChannel` を購読し、`credit` イベント（mint / transfer）を push 通知 / HUD で表示 |
| 送金（将来） | `POST /ledger/transfer`（Introspection + 将来的に `MFA_REQUIRED` 対象） |

「予定」と記載した通り、UI は未実装です。実装済み範囲は [現況（実装済み範囲）](#現況実装済み範囲) を
参照してください。

## 技術スタック

| カテゴリ | 技術 |
|---|---|
| 言語 | Kotlin 2.3.20（Multiplatform） |
| UI | Compose Multiplatform 1.10.3 / Material3 1.11.0-alpha06 |
| ビルド | Gradle (Kotlin DSL) + Version Catalog (`gradle/libs.versions.toml`) / AGP 8.11.2 / JDK 17 |
| ターゲット | `androidTarget()`（JVM 11） / `iosArm64()` / `iosSimulatorArm64()`（iOS framework baseName `Shared`, `isStatic = true`） |
| Android SDK | `minSdk = 24` / `targetSdk = 36` / `compileSdk = 36` |
| DI | Koin 4.2.1（`koin-core` / `koin-android` / `koin-test`） |
| HTTP クライアント | Ktor 3.4.2（`core` / `content-negotiation` / `logging` / `auth` / `websockets` / `okhttp`（androidMain） / `darwin`（iosMain） / `mock`（test）） |
| シリアライズ | `kotlinx-serialization-json` 1.9.0 |
| 非同期 | `kotlinx-coroutines` 1.10.2 |
| 日時 | `kotlinx-datetime` 0.7.1 |
| Android 暗号化保管 | `androidx.security:security-crypto` 1.1.0-alpha06 |
| ライフサイクル | `androidx-lifecycle` 2.10.0（`viewmodel-compose` / `runtime-compose`） |
| テスト | `kotlin-test`（`commonTest`） / `ktor-client-mock` / `kotlinx-coroutines-test` / `koin-test`（`androidUnitTest`） |

## プロジェクト構成

```
root/
├── composeApp/                                         # Compose Multiplatform アプリ（Android Compose UI; iOS UI は iosApp/ の SwiftUI）
│   └── src/
│       ├── androidMain/
│       │   ├── AndroidManifest.xml
│       │   └── kotlin/studio/nxtech/fujubank/
│       │       ├── FujuBankApp.kt                      # Application（Koin bootstrap）
│       │       ├── MainActivity.kt
│       │       └── App.kt                              # Splash → Login → MfaVerify → Root の遷移ハブ
│       ├── androidUnitTest/kotlin/                     # Android 向けユニットテスト
│       └── debug/                                      # Debug build variant 用リソース
├── shared/                                             # ドメイン / プラットフォーム抽象層
│   └── src/
│       ├── commonMain/kotlin/studio/nxtech/fujubank/
│       │   ├── auth/                                   # TokenStorage / TokenStorageFactory（expect） / PersistentCookiesStorage（expect）
│       │   ├── data/
│       │   │   ├── remote/                             # ApiError / ApiErrorCode / NetworkResult（フラット・ネスト両形式対応）
│       │   │   │   ├── api/                            # AuthApi / AuthCoreUserApi / UserApi / UserMeApi / LedgerApi / ArtifactApi / UserChannelClient
│       │   │   │   └── dto/                            # API 入出力 DTO（UserResponse は id: Long、sub nullable）
│       │   │   └── repository/                         # AuthRepository / UserRepository / ProfileRepository / LedgerRepository / ArtifactRepository / RealtimeRepository
│       │   ├── di/                                     # SharedModule / AuthModule / UserModule / SessionModule / SignupModule / AccountModule / LedgerModule / ArtifactModule / RealtimeModule / Qualifiers / BuildConfigFacade
│       │   ├── domain/model/
│       │   ├── account/                                # AccountProfileProvider（Remote / Dummy） / NotificationSettingsPreferences / PrivacyPreferences
│       │   ├── session/                                # SessionStore / SessionState / AuthErrorMessages / SessionResetCoordinator / TokenExpiryWatcher
│       │   └── network/                                # HttpClientFactory（expect, installAuth フラグあり） / AuthTokenRefresher
│       ├── androidMain/kotlin/studio/nxtech/fujubank/
│       │   ├── auth/                                   # EncryptedSharedPreferences 実装（actual: TokenStorage / PersistentCookiesStorage）
│       │   ├── di/AndroidPlatformModule.kt             # androidPlatformModule（bank 用 / AuthCore 用 2 つの HttpClient single）
│       │   └── network/                                # OkHttp 版 createHttpClient（actual）
│       └── iosMain/kotlin/studio/nxtech/fujubank/
│           ├── auth/                                   # Keychain 実装（actual: TokenStorage / PersistentCookiesStorage）
│           ├── di/
│           │   ├── IosPlatformModule.kt                # iosPlatformModule（bank 用 / AuthCore 用 2 つの HttpClient single）
│           │   └── KoinIos.kt                          # doInitKoin / userApi ファサード
│           └── network/                                # Darwin 版 createHttpClient（actual）
├── iosApp/                                             # iOS ネイティブエントリ（SwiftUI / Xcode）
│   └── iosApp/iOSApp.swift                             # init で KoinIosKt.doInitKoin() を呼ぶ
├── gradle/libs.versions.toml                           # Version Catalog
├── build.gradle.kts
└── settings.gradle.kts                                 # rootProject.name = "Fujubankapp"
```

- **モジュール境界**: UI は `composeApp`、API クライアント / モデル / 認証保管などドメイン処理は
  `shared` に寄せます。Android / iOS SDK 依存を `commonMain` に混入させません。
- **`expect` / `actual` の方針**: プラットフォーム固有 API が必要な場合のみ `commonMain` に
  `expect` を置き、`androidMain` / `iosMain` で `actual` を実装します。代表例は
  `TokenStorageFactory`（auth）、`createHttpClient`（network）の 2 つです。
- **依存追加**: `gradle/libs.versions.toml` に追記し、`build.gradle.kts` からは `libs.xxx` 経由で参照します。
- **`settings.gradle.kts`**: `include(":composeApp", ":shared")` / `TYPESAFE_PROJECT_ACCESSORS` を
  有効化しているため、プロジェクト参照は `projects.shared` のように書けます。

## アーキテクチャ

クライアントは以下の 4 層で構成します。図は用いず、責務表と文章で記述します。

### 層構造と責務

| 層 | 責務 | 代表クラス | ソースセット |
|---|---|---|---|
| UI 層 | 画面・状態管理。`NetworkResult` を解釈して表示に落とす | Compose 画面（未実装） / ViewModel（未実装） | `composeApp/commonMain` |
| Repository 層 | ユースケースに沿って API を束ねる。`NetworkResult` を透過させつつ副作用（トークン保存 / MFA イベント emit）を担う | `AuthRepository` / `UserRepository` / `LedgerRepository` / `ArtifactRepository` / `RealtimeRepository` | `shared/commonMain` |
| Api 層 | Ktor の薄いラッパ。`runCatchingNetwork` でエラーを `NetworkResult` に変換する | `AuthApi` / `UserApi` / `LedgerApi` / `ArtifactApi` / `UserChannelClient` | `shared/commonMain` |
| Platform 抽象層 | OS 依存の生成・永続化を `expect` / `actual` で分離する | `HttpClientFactory`（`createHttpClient`） / `TokenStorageFactory` / `TokenStorage` | `shared/commonMain` + `shared/androidMain` / `shared/iosMain` |

### データフロー

- **API 呼び出し**: UI → Repository → Api → Ktor `HttpClient`。`HttpClient` の `defaultRequest` が
  `baseUrl` と `Accept: application/json` を付与し、`Auth { bearer }` プラグインが
  `Authorization: Bearer <access>` を `TokenStorage.getAccessToken()` から補充します。
- **エラー変換**: Ktor の `ResponseException` は `runCatchingNetwork` が `ApiErrorEnvelope` を
  `body<>()` で復号し、`NetworkResult.Failure(ApiError)` に変換します。`CancellationException`
  は必ず再スローし、それ以外の `Throwable` は `NetworkResult.NetworkFailure` にまとめます。
- **401 時のリフレッシュ**: `HttpClientConfig.tokenRefresher` が指定されていれば Ktor の
  `refreshTokens` ブロックが発火し、成功時に `onTokensRefreshed` 経由で `TokenStorage` に
  新トークンを保存します（現状は DI で `tokenRefresher = null`、フックのみ用意済み）。
- **ログイン**: `AuthApi.login(email, password)` → `TokenResponse` を `TokenStorage.save(access, refresh, subject)`
  で永続化。`MFA_REQUIRED` が返った場合は `AuthRepository.mfaRequiredEvents`（`SharedFlow<Unit>`,
  `BufferOverflow.DROP_OLDEST`）に emit します。
- **ActionCable 購読**: `UserChannelClient.subscribe(userId)` が `Flow<CreditEventDto>` を返し、
  `channelFlow` 内で WebSocket を張って subscribe コマンドを送信、受信フレームから `credit`
  イベントのみを emit します。詳細は [API 仕様](#api-仕様) の「ActionCable」節を参照。

## DI（Koin）構成と bootstrap

DI は Koin 4.2.1 を使用します。`shared` が提供する Koin モジュールを、プラットフォーム側の
`androidPlatformModule` / `iosPlatformModule` が supply する構成です。

### `initKoin` の契約

```kotlin
fun initKoin(
    cableUrl: String,                                   // ws:// または wss:// のみ許可（require で拒否）
    appDeclaration: KoinAppDeclaration = {},            // androidContext(...) など platform 固有設定
): KoinApplication
```

- `cableUrl` が `ws://` / `wss://` で始まらない場合は `IllegalArgumentException` を投げます。
- プロセス内で **1 度だけ** 呼び出してください。2 回目は Koin が
  `KoinApplicationAlreadyStartedException` を投げます。
- テストでは `Module.verify` を使い、`initKoin` は呼びません。

### モジュール一覧

| モジュール | 主な供給物 | ソースセット |
|---|---|---|
| `authModule` | `TokenStorage`（`TokenStorageFactory.create()` 経由）/ `AuthApi(get(qualifier = AUTHCORE_CLIENT_QUALIFIER), defaultAuthCoreBaseUrl())` / `AuthRepository` / `AuthTokenRefresher` | `shared/commonMain` |
| `userModule` | `UserApi` / `UserMeApi` / `UserRepository` / `AuthCoreUserApi` / `ProfileRepository` | `shared/commonMain` |
| `ledgerModule` | `LedgerApi` / `LedgerRepository` | `shared/commonMain` |
| `artifactModule` | `ArtifactApi` / `ArtifactRepository` | `shared/commonMain` |
| `realtimeModule(cableUrl)` | `cableUrl`（`CABLE_URL_QUALIFIER`） / `CoroutineScope`（`APP_SCOPE_QUALIFIER`, `SupervisorJob + Dispatchers.Default`） / `UserChannelClient` / `RealtimeRepository` | `shared/commonMain` |
| `sessionModule` | `SessionStore`（プロセス内で唯一のセッション状態ホルダー） / `SessionResetCoordinator`（`Authenticated → Unauthenticated` 遷移を観測して `AccountProfileProvider.reset()` を発火） / `TokenExpiryWatcher`（resume 契機の on-demand expiry チェック。`nowMillis = { Clock.System.now().toEpochMilliseconds() }`） | `shared/commonMain` |
| `signupModule` | `SignupCompletionSignal` ほか signup フロー連携用 single | `shared/commonMain` |
| `accountModule` | `SignupWelcomePreferences` ほかアカウント設定連携用 single | `shared/commonMain` |
| `androidPlatformModule` | `TokenStorageFactory(androidContext())` / `PersistentCookiesStorageFactory` / `CookiesStorage`（single, bank / AuthCore で共有） / Bearer 用 `HttpClient`（OkHttp + `installAuth = true`） / AuthCore 用 `HttpClient(AUTHCORE_CLIENT_QUALIFIER)`（OkHttp + `installAuth = false`） | `shared/androidMain` |
| `iosPlatformModule` | `TokenStorageFactory()` / `PersistentCookiesStorageFactory` / `CookiesStorage`（single, bank / AuthCore で共有） / Bearer 用 `HttpClient`（Darwin + `installAuth = true`） / AuthCore 用 `HttpClient(AUTHCORE_CLIENT_QUALIFIER)`（Darwin + `installAuth = false`） | `shared/iosMain` |

`sharedModules(cableUrl)` は `authModule` / `userModule` / `sessionModule` / `signupModule` /
`accountModule` / `ledgerModule` / `realtimeModule(cableUrl)` / `artifactModule` をまとめて返すヘルパです
（`shared/commonMain/.../di/SharedModule.kt`）。

### `appScope` と qualifier

- `appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)` はプロセス全体で 1 つだけ
  生成し、`APP_SCOPE_QUALIFIER`（`named("appScope")`）で取り出します。
- キャンセルはプラットフォーム側のライフサイクルに委ねています（shared 側で自動 cancel はしません）。
- `cableUrl` は `CABLE_URL_QUALIFIER`（`named("cableUrl")`）付き single として登録し、
  `UserChannelClient` に注入します。
- `AUTHCORE_CLIENT_QUALIFIER`（`named("authCoreClient")`）は AuthCore `/v1/auth` 配下
  （login / refresh / logout / mfaVerify）専用の `HttpClient` を取り出すために使います。
  Auth プラグインを外して登録することで、refresh が 401 を返した瞬間に `refreshTokens` ブロックが
  再帰起動し `refreshTokensDeferred` の自己 await で deadlock する事象を回避しています。
  bank API / AuthCore `/v1/user/profile` 用の bearer 認証クライアントとは別 single です。

### Android の bootstrap 経路

- `composeApp/androidMain/.../FujuBankApp.kt` の `Application.onCreate()` で以下を実行します。

  ```kotlin
  initKoin(cableUrl = CABLE_URL) {
      androidContext(this@FujuBankApp)
      modules(androidPlatformModule)
  }
  ```

- `AndroidManifest.xml` で `<application android:name=".FujuBankApp" ... />` として登録済みです。
- `CABLE_URL` は `BuildKonfig.CABLE_URL` 経由で渡しており、現状は `wss://api.fujupay.app/cable`
  （default / release flavor とも同じ本番値）。

### iOS の bootstrap 経路

- `shared/iosMain/.../di/KoinIos.kt` の `doInitKoin()` が `initKoin(cableUrl = CABLE_URL) { modules(iosPlatformModule) }`
  を呼びます。`CABLE_URL` は Android と同じく `BuildKonfig.CABLE_URL`（`wss://api.fujupay.app/cable`）を使います。
- Swift 側では `iosApp/iosApp/iOSApp.swift` の `init` で `KoinIosKt.doInitKoin()` を呼びます。
- Kotlin ファイル名 `KoinIos.kt` は Obj-C 公開時に自動で `KoinIosKt` という suffix 付きクラスに
  なります（Kotlin/Native → Obj-C interop の命名規則）。
- `userApi(): UserApi` は Swift から Koin グラフ上の `UserApi` を取り出すためのファサードです
  （Koin を Swift から直接触ると型付けが煩雑なため用意）。

## API 仕様

バックエンド側の詳細は [fuju-bank-backend](https://github.com/NxTECH-studio/fuju-bank-backend)
の README を参照してください。ここではクライアント側が呼び出す範囲を掲載します。

### 連携する API（fuju-bank-backend）

| Method | Path | 用途 | 認証 | クライアント呼び出し |
|---|---|---|---|---|
| `POST` | `/users` | User 作成（旧経路、将来削除予定） | AuthCore JWT | `UserApi.create(request)` |
| `GET` | `/users/:id` | User 情報 + 残高取得 | AuthCore JWT | `UserApi.get(userId)` |
| `POST` | `/users/me` | 自分の lazy provision（初回ログイン時に新規作成、既存なら no-op） | AuthCore JWT | `UserMeApi.upsertMe(...)` |
| `GET` | `/users/me` | 自分の最新状態取得（残高・名前・public_key） | AuthCore JWT | `UserMeApi.getMe()` |
| `GET` | `/users/:id/transactions` | 取引履歴（mint / transfer 統合） | AuthCore JWT | `UserApi.transactions(userId)` |
| `GET` | `/artifacts/:id` | Artifact 情報 | AuthCore JWT | `ArtifactApi`（`GET /artifacts/:id`） |
| `POST` | `/ledger/transfer` | 送金（User → User） | AuthCore JWT + introspection | `LedgerApi.transfer(...)` |

`BANK_API_BASE_URL` は `BuildKonfig.BANK_API_BASE_URL`（`https://api.fujupay.app`）を
`shared/commonMain/.../di/BuildConfigFacade.kt` の `defaultBankApiBaseUrl()` 経由で参照します。

### 連携する API（AuthCore: `fuju-system-authentication`）

| Method | Path | 用途 | 認証 | クライアント呼び出し |
|---|---|---|---|---|
| `POST` | `/v1/auth/login` | identifier（メール / public_id）+ password でログイン。MFA 必須なら `pre_token` を返す | なし | `AuthApi.login(identifier, password)` |
| `POST` | `/v1/auth/mfa/verify` | TOTP / リカバリコードで MFA を完了し access_token を発行 | `pre_token`（Bearer） | `AuthApi.mfaVerify(preToken, code, recoveryCode)` |
| `POST` | `/v1/auth/refresh` | HttpOnly cookie 経由で access_token を再発行 | `refresh_token` cookie | `AuthApi.refresh()` |
| `POST` | `/v1/auth/logout` | refresh_token family を revoke | access（Bearer）+ cookie | `AuthApi.logout()` |
| `GET` | `/v1/user/profile` | AuthCore 側プロフィール（id / email / public_id / icon_url / mfa_enabled） | access（Bearer） | `AuthCoreUserApi.getProfile()` |

`AUTHCORE_BASE_URL` は `BuildKonfig.AUTHCORE_BASE_URL`（`https://auth.fujupay.app`）を
`defaultAuthCoreBaseUrl()` 経由で参照します。`/v1/auth` 配下は cookie / pre_token / credentials 認証で
あり、bearer access_token は要らないため、Auth プラグインを外した専用 `HttpClient`
（`AUTHCORE_CLIENT_QUALIFIER`）に注入されます。`/v1/user/profile` は bearer 認証なので bank API
と同じ Auth プラグイン入りクライアントを使います。

### `NetworkResult<T>` の 3 状態

`NetworkResult<T>` は sealed class で、呼び出し側は `when` で網羅します。

| 状態 | 意味 | 発生条件 |
|---|---|---|
| `Success<T>(value)` | 正常レスポンス | HTTP 2xx + body の deserialize 成功 |
| `Failure(ApiError)` | バックエンドのエラーレスポンス | Ktor の `ResponseException`（`expectSuccess = true`）を `JsonElement` 経由でパース。`error` フィールドが `JsonObject` ならネスト形式（bank API）、`JsonPrimitive` ならフラット形式（AuthCore）として両方を受理 |
| `NetworkFailure(Throwable)` | それ以外の例外 | `ResponseException` 以外（タイムアウト / 接続失敗 / パース失敗など） |

`runCatchingNetwork { ... }` が 3 状態への振り分けを担当します。`CancellationException` は
再スローし、上位の coroutine cancel を妨げません。

### 統一エラーレスポンスと `ApiErrorCode`

bank API はネスト形式を返します。

```json
{
  "error": {
    "code": "INSUFFICIENT_BALANCE",
    "message": "残高が不足しています"
  }
}
```

AuthCore はフラット形式を返します。

```json
{"error":"INVALID_CREDENTIALS","message":"invalid credentials"}
```

`runCatchingNetwork` は両形式を受理し、同じ `ApiError(code, message, httpStatus)` に正規化します。
`ApiErrorCode` enum は以下のコードを持ちます。`fromString` で未知コードはすべて `UNKNOWN` に
マップされるため、新コード追加時にクライアントがクラッシュすることはありません。

| コード | 用途 |
|---|---|
| `VALIDATION_FAILED` | バリデーション失敗 |
| `NOT_FOUND` | リソース不在 |
| `INSUFFICIENT_BALANCE` | 送金時の残高不足 |
| `UNAUTHENTICATED` | JWT 無効 / 欠落 |
| `TOKEN_INACTIVE` | introspection で `active=false`（revoke 済み） |
| `AUTHCORE_UNAVAILABLE` | AuthCore への問い合わせが 5xx / タイムアウト |
| `INVALID_CREDENTIALS` | login の identifier / password 不一致 |
| `ACCOUNT_LOCKED` | login 試行回数超過によるロック |
| `MFA_REQUIRED` | MFA 未検証トークンで MFA 必須 action を叩いた |
| `TOTP_CODE_INVALID` | mfa/verify の TOTP コード不正 |
| `RECOVERY_CODE_INVALID` | mfa/verify のリカバリコード不正 |
| `RATE_LIMIT_EXCEEDED` | レート制限到達 |
| `TOKEN_EXPIRED` | access / refresh / pre_token 期限切れ |
| `TOKEN_INVALID` | トークン形式不正・cookie 欠落 |
| `TOKEN_REVOKED` | refresh family が失効済み |
| `MFA_NOT_ENABLED` | MFA 操作だが対象 user で未有効 |
| `MFA_ALREADY_ENABLED` | MFA 有効化を二重に行おうとした |
| `UNKNOWN` | 上記以外 / 未知コード |

### Idempotency-Key

`POST /ledger/transfer` は **`Idempotency-Key` ヘッダと body の `idempotency_key` の両方に
同じ UUID を入れる** 実装です（`LedgerApi.transfer`）。

- デフォルトの key 生成は `Uuid.random().toString()`（`@OptIn(ExperimentalUuidApi::class)`）。
  ファクトリは `LedgerApi(client, idempotencyKeyFactory = { ... })` でテスト時に差し替えられます。
- リトライ時は呼び出し側が **同一キーを再利用** してください。バックエンドは `ledger_transactions.idempotency_key`
  のユニーク制約で重複受信を吸収し、既存トランザクションをそのまま返します。

### ActionCable（`UserChannel`）

`UserChannelClient.subscribe(userId): Flow<CreditEventDto>` が WebSocket 接続・購読・再接続までを
まとめて面倒を見ます。

- 接続先は `cableUrl`（DI で `CABLE_URL_QUALIFIER` として注入）。`BuildKonfig.CABLE_URL`
  経由で `wss://api.fujupay.app/cable` を渡しています。
- 接続確立後、以下の subscribe コマンドを Text frame で送信します。`identifier` は
  **JSON 文字列を値として持つ JSON フィールド** という二重エンコードです。

  ```json
  {
    "command": "subscribe",
    "identifier": "{\"channel\":\"UserChannel\",\"user_id\":\"<userId>\"}"
  }
  ```

- 制御フレーム（`welcome` / `ping` / `confirm_subscription` / `disconnect`）は
  `envelope.type != null` で弾き、`message` 側のみデコードします。
- デコード後、`type == "credit"` のペイロードだけを emit します（`mint` / `transfer` どちらも
  ここに届きます）。

`credit` ペイロード例（バックエンド側の broadcast 形状）:

```json
{
  "type": "credit",
  "amount": 15,
  "transaction_id": 42,
  "transaction_kind": "mint",
  "artifact_id": 7,
  "from_user_id": null,
  "metadata": { "dwell_seconds": 12, "gaze_strength": 0.8 },
  "occurred_at": "2026-04-18T12:34:56Z"
}
```

**再接続ポリシー**: `retryWhen` で指数バックオフ（1s → 2s → 4s → 8s → 16s → 30s 上限）。
`CancellationException` のときは再接続しません。`.buffer()` を挟んでバックプレッシャを吸収します。

## `error.code` のクライアントハンドリング方針

`NetworkResult.Failure(ApiError)` を受けたときの扱い方針を整理します。UI 連携の実装は
段階的に追加していく想定です。

| コード | クライアント挙動（方針 / 実装状況） |
|---|---|
| `UNAUTHENTICATED` / `TOKEN_INACTIVE` / `TOKEN_INVALID` / `TOKEN_REVOKED` | 実装済み: Ktor Auth プラグインの `refreshTokens` が `AuthTokenRefresher` を呼び自動リフレッシュ。失敗時は `SessionStore` を `Unauthenticated` に戻してログイン画面へ |
| `INVALID_CREDENTIALS` | 実装済み: `AuthErrorMessages.forLogin` が「メールアドレス/公開ID または パスワードが間違っています」を返す |
| `ACCOUNT_LOCKED` / `RATE_LIMIT_EXCEEDED` | 実装済み: `AuthErrorMessages.forLogin` がしばらく待つよう案内 |
| `MFA_REQUIRED` | 実装済み: `AuthRepository.login` が `LoginResult.NeedsMfa(preToken)` を返し、`SessionStore.setMfaPending(preToken)` 経由で `MfaVerifyScreen` に遷移 |
| `TOTP_CODE_INVALID` / `RECOVERY_CODE_INVALID` / `TOKEN_EXPIRED` | 実装済み: `AuthErrorMessages.forMfa` で個別文言 |
| `INSUFFICIENT_BALANCE` | 送金フォームでバリデーションメッセージとして表示（送金 UI は MVP 範囲外） |
| `AUTHCORE_UNAVAILABLE` | 一時的障害としてリトライ誘導 |
| `VALIDATION_FAILED` / `NOT_FOUND` | `ApiError.message` をそのまま表示 |
| `UNKNOWN` | 汎用エラー表示 |

`NetworkResult.NetworkFailure(cause)` はネットワーク断（接続失敗 / タイムアウト / パース失敗
など）として扱い、リトライ可能な UI を出します。

## 認証フロー（AuthCore 連携）

認証基盤は別リポジトリの **AuthCore**（`fuju-system-authentication`、JWT RS256 + introspection 併用）
です。AuthCore は access_token を JSON で、refresh_token を **HttpOnly cookie** で配送する設計で、
クライアントは refresh_token 文字列を直接扱いません。bank 側は AuthCore access_token の `sub` を
external_user_id として読み、内部で連番 `id` の user 行を `lazy provision`（`POST /users/me`）します。
クライアントの `User.id` は文字列（`bank.id.toString()`）で扱います。

### 処理の流れ

1. **ログイン**: `AuthApi.login(identifier, password)` →
   - 通常の場合は `TokenResponse(access_token, expires_in, ...)` を受け、`TokenStorage.saveAccess(token, expiresAt)` で access のみ保存。同時にサーバが `Set-Cookie: refresh_token=...` を返すため `PersistentCookiesStorage` が永続化する。
   - MFA が必須の user は `PreTokenResponse(pre_token, mfa_required, ...)` が返り、`AuthRepository.login` が `LoginResult.NeedsMfa(preToken)` を返す。`SessionStore.setMfaPending(preToken)` 経由で `MfaVerifyScreen` に遷移する。
2. **MFA 検証**: `AuthApi.mfaVerify(preToken, code = totp, recoveryCode = ...)` で TOTP / リカバリコードを送り、`TokenResponse` を受けて `TokenStorage.saveAccess` する。`pre_token` は Bearer ヘッダで明示的に付与する（Auth プラグイン経由ではない）。
3. **API 呼び出し**: bank API および AuthCore `/v1/user/profile` は Auth プラグイン入りクライアント経由。`loadTokens` が `TokenStorage.loadAccess()` を読んで `Authorization: Bearer <access>` を自動付与する。
4. **リフレッシュ**: 401 時に Ktor の `refreshTokens` が発火し、`AuthTokenRefresher.refresh()` が `AuthRepository.refresh()` → `AuthApi.refresh()`（`AUTHCORE_CLIENT_QUALIFIER` 経由）を呼ぶ。AuthCore は HttpOnly cookie 経由で `refresh_token` を読み、新しい access を JSON で返す。`TokenStorage.saveAccess` で更新後、Ktor が新 `BearerTokens` で元リクエストをリトライする。
5. **401 / 403**: `ResponseException` → `runCatchingNetwork` → `NetworkResult.Failure(ApiError)`。
   `ApiErrorCode` に応じて前節の方針で処理します。
6. **ログアウト**: `AuthRepository.logout()` → `AuthApi.logout()` でサーバ側 refresh family を revoke し、`TokenStorage.clear()` で local の access を破棄。cookie は `PersistentCookiesStorage` 側に残るが、access 無しでは認証済み扱いにならない。
7. **resume 契機の事前リフレッシュ**: `TokenExpiryWatcher.checkNow()` を Android `Lifecycle.Event.ON_RESUME` / iOS `ScenePhase = .active` から呼ぶ。`expiresAt - 60s` を過ぎていれば `AuthRepository.refresh()` を kick する。401-driven の自動リフレッシュ（4. の `refreshTokens`）は「リクエスト時に失敗してから」のリアクティブ動作だが、こちらは「foreground 復帰時に先回りで」走らせるプロアクティブ動作。`refresh` が API エラーを返したら `SessionStore` を `Unauthenticated` に倒す（`NetworkFailure` のときは何もせず、次の resume / 401-driven refresh に委ねる ─ 圏外復帰で毎回ログアウトされる UX を避けるため）。
8. **logout / refresh 失敗時のローカルキャッシュ破棄**: `SessionResetCoordinator` がアプリ起動時に `start()` され、`SessionStore.state` を観測して `Authenticated → Unauthenticated` の遷移エッジで `AccountProfileProvider.reset()` を呼ぶ。VM 内 state は画面 unmount で自然破棄されるが、Koin singleton として保持しているプロフィールキャッシュは明示的に reset しないと前ユーザーの情報が残るためこの調停役を置いている。`Unauthenticated` 起動時を logout と誤認しないよう、直前状態を保持して遷移エッジでのみ発火する設計。

### トークン保管（`expect` / `actual`）

`TokenStorage` は access_token 専用の suspend API（`loadAccess` / `loadExpiresAt` / `saveAccess` /
`clear`）で、`TokenStorageFactory` を `expect class` として共有インターフェースを定義しています。
refresh_token は HttpOnly cookie のため `TokenStorage` では扱わず、`PersistentCookiesStorage` 側で
永続化します（Android: `EncryptedSharedPreferences` ファイル `fuju_cookies`、iOS: Keychain）。

| プラットフォーム | TokenStorage 実装 | 鍵・アクセシビリティ |
|---|---|---|
| Android | `EncryptedSharedPreferences`（ファイル名 `fuju_tokens`） | `MasterKey.KeyScheme.AES256_GCM` + `PrefKeyEncryptionScheme.AES256_SIV` / `PrefValueEncryptionScheme.AES256_GCM`。I/O は `Dispatchers.IO` |
| iOS | Keychain（`kSecClassGenericPassword`） | `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` / service `studio.nxtech.fujubank` / account `access`。`ExperimentalForeignApi` + CoreFoundation / Security cinterop |

### HTTP クライアントの共通構成

`network/HttpClientFactory.kt` の `applyCommon(config)` がすべてのプラットフォーム共通で以下を
適用します（`createHttpClient` は Android: OkHttp / iOS: Darwin の `expect` / `actual`）。

- `expectSuccess = true`（非 2xx で `ResponseException` を投げる）
- `ContentNegotiation` + kotlinx Json（`ignoreUnknownKeys = true` / `explicitNulls = false`）
- `Logging`（`enableLogging` で BODY / HEADERS を切替。`Authorization` / `Cookie` / `Set-Cookie` / `Proxy-Authorization` は `sanitizeHeader` でマスク）
- `HttpTimeout`（request 30s / connect 10s / socket 30s）
- `WebSockets`（ActionCable で使用）
- `HttpCookies`（`PersistentCookiesStorage` を `storage` に注入し、`refresh_token` cookie を永続化）
- `Auth { bearer { loadTokens / refreshTokens } }` — ただし `installAuth = true` のクライアントのみ
- `defaultRequest { url(baseUrl); Accept: application/json }`

`HttpClientConfig.installAuth: Boolean = true` で Auth プラグインの取り付けを切り替える設計です。
プロセス内では 2 つの `HttpClient` が共存します。

| 用途 | qualifier | `installAuth` | 認証経路 |
|---|---|---|---|
| bank API（`/users` / `/users/me` / `/ledger/transfer` 等）+ AuthCore `/v1/user/profile` | デフォルト single | `true` | `Authorization: Bearer <access>` を自動付与。401 で `refreshTokens` を起動 |
| AuthCore `/v1/auth/*`（login / refresh / logout / mfaVerify） | `AUTHCORE_CLIENT_QUALIFIER` | `false` | cookie / pre_token / credentials のみ。Auth プラグインを外して再帰 deadlock を回避 |

`CookiesStorage` は Koin single として登録し、両クライアントで同じインスタンスを共有します
（mutex / 在メモリ状態を分離させない）。

## 環境変数・build variant

環境固有の値は **BuildKonfig**（`buildkonfig` Gradle プラグイン）で `shared/build.gradle.kts` 内に
宣言し、`shared/build/buildkonfig/commonMain/.../BuildKonfig.kt` に生成されます。クライアントは
`shared/commonMain/.../di/BuildConfigFacade.kt` の `defaultBankApiBaseUrl()` /
`defaultCableUrl()` / `defaultAuthCoreBaseUrl()` 経由で参照します。

| フィールド | default 値 | release flavor 値 | 参照経路 |
|---|---|---|---|
| `BANK_API_BASE_URL` | `https://api.fujupay.app` | 同左 | `defaultBankApiBaseUrl()` |
| `CABLE_URL` | `wss://api.fujupay.app/cable` | 同左 | `defaultCableUrl()` |
| `AUTHCORE_BASE_URL` | `https://auth.fujupay.app` | 同左 | `defaultAuthCoreBaseUrl()` |
| `USE_DUMMY_PROFILE` | `local.properties` の `useDummyProfile`（既定 `false`） | 強制 `false` | `BuildKonfig.USE_DUMMY_PROFILE` |

MVP 段階では default / release で URL は同じ本番値です。release flavor のブロックを残しているのは
`USE_DUMMY_PROFILE=false` を強制してダミーデータが本番ビルドに混入する事故を防ぐためです。

`shared/build.gradle.kts` 上部の `triggersRelease` ロジックが、Android `assembleRelease` /
`bundleRelease` や iOS の Release framework リンク等のタスク名を検出した場合に
`-Pbuildkonfig.flavor=release` 相当を自動でセットします（Xcode の Scheme で Release を選んだ場合は
`CONFIGURATION` 環境変数も併せて見ます）。

`local.properties` に `useDummyProfile=true` を入れると debug ビルドだけでダミーデータ経路に
切り替わります。バックエンド未起動でも HomeScreen / 取引履歴を観察できるため、UI 単体確認に使います
（release flavor では強制 false なので本番ビルドへの混入はありません）。

## セットアップ

### 前提

- JDK 17 以上（推奨: JDK 17）
- Android Studio（Koala 以降推奨）
- Xcode（iOS 側をビルドする場合）
- macOS（iOS フレームワーク生成を行う場合）

debug / release ビルドとも本番 API（`*.fujupay.app`）を直接叩くため、ローカルバックエンドの
起動は **不要** です。バックエンド未起動でも UI を触りたい場合は `local.properties` に
`useDummyProfile=true` を書くと、`UserRepository` がダミーデータ経路に切り替わります。
ログイン画面はバックエンドへ実際にアクセスするので、ダミーモードでも本番 AuthCore に
登録済みのアカウントが必要です（`debug` ビルドのみ Login 画面に「認証スキップ」CTA が
出ます; release では存在しません）。

### Android

開発版ビルドは IDE の Run から、またはコマンドラインから実行できます。

```bash
./gradlew :composeApp:assembleDebug
```

### iOS

`iosApp/` を Xcode で開き、実機 / シミュレータで実行します。Kotlin 側フレームワークのみを先に
リンクしたい場合:

```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

## 検証コマンド早見表

| 目的 | コマンド |
|---|---|
| 全ターゲットのビルド確認 | `./gradlew build` |
| ユニットテスト（共通） | `./gradlew :shared:allTests` |
| Android デバッグ APK | `./gradlew :composeApp:assembleDebug` |
| iOS シミュレータ向けフレームワーク | `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` |
| Gradle デーモン再起動 | `./gradlew --stop` |

## 現況（実装済み範囲）

「予定」と「実装済み」が混在しているため、ここで状況を明示します。

| 領域 | 状況 |
|---|---|
| Koin bootstrap（Android / iOS） | 実装済み。`FujuBankApp` / `KoinIos.doInitKoin` が起動経路。`sharedModules(cableUrl)` で auth / user / session / signup / account / ledger / realtime / artifact をまとめて load |
| API クライアント（AuthCore） | 実装済み: `AuthApi`（login / mfaVerify / refresh / logout）/ `AuthCoreUserApi`（getProfile） |
| API クライアント（bank） | 実装済み: `UserApi`（create / get / transactions）/ `UserMeApi`（upsertMe / getMe）/ `LedgerApi`（transfer + Idempotency-Key）/ `ArtifactApi` |
| TokenStorage / Cookie | 実装済み: Android（`EncryptedSharedPreferences`）/ iOS（Keychain）。access_token は `TokenStorage`、refresh_token は HttpOnly cookie + `PersistentCookiesStorage` |
| ActionCable | `UserChannelClient` 実装済み（指数バックオフ 1s→30s、制御フレーム破棄、`credit` のみ emit）。`shareIn` による共有化は未着手 |
| UI（Android / iOS 両対応） | 実装済み: ログイン / サインアップ（メール → OTP → 完了） / ホーム（残高 + 取引履歴） / 取引詳細 / 設定（アカウント情報・プライバシー・パスワード変更・通知許可） |
| UI（未実装） | 送金フォーム / HUD / Artifact 投稿（MVP 範囲外: `project_mvp_scope.md` 参照、受け取り専用 MVP） |
| baseUrl / cableUrl | BuildKonfig 経由化済み。debug / release とも本番（`*.fujupay.app`）を向く |
| MFA / リフレッシュトークン | 実装済み: MFA は `AuthRepository.login` → `LoginResult.NeedsMfa` → `MfaVerifyScreen` の経路。リフレッシュは Auth プラグインの `refreshTokens` + `AuthTokenRefresher`（`AUTHCORE_CLIENT_QUALIFIER` 経由のため自己再帰 deadlock しない） |
| access_token の proactive 期限監視 | 実装済み: `TokenExpiryWatcher.checkNow()` を Android `Lifecycle.Event.ON_RESUME` / iOS `ScenePhase = .active` から呼ぶ。`DEFAULT_REFRESH_THRESHOLD_MS = 60_000` 以内なら refresh を先回り起動。API 失敗時は session を `Unauthenticated` に倒し、`NetworkFailure` 時は no-op |
| logout 時のローカル state 破棄 | 実装済み: `SessionResetCoordinator` をアプリ起動時に `start()` し、`Authenticated → Unauthenticated` の遷移エッジで `AccountProfileProvider.reset()` を発火。Koin singleton 経由のプロフィールキャッシュにユーザー切替時の残骸が出ないことを保証 |
| CI | テストとビルドで job を分離: `build-and-check`（Android assemble + lint）/ `build-ios-framework` / `build-ios-app`（xcodebuild）/ `test-jvm`（`:shared:testDebugUnitTest`）/ `test-ios`（`:shared:iosSimulatorArm64Test`）の 5 job 構成。Pull Request トリガー固定で `concurrency: cancel-in-progress` 有効 |

## 関連リポジトリ

- [NxTECH-studio/fuju-bank-backend](https://github.com/NxTECH-studio/fuju-bank-backend) — 銀行層バックエンド（Rails 8.1 API）

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html).
