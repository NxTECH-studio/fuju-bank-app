# client-bank-21: 銀行アプリ自体からのサインアップと MFA セットアップ実装

## 概要

現在モック実装になっている銀行アプリのサインアップフローを、AuthCore (`fuju-system-authentication`) の実 API に接続し、**新規アカウント作成 + MFA 必須セットアップ** までを 1 本の動線として完結させる。MVP の「銀行アプリだけでサインアップから利用開始まで完結する」要件を満たす。

SNS バックエンド側 (`fuju-system-authentication`) と AuthCore の DTO / エンドポイントを調査した結果、以下の構成で実装する:

- サインアップ本体: `POST /v1/auth/register`（email + password + public_id 必須、トークン非発行、メール OTP なし）
- MFA セットアップ: `POST /v1/auth/mfa/register` → `POST /v1/auth/mfa/enable` の 2 ステップ
- QR コードは AuthCore がサーバ側 (go-qrcode) で 256px PNG を生成し `data:image/png;base64,...` 形式で返す
- 「メール OTP による登録確認」工程は **AuthCore に存在しない** ため、現行モック (`SignUpOtpScreen.kt` / `SignUpOtpView.swift`) は撤去する

## 背景・現状ギャップ

### 現状の実装

- `composeApp/.../features/signup/SignUpFlowViewModel.kt` および `iosApp/.../Features/SignUp/SignUpFlowState.swift` は **email + password + 6 桁 OTP** だけを保持するモック ViewModel。API 連携は一切なし。
- `SignUpOtpScreen.kt` / `SignUpOtpView.swift` は「登録したメールに 6 桁のコードを送信しました」というメッセージで 6 桁の数字 OTP を入力させるが、**AuthCore は登録時にメール OTP を送らない**ため実 API と合致しない。
- `App.kt` / `AppRoot.swift` の `SignupRoute` enum は `Create → Otp → Success` の 3 ステップ前提で組まれており、CTA タップで形式上は次画面に遷移するだけで、`/v1/auth/register` も `provisionMe` も呼んでいない。
- `SignUpCreateScreen.kt` の入力欄は email + password のみで、**AuthCore が必須としている `public_id` の入力欄がない**ため、現状の UI のままでは register API を呼べない。
- `LoginViewModel.kt` には `provisionAndAuthenticate()` 経路（`provisionMe` → `setAuthenticated`）が既に存在するため、register 後の「裏で自動 login → provision」工程はこの経路を再利用できる。
- `AuthFlowIos.kt` には `loginAndProvision` / `verifyMfaAndProvision` のファサードがあるが、**signup 用ファサードは未実装**。Swift 側はこれを呼ぶ前提で薄くする。

### MVP 確定要件とのギャップ

- MVP は「銀行アプリ単体でサインアップから受け取りまで完結」。現状は実 API 未接続のため、新規ユーザはアプリを開いても先に進めない。
- 認証要件として MFA 必須 (TOTP) が確定済み。`MfaVerifyScreen` / `MfaVerifyView` は **既存ユーザが login 時に MFA を入力する** 画面しかなく、新規ユーザがセットアップする (QR を出して TOTP を登録する) 画面群は未実装。

## 採用するフロー全体図

```
[Step 1] アカウント作成画面 (email + password + public_id)
   |
   |  CTA タップ
   v
[Step 2] POST /v1/auth/register   <-- AuthCore: 201 Created, トークン非発行
   |        body: { email, password, public_id }
   |
   |  成功 (user 情報受領)
   v
[Step 3] 裏で POST /v1/auth/login  <-- access_token + refresh_token cookie 取得
   |        |
   |        v
   |     UserRepository.provisionMe()  <-- 銀行 BE に user 行を upsert
   |
   |  ※この時点ではまだ SessionStore.setAuthenticated は呼ばない
   |     （Authenticated に倒すと AppRoot が RootScaffold を出してしまうため）
   v
[Step 4] MFA セットアップ画面（QR 表示）
         POST /v1/auth/mfa/register  <-- secret + qr_code(base64 PNG) + recovery_codes
         |
         |  「コードを入力」CTA
         v
[Step 5] TOTP 検証画面（6 桁入力）
         POST /v1/auth/mfa/enable  <-- code: "123456"
         |
         |  成功 (mfa_enabled = true)
         v
[Step 6] Recovery codes 表示画面（必ずコピー & チェック必須）
         |
         |  「保存しました」CTA
         v
   SignupCompletionSignal.arm()
   SessionStore.setAuthenticated(userId)
         |
         v
      Welcome 画面 → ホーム
```

