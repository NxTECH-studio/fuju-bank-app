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
├── composeApp/                                         # Compose Multiplatform アプリ（現状は Android ターゲットのみ宣言）
│   └── src/
│       ├── androidMain/
│       │   ├── AndroidManifest.xml
│       │   └── kotlin/studio/nxtech/fujubank/
│       │       ├── FujuBankApp.kt                      # Application（Koin bootstrap）
│       │       ├── MainActivity.kt
│       │       └── App.kt
│       ├── androidUnitTest/kotlin/                     # Android 向けユニットテスト
│       └── debug/                                      # Debug build variant 用リソース
├── shared/                                             # ドメイン / プラットフォーム抽象層
│   └── src/
│       ├── commonMain/kotlin/studio/nxtech/fujubank/
│       │   ├── auth/                                   # TokenStorage / TokenStorageFactory（expect）
│       │   ├── data/
│       │   │   ├── remote/                             # ApiError / ApiErrorCode / NetworkResult / NetworkConstants
│       │   │   │   ├── api/                            # AuthApi / UserApi / LedgerApi / ArtifactApi / UserChannelClient
│       │   │   │   └── dto/                            # API 入出力 DTO
│       │   │   └── repository/                         # AuthRepository / UserRepository / LedgerRepository / ArtifactRepository / RealtimeRepository
│       │   ├── di/                                     # SharedModule / AuthModule / UserModule / LedgerModule / ArtifactModule / RealtimeModule / Qualifiers
│       │   ├── domain/model/
│       │   └── network/                                # HttpClientFactory（expect） / AuthTokenRefresher
│       ├── androidMain/kotlin/studio/nxtech/fujubank/
│       │   ├── auth/                                   # EncryptedSharedPreferences 実装（actual）
│       │   ├── di/AndroidPlatformModule.kt             # androidPlatformModule（baseUrl 暫定値あり）
│       │   └── network/                                # OkHttp 版 createHttpClient（actual）
│       └── iosMain/kotlin/studio/nxtech/fujubank/
│           ├── auth/                                   # Keychain 実装（actual）
│           ├── di/
│           │   ├── IosPlatformModule.kt                # iosPlatformModule（baseUrl 暫定値あり）
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
| `authModule` | `TokenStorage`（`TokenStorageFactory.create()` 経由）/ `AuthApi(get(), NetworkConstants.AUTHCORE_BASE_URL)` / `AuthRepository` | `shared/commonMain` |
| `userModule` | `UserApi` / `UserRepository` | `shared/commonMain` |
| `ledgerModule` | `LedgerApi` / `LedgerRepository` | `shared/commonMain` |
| `artifactModule` | `ArtifactApi` / `ArtifactRepository` | `shared/commonMain` |
| `realtimeModule(cableUrl)` | `cableUrl`（`CABLE_URL_QUALIFIER`） / `CoroutineScope`（`APP_SCOPE_QUALIFIER`, `SupervisorJob + Dispatchers.Default`） / `UserChannelClient` / `RealtimeRepository` | `shared/commonMain` |
| `androidPlatformModule` | `TokenStorageFactory(androidContext())` / `HttpClient`（OkHttp エンジン） | `shared/androidMain` |
| `iosPlatformModule` | `TokenStorageFactory()` / `HttpClient`（Darwin エンジン） | `shared/iosMain` |

`sharedModules(cableUrl)` は `authModule` / `userModule` / `ledgerModule` / `realtimeModule(cableUrl)` /
`artifactModule` をまとめて返すヘルパです（`shared/commonMain/.../di/SharedModule.kt`）。

### `appScope` と qualifier

- `appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)` はプロセス全体で 1 つだけ
  生成し、`APP_SCOPE_QUALIFIER`（`named("appScope")`）で取り出します。
- キャンセルはプラットフォーム側のライフサイクルに委ねています（shared 側で自動 cancel はしません）。
- `cableUrl` は `CABLE_URL_QUALIFIER`（`named("cableUrl")`）付き single として登録し、
  `UserChannelClient` に注入します。

### Android の bootstrap 経路

- `composeApp/androidMain/.../FujuBankApp.kt` の `Application.onCreate()` で以下を実行します。

  ```kotlin
  initKoin(cableUrl = CABLE_URL) {
      androidContext(this@FujuBankApp)
      modules(androidPlatformModule)
  }
  ```

- `AndroidManifest.xml` で `<application android:name=".FujuBankApp" ... />` として登録済みです。
- `CABLE_URL` は `ws://10.0.2.2:3000/cable`（エミュレータからホスト localhost を叩く暫定値）。

### iOS の bootstrap 経路

