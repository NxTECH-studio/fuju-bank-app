# README を充実化する（エンジニア向けドキュメント / ポートフォリオ）

## 概要

`README.md` 1 本にオンボーディング & ポートフォリオ用途のエンジニア向けドキュメントをまとめ直します。バックエンド `fuju-bank-backend` の README と同等の粒度で、クライアント内部の層構造・DI・認証フロー・API 呼び出し・ActionCable 購読・環境変数・現況までを一通り把握できる状態にします。

## 背景・目的

- 現在の `README.md` は概要・API 一覧・認証方針・検証コマンド程度にとどまり、クライアント側の内部設計（Koin グラフ、`expect`/`actual` 境界、ActionCable 再接続、エラーハンドリング方針、環境変数の切り替えポリシー）がドキュメントから読み取れません。
- オンボーディング & ポートフォリオ（採用担当者・他エンジニアが技術力 / 設計力を評価できる水準）を目的として、KMP クライアント側の設計意図と実装現況を 1 本の `README.md` に集約します。
- バックエンド README が「同等の粒度」での参照基準です。クライアント特有（DI / expect-actual / Swift interop / build variant / Keychain・EncryptedSharedPreferences）の深さを出します。

## 影響範囲

- モジュール: ルート（`README.md` のみ）
- ソースセット: なし（ドキュメントのみ。コード変更は行わない）
- 破壊的変更: なし
- 追加依存: なし

補足:

- 図は入れません（アーキテクチャ図・スクリーンショット無し）
- `docs/` 配下への分割はしません（`README.md` 1 本）
- 開発ワークフロー（ブランチ戦略 / コミット規約 / Claude Code / Notion 連携）は含めません
- テスト戦略 / CI/CD / セキュリティ / expect-actual 活用例などの深掘り節は作らず、必要な範囲で他節に織り込むのみ

## 調査で判明した事実（README へ転記する素材）

コードベース調査の結果、README に記載すべき事実は以下に確定しています。記載時はこの内容を一次ソースとし、推測は書かないこと。

### モジュール構成 / 設定

- `settings.gradle.kts`: `rootProject.name = "Fujubankapp"` / `include(":composeApp", ":shared")` / `TYPESAFE_PROJECT_ACCESSORS` 有効
- ターゲット: `androidTarget()`（`JvmTarget.JVM_11`）, `iosArm64()`, `iosSimulatorArm64()`
- iOS framework baseName: `Shared`（`isStatic = true`）
- Android 名前空間:
  - `composeApp`: `studio.nxtech.fujubank`（`applicationId` 同名, `versionCode = 1`, `versionName = "1.0"`, `minSdk = 24`, `targetSdk = 36`, `compileSdk = 36`）
  - `shared`: `studio.nxtech.fujubank.shared`
- JDK / Gradle: JDK 17 前提、AGP 8.11.2、Kotlin 2.3.20、Compose Multiplatform 1.10.3、Material3 1.11.0-alpha06

### 主要依存（`gradle/libs.versions.toml`）

| 役割 | 依存 |
|---|---|
| HTTP クライアント | Ktor 3.4.2（core / content-negotiation / logging / auth / websockets / okhttp (androidMain) / darwin (iosMain) / mock (test)）|
| JSON | `kotlinx-serialization-json` 1.9.0 |
| 非同期 | `kotlinx-coroutines` 1.10.2 |
| 日時 | `kotlinx-datetime` 0.7.1 |
| DI | Koin 4.2.1（`koin-core` / `koin-android` / `koin-test`）|
| Android 暗号化保管 | `androidx.security:security-crypto` 1.1.0-alpha06 |
| Lifecycle | `androidx-lifecycle` 2.10.0（`viewmodel-compose` / `runtime-compose`）|

### Koin グラフ（bootstrap の仕組み）

- `shared/commonMain/.../di/SharedModule.kt`:
  - `sharedModules(cableUrl)` が以下のモジュールをまとめる: `authModule`, `userModule`, `ledgerModule`, `realtimeModule(cableUrl)`, `artifactModule`
  - `initKoin(cableUrl, appDeclaration)` がエントリポイント。`ws://` または `wss://` でない URL は `require` で拒否。プロセス内で 1 度だけ呼ぶ