### フローの設計上の重要ポイント

1. **Step 3 の自動 login は SessionStore に Authenticated を伝搬しない**。
   `AuthRepository.login()` は内部で `tokenStorage.saveAccess()` を呼ぶため、`UserRepository.provisionMe()` は通る。だが `SessionStore.setAuthenticated()` を即座に呼ぶと `App.kt` 側の `showRoot` 判定（`Authenticated && !welcomePending`）で RootScaffold に飛んでしまうため、MFA セットアップ完了まで `Unauthenticated` のままにする。
2. **MFA セットアップはスキップ不可**。Step 4-6 の戻るボタン・システムバックは抑止する。アプリ kill で離脱した場合、AuthCore 側は `mfa_enabled = false` のまま残るため、次回 login 時に再度 `mfa/register` から強制セットアップする経路を用意する（既存の `MfaPending` ではなく、新たに `MfaSetupRequired` 系の状態が必要）。
3. **`mfa/register` は非べき等**。再呼び出しすると secret/QR/recovery codes が **新しいものに上書き** される（旧 secret は失効）。戻るボタンで Step 4 から離脱して再入する UX は設計上避ける。クライアントは戻るボタン抑止 + 同画面内に「再生成」ボタンを置く構成にする。
4. **QR コードはサーバ側生成**。AuthCore が go-qrcode で 256px PNG を作って `qr_code: "data:image/png;base64,iVBOR..."` の形で返す。クライアントは base64 部分を切り出してデコードして表示するだけ。`qrose` 依存（既に `libs.versions.toml` 28 行目に存在）はクライアント側 QR 生成には使わないが、別箇所で利用しているので削除はしない。
5. **public_id 入力欄の見た目は LoginScreen 準拠**。Figma は無いが、`LoginScreen.kt` / `LoginView.swift` の `FlatTextField` / `flatField` のスタイル（rounded-16 / 白カード / placeholder 色 `#DADBDF` / `BankTextField` も同等）に揃える。

## 影響範囲

### モジュール / ソースセット別

| パス | 役割 | 変更内容 |
|------|------|----------|
| `shared/commonMain/.../data/remote/dto/AuthDto.kt` | AuthCore DTO | `RegisterRequest` / `RegisterResponse` 追加 |
| `shared/commonMain/.../data/remote/dto/MfaDto.kt` | MFA DTO | `MfaRegisterResponse` / `MfaEnableRequest` 追加（`MfaVerifyRequest` は既存） |
| `shared/commonMain/.../data/remote/api/AuthApi.kt` | AuthCore クライアント | `register()` / `mfaRegister()` / `mfaEnable()` を追加 |
| `shared/commonMain/.../data/repository/AuthRepository.kt` | 認証 Repository | `register()` / `setupMfa()` / `enableMfa()` を追加 |
| `shared/commonMain/.../data/remote/ApiErrorCode.kt` | エラーコード enum | `USER_ALREADY_EXISTS` / `PUBLIC_ID_ALREADY_EXISTS` / `PUBLIC_ID_RESERVED` / `MFA_SETUP_REQUIRED` を追加 |
| `shared/commonMain/.../session/AuthErrorMessages.kt` | 文言マッピング | `forRegister()` / `forMfaSetup()` / `forMfaEnable()` 追加 |
| `shared/commonMain/.../session/AuthFlowIos.kt` | Swift 用ファサード | `registerAndStartMfaSetup()` / `enableMfaAndProvision()` を追加 |
| `shared/commonMain/.../session/SessionStore.kt` | セッション状態 | `MfaSetupRequired(preToken/email)` 追加（既存 ログイン経路で MFA 未セットアップユーザを誘導するため） |
| `shared/commonMain/.../signup/SignupCompletionSignal.kt` | ワンショット | 変更なし（`arm()` を Step 6 完了時に呼ぶ） |
| `composeApp/androidMain/.../features/signup/` | Android UI | 全面改修（後述） |
| `iosApp/iosApp/Features/SignUp/` | iOS UI | 全面改修（後述） |
| `composeApp/androidMain/.../App.kt` | ルート | `SignupRoute` enum 改修、MFA セットアップ画面の呼び出し追加 |
| `iosApp/iosApp/App/AppRoot.swift` | ルート | `SignupRoute` enum 改修、MFA セットアップ View の呼び出し追加 |