- `shared/iosMain/.../di/KoinIos.kt` の `doInitKoin()` が `initKoin(cableUrl = CABLE_URL) { modules(iosPlatformModule) }`
  を呼びます。`CABLE_URL` は `ws://localhost:3000/cable`（暫定値）。
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
| `POST` | `/users` | User 作成 | ローカル JWT | `UserApi.create(request)` |
| `GET` | `/users/:id` | User 情報 + 残高取得 | ローカル JWT | `UserApi.get(userId)` |
| `GET` | `/users/:id/transactions` | 取引履歴（mint / transfer 統合） | ローカル JWT | `UserApi.transactions(userId)` |
| `GET` | `/artifacts/:id` | Artifact 情報 | ローカル JWT | `ArtifactApi`（`GET /artifacts/:id`） |
| `POST` | `/ledger/transfer` | 送金（User → User） | ローカル JWT + introspection | `LedgerApi.transfer(...)` |

AuthCore 側は `POST /sessions`（ログイン） / `POST /sessions/refresh`（リフレッシュ）を `AuthApi`
が叩きます。`authCoreBaseUrl` は `NetworkConstants.AUTHCORE_BASE_URL`
（`https://authcore.fuju-bank.local`）を DI で渡しています。

### `NetworkResult<T>` の 3 状態

`NetworkResult<T>` は sealed class で、呼び出し側は `when` で網羅します。

| 状態 | 意味 | 発生条件 |
|---|---|---|
| `Success<T>(value)` | 正常レスポンス | HTTP 2xx + body の deserialize 成功 |
| `Failure(ApiError)` | バックエンドのエラーレスポンス | Ktor の `ResponseException`（`expectSuccess = true`）を `ApiErrorEnvelope` で復号 |
| `NetworkFailure(Throwable)` | それ以外の例外 | `ResponseException` 以外（タイムアウト / 接続失敗 / パース失敗など） |

`runCatchingNetwork { ... }` が 3 状態への振り分けを担当します。`CancellationException` は
再スローし、上位の coroutine cancel を妨げません。

### 統一エラーレスポンスと `ApiErrorCode`

```json
{
  "error": {
    "code": "INSUFFICIENT_BALANCE",
    "message": "残高が不足しています"
  }
}
```

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
| `MFA_REQUIRED` | MFA 未検証トークンで `MfaRequired` 適用 action を叩いた |
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

- 接続先は `cableUrl`（DI で `CABLE_URL_QUALIFIER` として注入、現状の暫定値は
  `ws://10.0.2.2:3000/cable` / `ws://localhost:3000/cable`）。
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

| コード | クライアント挙動（方針） |
|---|---|
| `UNAUTHENTICATED` / `TOKEN_INACTIVE` | `TokenStorage.clear()` でトークンを破棄し、ログイン画面へ遷移（将来実装） |
| `MFA_REQUIRED` | `AuthRepository.mfaRequiredEvents`（`SharedFlow<Unit>`）を画面側で購読し、MFA 画面へ誘導 |
| `INSUFFICIENT_BALANCE` | 送金フォームでバリデーションメッセージとして表示 |
| `AUTHCORE_UNAVAILABLE` | 一時的障害としてリトライ誘導 |
| `VALIDATION_FAILED` / `NOT_FOUND` | `ApiError.message` をそのまま表示 |
| `UNKNOWN` | 汎用エラー表示 |

`NetworkResult.NetworkFailure(cause)` はネットワーク断（接続失敗 / タイムアウト / パース失敗
など）として扱い、リトライ可能な UI を出します。

## 認証フロー（AuthCore 連携）

認証基盤は別リポジトリの **AuthCore**（JWT RS256 + introspection 併用）です。銀行側 `User` は
AuthCore の `sub`（ULID, 26 文字）で同定されるため、クライアント側もユーザー ID の管理に
AuthCore の `sub` を使います。

### 処理の流れ

1. **ログイン**: `AuthApi.login(email, password)` → `AuthRepository.login` が `TokenResponse` を受け、
   `TokenStorage.save(access, refresh, subject)` で 3 値を永続化します。`MFA_REQUIRED` が返ったら
   `mfaRequiredEvents` に emit します。
2. **API 呼び出し**: Ktor `Auth { bearer { loadTokens } }` が `authTokenProvider`（DI では
   `TokenStorage.getAccessToken` を渡しています）から access token を読み、`Authorization: Bearer`
   を自動付与します。refresh は `refreshTokenProvider` から同様に取ります。
3. **リフレッシュ**: `HttpClientConfig.tokenRefresher` が非 null のとき、Ktor の `refreshTokens` が
   401 時に発火します。成功すれば `onTokensRefreshed(RefreshedTokens)` で永続化し、新 `BearerTokens`
   を返します。現状は DI で `tokenRefresher = null`、**フックのみ用意済み**です。
4. **401 / 403**: `ResponseException` → `runCatchingNetwork` → `NetworkResult.Failure(ApiError)`。
   `ApiErrorCode` に応じて前節の方針で処理します。
5. **ログアウト**: `AuthRepository.logout()` が `TokenStorage.clear()` を呼びます。`isAuthenticated()`
   は `getAccessToken() != null` で判定します。

### トークン保管（`expect` / `actual`）

`TokenStorage` は suspend API（`getAccessToken` / `getRefreshToken` / `getSubject` / `save` / `clear`）で、
`TokenStorageFactory` を `expect class` として共有インターフェースを定義しています。