- `shared/commonMain/.../di/AuthModule.kt`: `TokenStorage`, `AuthApi(get(), NetworkConstants.AUTHCORE_BASE_URL)`, `AuthRepository`
- `shared/commonMain/.../di/UserModule.kt`: `UserApi`, `UserRepository`
- `shared/commonMain/.../di/LedgerModule.kt`: `LedgerApi`, `LedgerRepository`
- `shared/commonMain/.../di/ArtifactModule.kt`: `ArtifactApi`, `ArtifactRepository`
- `shared/commonMain/.../di/RealtimeModule.kt`:
  - `CABLE_URL_QUALIFIER` / `APP_SCOPE_QUALIFIER` で qualifier 付き single 登録
  - `appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)`（プロセス全体で 1 つ、キャンセルはプラットフォーム側のライフサイクルに委ねる）
  - `UserChannelClient`, `RealtimeRepository` を登録
- プラットフォーム側モジュール:
  - `shared/androidMain/.../di/AndroidPlatformModule.kt`: `TokenStorageFactory(androidContext())`, `createHttpClient(...)`。`BANK_API_BASE_URL = "http://10.0.2.2:3000"`（エミュレータ→ホスト localhost の暫定値。`// TODO: remove after smoke test`）
  - `shared/iosMain/.../di/IosPlatformModule.kt`: `TokenStorageFactory()`, `createHttpClient(...)`。`BANK_API_BASE_URL = "http://localhost:3000"`（同様の暫定値）
- エントリ:
  - Android: `composeApp/androidMain/.../FujuBankApp.kt` の `Application.onCreate()` で `initKoin(CABLE_URL) { androidContext(this@FujuBankApp); modules(androidPlatformModule) }`。`CABLE_URL = "ws://10.0.2.2:3000/cable"`（暫定値）。`AndroidManifest.xml` で `android:name=".FujuBankApp"`
  - iOS: `shared/iosMain/.../di/KoinIos.kt` の `doInitKoin()`（`CABLE_URL = "ws://localhost:3000/cable"` 暫定値） + `userApi()` ファサード。`iosApp/iosApp/iOSApp.swift` の `init` で `KoinIosKt.doInitKoin()`

### API クライアント層

- `shared/commonMain/.../data/remote/`
  - `NetworkConstants.AUTHCORE_BASE_URL = "https://authcore.fuju-bank.local"`
  - `ApiError` / `ApiErrorEnvelope` / `ApiErrorBody` / `ApiErrorCode`（enum: `VALIDATION_FAILED`, `NOT_FOUND`, `INSUFFICIENT_BALANCE`, `UNAUTHENTICATED`, `TOKEN_INACTIVE`, `AUTHCORE_UNAVAILABLE`, `MFA_REQUIRED`, `UNKNOWN`。`fromString` で未知コードは `UNKNOWN` にマップ）
  - `NetworkResult<T>`（`Success` / `Failure(ApiError)` / `NetworkFailure(Throwable)`）, `map`, `runCatchingNetwork`（`CancellationException` は再スロー、`ResponseException` を `ApiError` 封筒に復号、それ以外は `NetworkFailure`）
- `api/AuthApi`: `POST {AUTHCORE_BASE_URL}/sessions`（login）, `POST /sessions/refresh`
- `api/UserApi`: `POST /users`, `GET /users/:id`, `GET /users/:id/transactions`
- `api/LedgerApi`: `POST /ledger/transfer` — `Idempotency-Key` ヘッダ + body `idempotency_key` の両方にセット（default は `Uuid.random()` ファクトリ）
- `api/ArtifactApi`: `GET /artifacts/:id`
- `api/UserChannelClient`:
  - `subscribe(userId): Flow<CreditEventDto>` を `channelFlow` で構築
  - ActionCable サブプロトコル: `{"command":"subscribe","identifier":"{\"channel\":\"UserChannel\",\"user_id\":...}"}` を Text frame で送る
  - 制御フレーム（`welcome` / `ping` / `confirm_subscription` / `disconnect`）は `envelope.type != null` で除外、`message` 側のみデコードし `type == "credit"` のみを emit
  - `retryWhen` で指数バックオフ（1s, 2s, 4s, 8s, 16s, 30s 上限）。`CancellationException` は再接続しない
  - `buffer()` 付きでバックプレッシャ吸収