### 破壊的変更

- 既存の `SignUpFlowViewModel.kt` / `SignUpFlowState.swift` の **`otp: String` フィールドを削除**（モック OTP 6 桁入力欄が無くなるため）。
- 既存の `SignupRoute` enum メンバ `Otp` を削除し、`MfaQr` / `MfaVerify` / `RecoveryCodes` を追加。
- `SignUpOtpScreen.kt` / `SignUpOtpView.swift` をリポジトリから削除（モック画面）。
- 公開 API 変更ではないため、shared の ABI 影響はなし。Compose の ViewModel と SwiftUI の State は内部実装で外部参照ゼロ。

### 追加依存

- **追加なし**。`libs.versions.toml` 既存依存だけで完結する:
  - base64 デコードは `kotlin.io.encoding.Base64`（Kotlin 1.8+ の標準 API、`@OptIn(ExperimentalEncodingApi::class)` のみ必要）
  - PNG → ImageBitmap は Android `BitmapFactory` / iOS `UIImage` で `expect`/`actual` 抽象
  - SwiftUI 側はネイティブの `UIImage(data:)` で済む

## 実装ステップ

### Step A: API / DTO 層（shared/commonMain）

**A-1. `AuthDto.kt` に register 系を追加**

```kotlin
@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    @SerialName("public_id") val publicId: String,
)

@Serializable
data class RegisterResponse(
    val id: String,
    val email: String,
    @SerialName("public_id") val publicId: String,
    @SerialName("created_at") val createdAt: String,
)
```

**A-2. `MfaDto.kt` に setup/enable 系を追加**

```kotlin
@Serializable
data class MfaRegisterResponse(
    val secret: String,
    @SerialName("qr_code") val qrCodeDataUrl: String,
    @SerialName("recovery_codes") val recoveryCodes: List<String>,
)

@Serializable
data class MfaEnableRequest(val code: String)
```

**A-3. `AuthApi.kt` に 3 メソッド追加**

- `register(request: RegisterRequest): NetworkResult<RegisterResponse>` — `POST /v1/auth/register`、トークン非発行 (Bearer 不要)
- `mfaRegister(): NetworkResult<MfaRegisterResponse>` — `POST /v1/auth/mfa/register`、Bearer 必須なので Auth プラグイン付き **bank/AuthCore 共通クライアント** (`AUTHCORE_CLIENT_QUALIFIER` ではない方) を使う
- `mfaEnable(code: String): NetworkResult<Unit>` — `POST /v1/auth/mfa/enable`、Bearer 必須なので同上

> 注意: `register()` は Bearer 不要なので `AUTHCORE_CLIENT_QUALIFIER` 側（refresh 自己再帰防止用）を使う。`mfaRegister()` / `mfaEnable()` は access_token を発行済みの状態で叩くため、Auth plugin 付きの bank/AuthCore 共通クライアントを使う。誤って逆を使うと 401 → refresh ループに入るため、`AuthApi` のコンストラクタを **2 つの client 受け取り** に変更するか、メソッド単位で `qualifier` を取り直す形にする（実装時に検討、テストで `koin verify` を通す）。

**A-4. `ApiErrorCode.kt` に AuthCore 側の register/mfa エラーを追加**