| プラットフォーム | 実装 | 鍵・アクセシビリティ |
|---|---|---|
| Android | `EncryptedSharedPreferences`（ファイル名 `fuju_tokens`） | `MasterKey.KeyScheme.AES256_GCM` + `PrefKeyEncryptionScheme.AES256_SIV` / `PrefValueEncryptionScheme.AES256_GCM`。I/O は `Dispatchers.IO` |
| iOS | Keychain（`kSecClassGenericPassword`） | `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` / service `studio.nxtech.fujubank` / account `access` / `refresh` / `subject`。`ExperimentalForeignApi` + CoreFoundation / Security cinterop |

### HTTP クライアントの共通構成

`network/HttpClientFactory.kt` の `applyCommon(config)` がすべてのプラットフォーム共通で以下を
適用します（`createHttpClient` は Android: OkHttp / iOS: Darwin の `expect` / `actual`）。

- `expectSuccess = true`（非 2xx で `ResponseException` を投げる）
- `ContentNegotiation` + kotlinx Json（`ignoreUnknownKeys = true` / `explicitNulls = false`）
- `Logging`（`enableLogging` で BODY / HEADERS を切替。`Authorization` ヘッダは `sanitizeHeader` でマスク）
- `HttpTimeout`（request 30s / connect 10s / socket 30s）
- `WebSockets`（ActionCable で使用）
- `Auth { bearer { loadTokens / refreshTokens } }`
- `defaultRequest { url(baseUrl); Accept: application/json }`

## 環境変数・build variant

**現状、環境固有の値はソース内の private 定数として埋め込まれています**（`// TODO: remove after smoke test`
マーカー付き）。`buildConfigField` / `BuildKonfig` / flavor による差し替えは未実装です。

| 値 | 場所 | 現行の暫定値 |
|---|---|---|
| `BANK_API_BASE_URL`（Android） | `shared/androidMain/.../di/AndroidPlatformModule.kt` | `http://10.0.2.2:3000` |
| `BANK_API_BASE_URL`（iOS） | `shared/iosMain/.../di/IosPlatformModule.kt` | `http://localhost:3000` |
| `CABLE_URL`（Android） | `composeApp/androidMain/.../FujuBankApp.kt` | `ws://10.0.2.2:3000/cable` |
| `CABLE_URL`（iOS） | `shared/iosMain/.../di/KoinIos.kt` | `ws://localhost:3000/cable` |
| `AUTHCORE_BASE_URL` | `shared/commonMain/.../data/remote/NetworkConstants.kt` | `https://authcore.fuju-bank.local` |

Android の `buildTypes` は `release { isMinifyEnabled = false }` のみで、variant ごとの baseUrl 切替は
未実装です。将来的には `buildConfigField` / `BuildKonfig` / flavor のいずれかで差し替える想定で、
差し替え対象のファイルは上表の 5 つに限定されます。

## セットアップ

### 前提

- JDK 17 以上（推奨: JDK 17）
- Android Studio（Koala 以降推奨）
- Xcode（iOS 側をビルドする場合）
- macOS（iOS フレームワーク生成を行う場合）
- ローカルで起動している [fuju-bank-backend](https://github.com/NxTECH-studio/fuju-bank-backend)（`:3000` で listen）
  - 現行の暫定 baseUrl（`10.0.2.2:3000` / `localhost:3000`）はこの前提に依存します

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
| Koin bootstrap（Android / iOS） | 実装済み（T5-2 / T5-3）。`FujuBankApp` / `KoinIos.doInitKoin` が起動経路 |
| API クライアント | 実装済み: `AuthApi`（login / refresh）/ `UserApi`（create / get / transactions）/ `LedgerApi`（transfer + Idempotency-Key）/ `ArtifactApi` |
| TokenStorage | 実装済み: Android（`EncryptedSharedPreferences`）/ iOS（Keychain）。共通 `TokenStorage` インターフェース |
| ActionCable | `UserChannelClient` 実装済み（指数バックオフ 1s→30s、制御フレーム破棄、`credit` のみ emit）。`shareIn` による共有化は未着手（現状 Flow の lifetime は collector 側に委ねている） |
| UI | Compose Multiplatform の画面は **まだ本格実装していません**（ログイン / 残高ダッシュボード / 取引履歴 / HUD は未着手） |
| baseUrl / cableUrl | **ソース埋め込みの暫定値**（`// TODO: remove after smoke test` 付き）。差し替え箇所は「環境変数・build variant」節の 5 ファイル |
| MFA / リフレッシュトークン | フックのみ用意済み（`AuthRepository.mfaRequiredEvents` / `HttpClientConfig.tokenRefresher`）、画面連携は未実装 |

## 関連リポジトリ

- [NxTECH-studio/fuju-bank-backend](https://github.com/NxTECH-studio/fuju-bank-backend) — 銀行層バックエンド（Rails 8.1 API）

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html).