- Repository 層: `AuthRepository` / `UserRepository` / `LedgerRepository` / `ArtifactRepository` / `RealtimeRepository`
  - `AuthRepository.login()` は `tokenStorage.save(access, refresh, subject)` を実行。`ApiErrorCode.MFA_REQUIRED` を検知したら `mfaRequiredEvents: SharedFlow<Unit>` へ emit（`BufferOverflow.DROP_OLDEST`）
  - `isAuthenticated()` は `getAccessToken() != null`

### HTTP クライアント（`network/HttpClientFactory.kt`）

- `HttpClientConfig(baseUrl, enableLogging, authTokenProvider, refreshTokenProvider, tokenRefresher?, onTokensRefreshed)`
- `expect fun createHttpClient(config)`（Android: OkHttp, iOS: Darwin）
- 共通 `applyCommon`:
  - `expectSuccess = true`
  - `ContentNegotiation` + kotlinx Json（`ignoreUnknownKeys = true`, `explicitNulls = false`）
  - `Logging`（`enableLogging` で BODY / HEADERS を切り替え。`Authorization` ヘッダは sanitize）
  - `HttpTimeout`: request 30s / connect 10s / socket 30s
  - `WebSockets` インストール（ActionCable で利用）
  - `Auth { bearer { loadTokens / refreshTokens } }`: `authTokenProvider` → `BearerTokens(access, refresh)` を注入。`tokenRefresher` があればリフレッシュ時に `onTokensRefreshed` で永続化
  - `defaultRequest { url(baseUrl); Accept: application/json }`

### トークン保管（`expect`/`actual`）

- `shared/commonMain/.../auth/TokenStorage.kt`: `suspend` API（`getAccessToken` / `getRefreshToken` / `getSubject` / `save(access, refresh, subject)` / `clear`）
- `shared/commonMain/.../auth/TokenStorageFactory.kt`: `expect class TokenStorageFactory { fun create(): TokenStorage }`
- Android `actual`: `EncryptedSharedPreferences`（`MasterKey.KeyScheme.AES256_GCM` + `PrefKeyEncryptionScheme.AES256_SIV` / `PrefValueEncryptionScheme.AES256_GCM`）。I/O は `Dispatchers.IO`。ファイル名 `fuju_tokens`
- iOS `actual`: Keychain（`kSecClassGenericPassword` + `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`）。service `studio.nxtech.fujubank`、account は `access` / `refresh` / `subject`。`ExperimentalForeignApi` + CoreFoundation / Security cinterop

### リアルタイム購読（ActionCable）

- `realtimeModule(cableUrl)` で `UserChannelClient` と `appScope` を注入
- `UserChannelClient` はアプリスコープを受け取るが、現状 Flow の lifetime は collector に委ねる（`shareIn` は未使用）
- 再接続: 指数バックオフ（1s → 30s）、制御フレームは破棄、`credit` 以外の `message.type` も破棄

## 実装ステップ（README 章立て）

以下の章構成で `README.md` を全面書き直します。各節の要点と「この計画内の参照ソース（上記『調査で判明した事実』）」を明記します。

### 1. タイトル + 冒頭サマリ

- 既存冒頭を引き継ぎ、「KMP + Compose Multiplatform クライアント」「fuju-bank プロダクトの 3 層目（デモ SNS / 作家 HUD）」であることを 3〜5 行で提示
- 参照: 既存 README の冒頭 L1-10, L12-25

### 2. 3 層アーキテクチャでの位置づけ

- 既存の 3 層表を維持。クライアント側は「整数表示のみで小数計算には関与しない」旨を残す
- 参照: 既存 README L12-25