```kotlin
USER_ALREADY_EXISTS,
PUBLIC_ID_ALREADY_EXISTS,
PUBLIC_ID_RESERVED,
PUBLIC_ID_INVALID,
MFA_SETUP_REQUIRED,   // login 後に mfa_enabled = false のときの誘導用
```

### Step B: Repository 層（shared/commonMain）

**B-1. `AuthRepository.kt` に 3 メソッド追加**

```kotlin
suspend fun register(email: String, password: String, publicId: String): NetworkResult<RegisterResponse>

suspend fun setupMfa(): NetworkResult<MfaSetupBundle>  // secret / qrPngBase64 / recoveryCodes をドメイン化

suspend fun enableMfa(code: String): NetworkResult<Unit>
```

`MfaSetupBundle` は `qr_code` の `data:image/png;base64,` プレフィックスを切り落とした **生 base64 文字列** だけ保持する。base64 → ImageBitmap 化は UI 層の責務にして、Repository は通信結果のラッピングのみに留める。

**B-2. `AuthErrorMessages.kt` に文言マッピング追加**

- `forRegister(error)`:
  - `USER_ALREADY_EXISTS` → 「このメールアドレスは既に登録されています」 (UI 側でメール欄下にインライン表示 + ログイン画面導線)
  - `PUBLIC_ID_ALREADY_EXISTS` → 「このユーザー ID は既に使われています」 (ID 欄下にインライン表示)
  - `PUBLIC_ID_RESERVED` → 「このユーザー ID は使用できません」
  - `PUBLIC_ID_INVALID` / `VALIDATION_FAILED` → 「入力内容を確認してください」
  - その他 → 既定のフォールバック
- `forMfaSetup(error)`: TOTP secret 取得時の通信失敗
- `forMfaEnable(error)`:
  - `TOTP_CODE_INVALID` → 「認証コードが正しくありません」
  - その他 → 既定

### Step C: 共通ファサード（shared/iosMain `AuthFlowIos.kt`）

Swift 側から薄く呼べるよう、4 つの Outcome 型と関数を追加:

```kotlin
sealed class RegisterOutcome {
    data class Started(val email: String, val publicId: String) : RegisterOutcome()
    data class Failure(val message: String, val error: ApiError) : RegisterOutcome()
    data class NetworkFailure(val message: String) : RegisterOutcome()
}

sealed class MfaSetupOutcome {
    data class Ready(val qrPngBase64: String, val recoveryCodes: List<String>) : MfaSetupOutcome()
    // 失敗系は AuthFlowOutcome と同型
}

sealed class MfaEnableOutcome {
    data class Enabled(val userId: String) : MfaEnableOutcome()  // provisionMe まで完了
    data class Failure(val message: String, val error: ApiError) : MfaEnableOutcome()
    data class NetworkFailure(val message: String) : MfaEnableOutcome()
}

fun registerAndAutoLogin(authRepository, userRepository, sessionStore, email, password, publicId, onResult: (RegisterOutcome) -> Unit)
fun startMfaSetup(authRepository, sessionStore, onResult: (MfaSetupOutcome) -> Unit)
fun enableMfaAndProvision(authRepository, userRepository, sessionStore, code, onResult: (MfaEnableOutcome) -> Unit)
```

`registerAndAutoLogin` の中で `register()` 成功 → `login()` 成功 → `provisionMe()` 成功までを直列でやり、SessionStore は **触らない**（Step 3 設計ポイント参照）。`enableMfaAndProvision` の中で `enableMfa()` 成功後に `provisionMe()` を呼び（既に成功している場合は no-op で返るため再呼び出し OK）、最後に `sessionStore.setAuthenticated(userId)` する。

### Step D: ViewModel / ViewState 層

**D-1. `SignUpFlowViewModel.kt`（Android）全面改修**

- 状態フィールドを `email / password / publicId / phase / errorMessage / isSubmitting / mfaSetup (qr/recoveryCodes) / publicIdValidationError` に置き換え
- `phase` は enum (`AccountInput / MfaQr / MfaCodeInput / RecoveryCodes`)
- `submitAccount()`: `AuthRepository.register()` → 成功なら `login()` → `provisionMe()` → phase = MfaQr & `setupMfa()` 自動実行
- `submitMfaCode(code)`: `enableMfa()` → 成功なら phase = RecoveryCodes
- `confirmRecoveryCodes()`: `signupCompletionSignal.arm()` → `sessionStore.setAuthenticated(userId)` を呼ぶ
- `regenerateMfaQr()`: `mfaRegister()` を再呼び出し（非べき等性に対するエスケープハッチ）
- `validatePublicId(value)`: 入力中に `^[a-zA-Z0-9]{4,16}$` をチェックし、`publicIdValidationError` を更新（リアルタイムバリデーション赤字）

**D-2. `SignUpFlowState.swift`（iOS）全面改修**

同等のフィールドに置き換え。`@Published var phase: SignUpPhase` で SwiftUI 側の View 切替に使う。`AuthFlowIos` ファサード経由で API を叩く。

**D-3. `MfaSetupViewModel.kt` 新規作成（Android）**

QR 表示 + 「コードを入力する」CTA → 次画面へ。再生成タップで `setupMfa()` 再実行。

> ※`SignUpFlowViewModel` 1 個に集約しても良いが、状態が肥大するため MFA セットアップ専用 VM を切る方がテストしやすい。実装時に最終判断。

### Step E: UI 層（Compose / SwiftUI）

**E-1. Android: `SignUpAccountScreen.kt` 新規作成（`SignUpCreateScreen.kt` をリプレース）**

- 既存 `SignUpComponents.kt` の `BankTextField` をそのまま使う
- 入力欄は `email` / `password` / `publicId` の 3 つ
- ラベル: 「ユーザーID」、placeholder: 「半角英数字、4〜16 文字」
- 補助テキスト（フィールド下）: 「半角英数字・アンダースコア・ハイフン、1〜64 文字」… ではなく **「半角英数字 4〜16 文字。1〜3 文字は使用不可」** に修正（AuthCore の実バリデーションに合わせる。ヒアリング回答の表記は確定情報の方を採用）
- リアルタイムバリデーション: 入力中のみフィールド下に赤字でエラー文言（空欄時は出さない）
- 409 ハンドリング:
  - `USER_ALREADY_EXISTS` → メール欄下に赤字「このメールアドレスは既に登録されています」+「ログイン画面へ」リンク
  - `PUBLIC_ID_ALREADY_EXISTS` → ID 欄下に赤字「このユーザー ID は既に使われています」
- スタイルは LoginScreen の `FlatTextField` 系の余白・カラー・角丸に揃える（既に `BankTextField` は同等トークンを使っているため流用で OK）

**E-2. Android: `MfaQrScreen.kt` 新規作成**

- 中央に QR コード PNG を表示（`Image(bitmap = ...)`)
- base64 デコードは `expect fun decodeBase64Png(data: String): ImageBitmap?` で抽象化
  - androidMain: `BitmapFactory.decodeByteArray(...)` → `asImageBitmap()`
  - iosMain: `UIImage(data:)` から CGImage を取り出して Skia 経由 ImageBitmap 化
- 下部に「Google Authenticator などのアプリでスキャン」ガイダンス
- 「QR を再生成」リンク（`mfa/register` 非べき等性のエスケープハッチ）
- CTA「コードを入力する」で次画面へ
- **戻るボタン抑止**: `SignUpHeader(onBack = null)`、`BackHandler { /* no-op */ }` で OS バックも抑止

**E-3. Android: `MfaCodeScreen.kt` 新規作成**

- 既存 `MfaVerifyScreen.kt` の OTP 入力 UI を再利用（同じ 6 桁ボックス + hidden TextField 構成）
- ただし **OTP は CTA タップで明示送信**。6 桁完了 / IME Done での自動 submit はしない（CLAUDE.md UX ルール）
- エラー表示は CTA 下に赤字
- 戻るボタン抑止

**E-4. Android: `RecoveryCodesScreen.kt` 新規作成**