### 3. 主な画面・機能（予定）

- 既存表を維持（ログイン / 残高 / 取引履歴 / リアルタイム着金 / 将来の送金）
- 「予定」と明示し、実装済み範囲は『現況』節で別途示す
- 参照: 既存 README L27-35

### 4. 技術スタック（表）

- カテゴリ × 技術の表を拡張：言語 / UI / ビルド / ターゲット / DI / HTTP / シリアライズ / 非同期 / 日時 / 暗号化保管 / ライフサイクル / テスト
- 参照: 「主要依存」節、`libs.versions.toml`

### 5. プロジェクト構成（tree）

- 現行の tree を基に、`shared/` の内部（`data/remote/`, `data/repository/`, `auth/`, `network/`, `di/`）と `composeApp/` の `androidMain/.../FujuBankApp.kt` まで 1〜2 階層深く追加した tree を掲載
- 「モジュール境界」「`expect`/`actual` の方針」「依存追加は Version Catalog 経由」を箇条書き
- 参照: `settings.gradle.kts`, `composeApp/build.gradle.kts`, `shared/build.gradle.kts`

### 6. アーキテクチャ（層構造 / 文章 + 表、図なし）

- 層: UI（Compose / ViewModel） → Repository（shared） → Api（Ktor ラッパ） → HttpClient / TokenStorage（expect-actual）
- 責務表（層 × 責務 × 代表クラス × ソースセット）を記載
- データフロー記述（ログイン / API 呼び出し / 401 時のリフレッシュ / ActionCable 購読）は文章で
- 参照: 「API クライアント層」「HTTP クライアント」「Repository 層」節

### 7. DI（Koin）構成と bootstrap

- `initKoin(cableUrl, appDeclaration)` の契約（`ws://` / `wss://` 必須、1 プロセス 1 回）
- モジュール一覧を表で（`authModule` / `userModule` / `ledgerModule` / `artifactModule` / `realtimeModule(cableUrl)` / `androidPlatformModule` / `iosPlatformModule`、供給する主な依存）
- `appScope`（`SupervisorJob + Dispatchers.Default`）と qualifier（`CABLE_URL_QUALIFIER` / `APP_SCOPE_QUALIFIER`）を短く説明
- Android: `FujuBankApp : Application` で bootstrap、`AndroidManifest.xml` の `android:name`
- iOS: `KoinIos.kt` の `doInitKoin()` を `iOSApp.swift` の `init` から呼ぶ。Swift 側は `KoinIosKt.doInitKoin()` / `userApi()` ファサード
- 参照: 「Koin グラフ」節

### 8. API 仕様

- 既存の API 一覧表に加え、クライアント側の呼び出し方を併記する節を追加
- `NetworkResult<T>` の 3 状態（`Success` / `Failure(ApiError)` / `NetworkFailure`）と `runCatchingNetwork` の挙動（`CancellationException` 再スロー、`ResponseException` の封筒復号）
- 統一エラーレスポンスのスキーマと `ApiErrorCode` enum、未知コードは `UNKNOWN`
- Idempotency-Key: `POST /ledger/transfer` でヘッダと body の両方に同じ UUID を入れる（`LedgerApi` の実装に合わせる）。リトライ時は同一キーを再利用
- ActionCable: `UserChannel` 接続手順（`subscribe` command の identifier JSON 形式）、`credit` ペイロード例（バックエンド README の例を引用）、クライアント側の扱い（制御フレーム破棄 / 指数バックオフ 1s→30s）
- 参照: 「API クライアント層」節、バックエンド README L60-121

### 9. error.code のクライアントハンドリング方針

- `ApiErrorCode` enum と画面側の扱い方針を表で
  - `UNAUTHENTICATED` / `TOKEN_INACTIVE` → ログイン画面へ遷移（将来）
  - `MFA_REQUIRED` → `AuthRepository.mfaRequiredEvents` を購読、MFA 画面へ誘導
  - `INSUFFICIENT_BALANCE` → 送金フォームでバリデーションメッセージ表示
  - `AUTHCORE_UNAVAILABLE` → リトライ誘導
  - `UNKNOWN` → 汎用エラー表示