- recovery codes 8〜10 個を等幅フォントで縦並び表示
- 「すべてコピー」ボタン（`ClipboardManager`）
- 「保存しました」チェックボックス（必ず ON にしないと CTA enable しない）
- CTA「次へ」で `signupCompletionSignal.arm()` + `setAuthenticated()`
- **戻るボタン抑止 + 「再表示はできません」警告文言**

**E-5. iOS: 同等の SwiftUI View 群を新規作成**

- `SignUpAccountView.swift` (`SignUpCreateView.swift` をリプレース)
- `MfaQrView.swift`
- `MfaCodeView.swift`
- `RecoveryCodesView.swift`
- 戻るボタン抑止: `NavigationLink` ではなく `@State` 駆動の View 切替で行うため、`SignUpHeader(onBack: nil)` で UI 上の戻るアイコンを消す。SwiftUI のシステムバック (画面右スワイプ) は `.gesture(DragGesture()...)` か `.navigationBarBackButtonHidden(true)` で抑止する（NavigationStack を使わない場合は不要だが念のため検証）

### Step F: ルーティング / バックスタック制御

**F-1. `App.kt`（Android）**

- `SignupRoute` enum を `None / Account / MfaQr / MfaCode / RecoveryCodes` に置き換え
- `SignupRoute.Otp` / `SignupRoute.Success` は削除
- `BackHandler` を MFA セットアップ 3 画面に挟み、システムバックを no-op 化
- `RecoveryCodesScreen` の `onFinish` で `signupCompletionSignal.arm()` + `signupViewModel.confirmRecoveryCodes()` を呼ぶ

**F-2. `AppRoot.swift`（iOS）**

- `SignupRoute` enum を同様に置き換え
- `signupRoute = .recoveryCodes` 中はサインアップ動線完了。`signupFlow.confirmRecoveryCodes()` で `setAuthenticated` を Kotlin 側から呼ぶと `session.state` が更新され、AppRoot の上位 switch が `Authenticated` 分岐に入って `welcomeGate.shouldShowWelcome` が true なら `WelcomeView` が出る

**F-3. アプリ kill 中断時の復帰ルート**

- 既存 `LoginViewModel.handleLoginSuccess` で `LoginResult.Authenticated` 受領時に **AuthCore の `mfa_enabled` を確認** して false なら `MfaSetupRequired` 状態に切り替え、Login → MFA セットアップ画面群へ誘導する。
- `AuthCoreUserApi.getProfile()` の DTO `AuthCoreUserResponse` に `mfa_enabled: Boolean` フィールドが既にあるかは未確認 → 実装時に DTO を確認・必要なら追加。
- `MfaSetupRequired` が `SessionState` に増えるので `App.kt` / `AppRoot.swift` の when 分岐に追加する。

### Step G: 既存モック撤去

**削除ファイル一覧:**

- `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/signup/SignUpOtpScreen.kt`
- `iosApp/iosApp/Features/SignUp/SignUpOtpView.swift`
- `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/signup/SignUpCreateScreen.kt` （`SignUpAccountScreen.kt` で置換）
- `iosApp/iosApp/Features/SignUp/SignUpCreateView.swift` （`SignUpAccountView.swift` で置換）
- `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/signup/SignUpSuccessScreen.kt` （Welcome 画面に統合）
- `iosApp/iosApp/Features/SignUp/SignUpSuccessView.swift` （同上）

**削除する state/フィールド:**

- `SignUpFlowViewModel.kt` の `otp: String` フィールドおよび `OTP_LENGTH` 定数、`onOtpChange()` メソッド
- `SignUpFlowState.swift` の `otp: String` プロパティ、`updateOtp()`、`otpLength`
- `App.kt` / `AppRoot.swift` の `SignupRoute.Otp` / `SignupRoute.Success`

**残すファイル:**

- `SignUpComponents.kt` / `SignUpComponents.swift` は全画面で再利用（`PrimaryButton` / `BankTextField` / `SignUpHeader` / `PageIndicator` / `LoginRedirectLink`）

### Step H: KMP 両対応の検証

実装後に下記コマンドを順に実行する:

```bash
./gradlew build
./gradlew :shared:allTests
./gradlew :composeApp:assembleDebug
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

- `shared:allTests` には `AuthRepositoryTest` 既存テストの拡張で `register()` / `setupMfa()` / `enableMfa()` の HappyPath / エラー時挙動を追加する
- `koin verify` テスト（`SharedModuleTest.kt` / `SharedModuleVerifyTest.kt`）が通ることを確認（`AuthApi` のコンストラクタ変更が DI グラフに影響するため）

## 撤去するファイル一覧

| パス | 理由 |
|------|------|
| `composeApp/.../features/signup/SignUpOtpScreen.kt` | AuthCore にメール OTP がないため不要 |
| `composeApp/.../features/signup/SignUpCreateScreen.kt` | `SignUpAccountScreen.kt` でリプレース（public_id 入力欄追加のため別ファイル新設） |
| `composeApp/.../features/signup/SignUpSuccessScreen.kt` | 完了後は WelcomeScreen に統合 |
| `iosApp/.../SignUp/SignUpOtpView.swift` | 同上 |
| `iosApp/.../SignUp/SignUpCreateView.swift` | 同上 |
| `iosApp/.../SignUp/SignUpSuccessView.swift` | 同上 |

ファイル名変更による履歴の喪失を避けるため、git mv は使わず **新規追加 + 削除** で行い、PR 上の差分を読みやすくする（試行錯誤の履歴を残す方針）。

## 完了条件（KMP 両対応）

```bash
./gradlew build                                          # 全ターゲットのビルド確認
./gradlew :shared:allTests                               # shared 層の単体テスト
./gradlew :composeApp:assembleDebug                      # Android APK ビルド
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64    # iOS shared framework
```

- [ ] 上記 4 コマンドが全て通る
- [ ] Android 実機 or エミュレータで Step 1 → Step 6 → ホーム到達まで動作確認
- [ ] iOS シミュレータ (iPhone 15 / iOS 17 以降) で Step 1 → Step 6 → ホーム到達まで動作確認
- [ ] CI (`build-and-check` / `build-ios-framework` / `build-ios-app` / `test-jvm` / `test-ios`) が全通

## 手動検証手順（Android / iOS 両方で実施）

1. **正常系**: 新しい email / password (8 文字以上) / public_id (a-zA-Z0-9 4〜16 文字) を入力 → register 成功 → MFA QR 表示 → Google Authenticator でスキャン → 6 桁コード入力 → recovery codes 表示 → コピー → 「保存しました」ON → 「次へ」→ Welcome → ホーム
2. **email 重複**: 1 で登録済みの email で再度 register → メール欄下に赤字「このメールアドレスは既に登録されています」+「ログイン画面へ」リンクが出る
3. **public_id 重複**: 1 で登録済みの public_id で別 email register → ID 欄下に赤字
4. **public_id バリデーション**:
   - 3 文字以下 → リアルタイム赤字「4 文字以上必要です」
   - 17 文字以上 → リアルタイム赤字「16 文字以下にしてください」
   - 記号入り → 赤字「半角英数字のみ使用できます」
5. **戻るボタン抑止**: MFA QR / コード入力 / recovery codes 画面で OS バック (Android) / 画面右スワイプ (iOS) を試行 → 何も起きないこと
6. **アプリ kill 中断**: MFA QR 表示中にアプリを強制終了 → 再起動 → SplashGate 後、Login 画面が出る → 同じ email/password でログイン → MFA setup required 経路で MFA QR 画面に復帰（新しい secret になる）
7. **TOTP コード誤り**: 6 桁を間違える → CTA 下に赤字「認証コードが正しくありません」
8. **recovery codes コピー**: 「すべてコピー」タップ → クリップボードに 8〜10 個の改行区切りコードが入っている
9. **`/start-with-plan` で実装開始**: `feature/client-bank-21-signup-with-mfa-setup` ブランチが切られ、main 直コミットが起きないこと

## 技術的な補足

### QR コード生成方針

AuthCore (`fuju-system-authentication`) はサーバ側で go-qrcode を使い 256×256px の PNG を生成、`data:image/png;base64,iVBORw0KGgo...` 形式の data URL で返す。クライアントの責務:

1. `qrCodeDataUrl.removePrefix("data:image/png;base64,")` で生 base64 を取り出す
2. `kotlin.io.encoding.Base64.decode(...)` で `ByteArray` を得る (`@OptIn(ExperimentalEncodingApi::class)`)
3. `expect fun decodeBase64Png(bytes: ByteArray): ImageBitmap?` で各プラットフォームの decoder にかける

```kotlin
// shared/commonMain
expect fun decodeBase64Png(bytes: ByteArray): ImageBitmap?