- `NetworkFailure` はネットワーク断として扱う
- 参照: `ApiErrorCode.kt`, `NetworkResult.kt`, `AuthRepository.kt`

### 10. 認証フロー（AuthCore 連携）

- ログイン: `AuthApi.login` → `TokenResponse` を `TokenStorage.save(access, refresh, subject)` で永続化
- API 呼び出し時: Ktor `Auth { bearer { loadTokens } }` が `authTokenProvider`（= `TokenStorage.getAccessToken`）から取得して `Authorization: Bearer` を自動付与
- リフレッシュ: `HttpClientConfig.tokenRefresher` が設定されていれば Ktor `refreshTokens` が 401 時に呼ばれ、`onTokensRefreshed` で新トークンを永続化（現状は DI で `tokenRefresher = null`、設定フックは用意済み）
- 401/403: `ResponseException` → `runCatchingNetwork` → `NetworkResult.Failure(ApiError)`。コードに応じて前節の方針で処理
- expect/actual 境界:
  - Android: `EncryptedSharedPreferences` + Keystore（AES256-GCM）
  - iOS: Keychain（`AccessibleAfterFirstUnlockThisDeviceOnly`）
  - 共通インターフェース `TokenStorage`（suspend API）
- 参照: 「トークン保管」「HTTP クライアント」「API クライアント層」節

### 11. 環境変数・build variant

- 現状: 環境固有の値は **ソース内の private 定数** として埋め込まれている（`// TODO: remove after smoke test` マーカー付き）
  - `BANK_API_BASE_URL`（Android: `http://10.0.2.2:3000`, iOS: `http://localhost:3000`）
  - `CABLE_URL`（Android: `ws://10.0.2.2:3000/cable`, iOS: `ws://localhost:3000/cable`）
  - `NetworkConstants.AUTHCORE_BASE_URL = "https://authcore.fuju-bank.local"`
- build variant: Android は `buildTypes { release { isMinifyEnabled = false } }` のみ。variant ごとの baseUrl 切替は未実装
- 将来の想定（1〜2 行で）: `buildConfigField` / `BuildKonfig` / flavor による差し替えを検討
- 参照: `AndroidPlatformModule.kt`, `IosPlatformModule.kt`, `FujuBankApp.kt`, `KoinIos.kt`, `composeApp/build.gradle.kts`

### 12. セットアップ

- 前提（JDK 17+, Android Studio, Xcode, macOS）を維持
- Android: `./gradlew :composeApp:assembleDebug` / Android Studio の Run
- iOS: `iosApp/` を Xcode で開く。先にフレームワークをリンクするなら `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`
- バックエンド接続: ローカル `fuju-bank-backend` を `:3000` で起動しておくこと（現行の暫定 baseUrl に依存）
- 参照: 既存 README L106-129

### 13. 検証コマンド早見表

- 既存表を維持
  - `./gradlew build`
  - `./gradlew :shared:allTests`
  - `./gradlew :composeApp:assembleDebug`
  - `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`
  - `./gradlew --stop`
- 参照: 既存 README L131-139

### 14. 現況（実装済み範囲）

- Koin bootstrap: Android / iOS ともに起動経路あり（T5-2 / T5-3）
- API クライアント: `UserApi` / `LedgerApi`（transfer + Idempotency-Key）/ `ArtifactApi` / `AuthApi` 実装済み
- TokenStorage: Android（EncryptedSharedPreferences）/ iOS（Keychain）実装済み
- ActionCable: `UserChannelClient` 実装済み（指数バックオフ付き）。`shareIn` 化は未着手
- UI: Compose Multiplatform の画面はまだ本格実装していない（ログイン画面 / ダッシュボード / 取引履歴 / HUD は未着手）
- baseUrl / cableUrl: ソース埋め込みの暫定値（`// TODO: remove after smoke test`）
- MFA / リフレッシュトークン: フックは用意済み（`AuthRepository.mfaRequiredEvents`, `HttpClientConfig.tokenRefresher`）、画面連携は未実装

### 15. 関連リポジトリ

- `fuju-bank-backend` のリンクを維持
- （KMP 公式ドキュメントのリンクも維持）

## 段階的な進め方（セクション単位のコミット単位）

README をまとめて書き換えるのではなく、以下の粒度で段階的に反映することを推奨します。

1. 章立てと節の空スケルトンを作る（既存 README の構成を残しつつ章タイトルを入れ替え）
2. 1〜5 章（サマリ / 3 層 / 機能 / 技術スタック / 構成 tree）を埋める
3. 6〜7 章（アーキテクチャ / DI）を埋める
4. 8〜10 章（API 仕様 / error.code / 認証フロー）を埋める
5. 11〜14 章（環境変数 / セットアップ / 検証コマンド / 現況）を埋める
6. 15 章 + 冒頭サマリの最終調整、重複節の掃除

各段階で Markdown のプレビューを確認し、表記揺れ（`commonMain` / `androidMain` / `iosMain` の綴り、クラス名のバッククォート囲み、コードブロック言語指定）を統一すること。

## 検証（完了条件のチェックリスト）

README 自体の検証はビルド対象ではないので、以下の観点で目視レビューを行います。

- [ ] 概要（プロジェクト目的 / KMP 採用の位置づけ）が冒頭 + 3 層節で読める
- [ ] 技術スタック表にカテゴリ別の主要依存がすべて載っている
- [ ] ディレクトリ構成（tree）に `shared/` の主要サブパッケージ（`data/remote/`, `data/repository/`, `auth/`, `network/`, `di/`）が反映されている
- [ ] アーキテクチャ節に層構造の文章説明と責務表がある（図は無し）
- [ ] DI（Koin）節に `initKoin` の契約、モジュール一覧、Android / iOS それぞれの bootstrap 経路が書かれている
- [ ] API 仕様節にバックエンド API 一覧・認証方針・エラーコード・Idempotency-Key・ActionCable ペイロードが揃っている
- [ ] クライアント側の呼び出し方（`NetworkResult`, `runCatchingNetwork`, `ApiErrorCode`, `Idempotency-Key` 付与方法、ActionCable 再接続）が書かれている
- [ ] 認証フロー節でログイン → 保管 → リフレッシュ → 401/403 ハンドリングと `expect`/`actual`（EncryptedSharedPreferences / Keychain）が説明されている
- [ ] 環境変数・build variant 節で現状のソース埋め込み暫定値を正直に明示している
- [ ] セットアップ手順（Android / iOS）と検証コマンドが列挙されている
- [ ] 現況節で「何が実装済みで何が未着手か」が分かる
- [ ] バックエンド README と比較して同等の粒度（節数・表の網羅度）に達している
- [ ] 図・スクリーンショットが含まれていない
- [ ] 開発ワークフロー（ブランチ / コミット規約 / Claude Code / Notion）を含んでいない
- [ ] `./gradlew build` が従来どおり通る（ドキュメント変更のみなので当然通るはずだが念のため確認）

## 技術的な補足

- 暫定値（`BANK_API_BASE_URL` / `CABLE_URL`）は「現況」節で正直に暫定値と明示する。将来差し替える場所（`AndroidPlatformModule.kt`, `IosPlatformModule.kt`, `FujuBankApp.kt`, `KoinIos.kt`）を列挙しておくと、次に触る人が迷わない。
- iOS 側の `KoinIosKt.doInitKoin()` という呼称は Kotlin のファイル名ベースで自動生成される Obj-C クラス名に由来する（`KoinIos.kt` → `KoinIosKt`）。README でもこの命名規則に触れておくと Swift interop の入口が伝わりやすい。
- ActionCable の `identifier` は「JSON 文字列を値として持つ JSON フィールド」という二重エンコードが特徴的なので、README の例示はエスケープ付きで正確に載せる（`UserChannelClient.subscribe` の実装と一致させる）。
- バックエンド README のトーン（ですます調 / 表多用 / コードブロックは要所のみ）と合わせる。