// shared/androidMain
actual fun decodeBase64Png(bytes: ByteArray): ImageBitmap? =
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()

// shared/iosMain (skia 経由)
actual fun decodeBase64Png(bytes: ByteArray): ImageBitmap? =
    org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
```

> Compose Multiplatform 1.10 では `Image.makeFromEncoded` → `toComposeImageBitmap()` で iOS でも ImageBitmap 化できる。SwiftUI ネイティブ画面側は ImageBitmap を経由せず、`UIImage(data: Data(base64Encoded: ...))` で直接 `Image(uiImage: ...)` を出す方が素直。共通化に固執せず、Compose 側だけ expect/actual を使う。

### AuthCore `mfa/register` の非べき等性

- `mfa_enabled = false` 状態で `mfa/register` を再呼び出しすると、サーバ側は **新しい secret/QR/recovery codes を返し、旧 secret は無効化** する。
- そのため Step 4 の戻るボタン抑止は必須。やり直したい場合は同画面内の「QR を再生成」ボタン経由でユーザに明示的にトリガを引かせる。
- 既に `mfa_enabled = true` のユーザが `mfa/register` を呼ぶと `MFA_ALREADY_ENABLED` が返る (`ApiErrorCode.MFA_ALREADY_ENABLED` 既存)。クライアントはこれを検知したら setup フローを抜けて Authenticated 経路に倒す（=正常完了扱い）。

### Recovery codes の扱い

- AuthCore は recovery codes を **平文で 1 度だけ返し、以降は照合用ハッシュしか保持しない**。再表示不可。
- そのため UI 側の「保存しました」チェックボックスを ON にしないと CTA が押せない設計を必須とする。
- スクリーンショット禁止フラグ (`FLAG_SECURE` / iOS の `isSecureTextEntry` 相当) は別タスク扱い（client-bank-21 のスコープ外）。

### auto-login 失敗時の挙動

- `register()` 成功後に裏で叩く `login()` が万一失敗した場合、Step 4 に進めない。
- 設計判断: register は成功しているので **「アカウントは作成されました。再度ログインしてください」** と表示してログイン画面に戻す。次回 login 時に MFA 未セットアップ (`mfa_enabled = false`) のため `MfaSetupRequired` 経路でセットアップに進める。
- ユーザに「失敗したからやり直し」と思わせない文言にする。

### Swift interop の注意点

- `AuthFlowIos.kt` の新 sealed class (`RegisterOutcome` / `MfaSetupOutcome` / `MfaEnableOutcome`) は Obj-C ヘッダに `RegisterOutcomeStarted` / `RegisterOutcomeFailure` のように展開される。Swift 側の switch は `case is RegisterOutcomeStarted:` のように書く（既存の `AuthFlowOutcome` 系と同じ流儀）。
- `AuthFlowIos.kt` ファイル名はそのまま（リネーム禁止 — Swift 側で `AuthFlowIosKt.loginAndProvision(...)` と呼んでいるため）。

## コミット / ブランチ運用

- ブランチ: `feature/client-bank-21-signup-with-mfa-setup` を `main` から切る（`/start-with-plan` の標準動作）
- コミットメッセージ: 件名・本文ともに日本語、Conventional Commits prefix (`feat:` / `refactor:` / `test:` / `docs:`) は英語のまま
- main 直コミット禁止、PR 経由でマージ
- 試行錯誤の履歴は破棄せずブランチに残す（失敗 → revert になっても PR 経由で履歴に残す方針）
