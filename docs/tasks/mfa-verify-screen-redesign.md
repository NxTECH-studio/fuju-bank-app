# 二段階認証 (MFA) 画面の Figma リデザイン適用

## 概要

ログイン時の MFA 入力画面 (`MfaVerifyScreen.kt` / `MfaVerifyView.swift`) を Figma の
最新デザインに刷新する。OTP slot UI（6 個の下線スロット）+ ピンク CTA + 成功後の
3 画面オンボーディング（成功 →「ようこそ」→ fujupay ロゴ → ホーム）を Android / iOS
両プラットフォームで実装。MFA factor は **TOTP（既存）のまま**で、UI のみリデザイン。

> **優先度**: `transaction-listing-dto-alignment.md` および
> `transaction-history-production-ready.md` より**前**に着手。
> backend 側で MFA 必須化が走っているため UI を整える必要が出てきた。

## 背景・目的

- backend（fuju-bank-backend / authcore）で MFA 必須化が進行中で、ログイン後の MFA 画面が
  「すべてのユーザが必ず通る関門」になった。現状の `MfaVerifyScreen` は Material3 デフォルトの
  `OutlinedTextField` + Tab で組まれた暫定 UI で、本番運用に堪えない見た目。
- Figma で「OTP slot UI + 成功遷移演出 + fujupay ブランドロゴ」のリデザインが完成しており、
  ここに UI を寄せる。
- 既存実装の TOTP / Recovery タブは Recovery 側を本タスクではスコープ外とし、UI から仮設で
  隠す（裏の `AuthRepository.verifyMfa(recoveryCode=...)` ロジック・SessionStore は無変更）。
- MFA factor は **TOTP のまま**（QR コード →Authenticator アプリ →6 桁入力）。Figma 上の
  「登録したメールに6桁のコードを送信しました」というキャプションは実装時に
  「認証アプリの 6 桁コードを入力してください」相当に差し替える。

### Figma ノード（順番に流れる導線）

| # | nodeId | 役割 |
|---|---|---|
| 01 | `383:14941` | 二段階認証画面（空状態、6 slot のうち 1 個目だけ active） |
| 02 | `383:16473` | 二段階認証画面（4 桁入力中、5 個目に active） |
| 03 | `383:16105` | 認証成功画面（「認証が成功しました」+ 「次へ」+ ページインジケータ 3 ドット） |
| 04 | `383:16889` | 「ようこそ」画面（中間） |
| 05 | `383:17075` | fujupay ロゴ画面（最終） |

screenshot は `docs/figma-assets/mfa-redesign/01-383-14941.png` …`05-383-17075.png` に保存済み。

## 影響範囲

- モジュール: `composeApp` / `iosApp`
- ソースセット:
  - `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/auth/`
    - `MfaVerifyScreen.kt`（OTP slot UI へ刷新）
    - `MfaVerifyViewModel.kt`（タブ削除、6 桁入力モデルに刷新、成功後オンボーディング状態を追加）
    - 新規: `MfaSuccessScreen.kt`（03 認証成功）
    - 新規: `MfaWelcomeScreen.kt`（04 ようこそ）
    - 新規: `MfaBrandScreen.kt`（05 fujupay ロゴ）
    - もしくは上記 3 つを単一の `MfaPostVerifyOnboarding.kt` 内 sealed state で表現
  - `iosApp/iosApp/Features/Auth/`
    - `MfaVerifyView.swift`（同上）
    - `MfaVerifyViewModel.swift`（同上）
    - 新規: `MfaSuccessView.swift` / `MfaWelcomeView.swift` / `MfaBrandView.swift`
      （または同様に単一ファイル sealed state）
  - `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/theme/`
    - 既存トークン (`FujuBankColor.AccentPink` 等) を利用、必要なら不足色を追加
- 破壊的変更:
  - `MfaVerifyUiState` / Swift 側 `MfaVerifyViewModel` の公開フィールド構成が変わる
    （`mode: MfaInputMode` を削除、新たに `phase: MfaPhase` 系の sealed 状態を追加）
  - 共通 ABI（shared framework）には**手を入れない**。`AuthRepository.verifyMfa` /
    `SessionStore.MfaPending → Authenticated` の API は無変更で、UI 内で
    オンボーディング 3 画面を表示してから最終的に `setAuthenticated` を呼ぶ方式。
- 追加依存: なし（OTP slot UI は Material3 / SwiftUI 標準コンポーネントで自前実装）

## 実装ステップ

### A. 共通方針

1. **SessionState は変更しない**
   - 「MFA verify 成功 → onboarding 3 画面 → ホーム」の遷移を `SessionState.MfaPending`
     から `SessionState.Authenticated` への単一遷移として扱い、UI 内部で 3 画面を
     表示するシーケンスを実装する。
   - `verifyMfa` 成功 + `provisionMe` 成功までは内部 phase を `Verifying` →
     `Onboarding(stage = Success/Welcome/Brand)` で持つ。
   - 最終 stage で `sessionStore.setAuthenticated(...)` を呼んで Authenticated 化し、
     既存の root navigator がホームへ遷移する。
2. **Recovery code は UI から仮設で隠す**
   - `MfaInputMode` enum と Tab UI を削除。
   - `AuthRepository.verifyMfa(preToken, code = totp, recoveryCode = null)` を呼ぶ形に
     固定（`recoveryCode` パラメータは残す。後続タスクで Recovery 画面を別途作る前提）。

### B. Android 実装

3. **`composeApp/.../features/auth/MfaVerifyViewModel.kt`**
   - `MfaInputMode` 削除、`MfaVerifyUiState` を以下に再設計:
     ```kotlin
     data class MfaVerifyUiState(
         val phase: MfaPhase = MfaPhase.Input,
         val code: String = "",
         val isSubmitting: Boolean = false,
         val errorMessage: String? = null,
     )

     sealed interface MfaPhase {
         object Input : MfaPhase
         data class Onboarding(val stage: OnboardingStage) : MfaPhase
     }

     enum class OnboardingStage { Success, Welcome, Brand }
     ```
   - `onCodeChange(value)`: 数字のみ・最大 6 桁にサニタイズ。
   - `submit()`: 6 桁未満なら「6 桁を入力してください」エラー。
     verify → provision 成功で `phase = Onboarding(Success)` に遷移。
   - `advanceOnboarding()`: 成功画面 → ようこそ → ロゴ の順で進め、Brand 完了時に
     `sessionStore.setAuthenticated(userId)`。
   - `cancel()`: Input phase で表示する戻るボタンから呼ぶ。SessionStore.clear()。

4. **`composeApp/.../features/auth/MfaVerifyScreen.kt`**
   - `state.phase` で 4 種類の Composable を出し分け:
     - `MfaPhase.Input` → `MfaVerifyInputScreen`（OTP slot UI）
     - `Onboarding(Success)` → `MfaSuccessScreen`
     - `Onboarding(Welcome)` → `MfaWelcomeScreen`
     - `Onboarding(Brand)` → `MfaBrandScreen`
   - **`MfaVerifyInputScreen`**:
     - ヘッダ行: 戻るアイコン（`ArrowBack`）+ fujupay ロゴ画像
     - タイトル: 「二段階認証」（typography: 既存 `headlineSmall` 相当 or theme トークン）
     - 説明文: 「認証アプリの 6 桁コードを入力してください」（Figma の「メールに送信」は誤情報のため差し替え）
     - **OTP slot UI** (`OtpSlotRow` 新規 Composable):
       - 6 個の Box を `Row` に並べ、各 Box は幅 32dp 程度で下線（active = 太い黒線、未入力 = 灰色のドット）
       - 中央の 1 文字を `Text` で表示。単一の隠し `BasicTextField` をフルサイズで重ねて入力を吸収し、各 slot は `state.code[i]` を表示（一般的な OTP 入力の実装パターン）
       - フォーカスは初期表示時に自動取得
       - paste 対応・バックスペース対応・Number keyboard
       - 6 桁完了で自動的に submit を kick するかどうかは未決事項（CTA タップでも可）
     - エラー文（`state.errorMessage`）は OTP slot 直下に赤テキスト
     - 下部 CTA: 全幅ピンクの「確認する」ボタン（disabled: code.length < 6 || isSubmitting）。色は `FujuBankColor.AccentPink`（`MaterialTheme.colorScheme.primary` の現行マッピングを確認して使い分け）
     - 下部 SafeArea / IME パディング。
   - **`MfaSuccessScreen`** (03):
     - ヘッダ: 戻る + fujupay ロゴ
     - 中央: 大見出し 2 行「認証が\n成功しました」
     - 下部: ページインジケータ（3 ドット、2 個目アクティブ）+ 全幅ピンク「次へ」ボタン
     - 「次へ」タップで `viewModel.advanceOnboarding()`
   - **`MfaWelcomeScreen`** (04):
     - 中央に「ようこそ」テキストのみ
     - 自動進行（1.5〜2 秒）or タップで進行 → 未決事項
     - SafeArea 上下を維持
   - **`MfaBrandScreen`** (05):
     - 中央に fujupay ロゴ画像（既存スプラッシュと同等、ただし別スクリーンとして実装）
     - 自動進行（1.5〜2 秒程度）→ `viewModel.advanceOnboarding()` で `setAuthenticated`
   - 4 画面は Compose の `AnimatedContent` で fade トランジションを付ける（細部は実装時）

5. **`composeApp/.../theme/`**
   - 必要なら `FujuBankColor.AccentPink` / 「次へ」ボタンの押下時 ripple 色 / 下線色 (active=黒, idle=#999) などの不足トークンを追加。既存 token で済むかは実装着手時に確認。

### C. iOS 実装

6. **`iosApp/.../Features/Auth/MfaVerifyViewModel.swift`**
   - `InputMode` enum 削除、`MfaVerifyViewModel` を Android と同形に再設計:
     ```swift
     enum MfaPhase: Equatable {
         case input
         case onboarding(OnboardingStage)
     }
     enum OnboardingStage { case success, welcome, brand }
     ```
   - `@Published var phase: MfaPhase = .input` / `code` / `isSubmitting` / `errorMessage`
   - `submit()`: 6 桁未満なら早期 return + エラー設定。`AuthRepository.verifyMfa(...)` →
     `provisionMe` 成功で `phase = .onboarding(.success)`。
   - `advanceOnboarding()`: success → welcome → brand → `sessionStore.setAuthenticated(...)`
   - `cancel()`: input phase の戻るボタンから呼ぶ。

7. **`iosApp/.../Features/Auth/MfaVerifyView.swift`**
   - `phase` で `switch` してサブビュー出し分け
   - **`MfaVerifyInputView`**:
     - ヘッダ: `Button(action: viewModel.cancel) { Image(systemName: "chevron.left") }` + fujupay ロゴ Image
     - 「二段階認証」タイトル + 「認証アプリの 6 桁コードを入力してください」説明
     - **OTP slot UI**: 6 個の下線セル + 隠し `TextField` の重ね合わせで実装
       - SwiftUI の場合、見た目は `HStack(spacing: ...)` の 6 個 `OtpSlotCell`（active なら下線太く、それ以外は灰ドット）+ 上に `TextField("", text: $viewModel.code)` を `.opacity(0.001)` で重ねる
       - `.keyboardType(.numberPad)` / `.onChange(of: code)` で 6 桁に丸める
       - 自動フォーカスは `@FocusState` を `.onAppear { isFocused = true }` で取得
     - エラー赤テキスト
     - ピンク CTA（`Button` → `RoundedRectangle` ピル形状、disabled で 50% opacity）
   - **`MfaSuccessView`** / **`MfaWelcomeView`** / **`MfaBrandView`** を分離
   - 4 画面切替は `withAnimation(.easeInOut)` + `phase` の差替えで fade

8. **`iosApp/.../App/AppRoot.swift`**
   - 既に `SessionState.MfaPending` で `MfaVerifyView` を表示する分岐があるはず。
     確認のみで原則無変更（オンボーディングは MfaVerifyView 内で完結するため）。

### D. テスト & 検証用調整

9. **既存テスト**
   - `MfaVerifyViewModel` / Swift 側 ViewModel に対するロジックテストはほぼ無いはずだが、
     既存があれば mode 削除に伴うシグネチャ変更で更新する。
   - 共通 ABI（`AuthRepository.verifyMfa` / `SessionStore`）は変えないため、
     shared テストへの影響は無し。

10. **debug ビルドでの動作確認用ヘルパ**
    - 現状 `BuildKonfig.USE_DUMMY_PROFILE` 等の debug フラグがあるなら、
      MFA verify をスキップしてオンボーディング 3 画面だけ流せるショートカットがあると
      実装中に楽。必須ではないので時間がなければ省略。

## 検証

- [ ] `./gradlew build` が通る
- [ ] `./gradlew :composeApp:assembleDebug` が通る
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が通る（shared に変更が及ばない想定だが念のため）
- [ ] `./gradlew :shared:allTests` が通る（shared 無変更のため regression のみ確認）
- [ ] **Android 実機 / エミュレータ**:
  - [ ] MFA 必須ユーザでログインすると Figma 通りの OTP slot 入力画面が出る
  - [ ] 6 桁入力で「確認する」が enabled、誤コードでエラー赤テキスト表示
  - [ ] 正コードで成功画面 →「次へ」→「ようこそ」→ fujupay ロゴ → ホームの順に遷移
  - [ ] 戻るボタンで `SessionStore.clear()` され Login 画面へ戻る
  - [ ] paste 6 桁で全 slot が埋まる、バックスペースで 1 桁ずつ消える
- [ ] **iOS シミュレータ**: 上記同等を確認
- [ ] release ビルドで `BANK_API_BASE_URL` を本番に向けても遷移ロジックが破綻しないこと
- [ ] 既存の Recovery code 検証パス（`AuthRepository.verifyMfa(recoveryCode=...)`）が
  コードレベルで残っていること（後続タスク用）

## 技術的な補足

### OTP slot UI の実装パターン

- Compose / SwiftUI とも「単一の隠し TextField + 視覚用 Box × 6」が定番。
  - 文字単位フォーカス制御は煩雑＆IME と相性悪いため、隠し TextField に全文字を集約する方が安定。
  - active slot のハイライトは `state.code.length` をインデックスとして判定。
- フォントは Figma を見る限り mono 系の数字（`monospace` ファミリ or `Roboto Mono` 等）。実機確認時に既存 typography トークンと突き合わせて決める。

### 4 画面間トランジション

- Compose: `AnimatedContent(targetState = state.phase)` + `fadeIn() + fadeOut()` が無難。
- SwiftUI: `.transition(.opacity)` + `withAnimation(.easeInOut(duration: 0.3))`。
- 自動進行（Welcome / Brand）は `LaunchedEffect(stage) { delay(1500); viewModel.advanceOnboarding() }` / `.task { try? await Task.sleep(...) }` パターン。

### 既存 SplashScreen / WelcomeScreen との関係

- 既存の `composeApp/.../splash/SplashScreen.kt` / `iosApp/.../Splash/SplashGate.swift` は
  アプリ起動直後の bootstrap 中に表示される別物。MFA 後オンボーディングは別 UI として
  独立実装する（既存ファイルには触らない）。
- 既存の `composeApp/.../features/welcome/WelcomeScreen.kt` は signup 導線で使われている
  「Welcome」画面で、本タスクの 04「ようこそ」とは別物。Figma 04 は MFA 専用の中間スクリーンとして新規作成する。

## Out of Scope（後続タスクで対応）

1. **Recovery code 入力画面の Figma 化** — 別途 Figma を起こして再実装する。今回は UI から
   仮設で隠すだけ。
2. **MFA factor を email OTP / SMS など TOTP 以外に拡張** — backend 次第。
3. **MFA セットアップ（QR コード提示・初回 enroll）画面の Figma 化** — signup フロー側で別 PR。

## 未決事項

- **6 桁入力完了で自動 submit するか、CTA タップ必須か** — UX 上は自動 submit が早いが、
  誤入力時のキャンセル余地がなくなる。Figma に決定情報がないため実装時に user に確認 or
  両方試して判断。デフォルトは「CTA タップ必須」で進める想定。
- **Welcome (04) / Brand (05) の進行方式（自動 or タップ）** — Figma にボタンが無いので
  自動進行が妥当。タイマー秒数 (1.5s / 2s) は実装時に微調整。
- **Brand (05) と既存 SplashScreen のロゴ表示の一貫性** — フォント / スケール / レイアウトを
  確認し、ロゴアセットを共用するか別アセットにするか決める。
- **Figma の説明文「登録したメールに6桁のコードを送信しました」の差し替え文言確定** —
  「認証アプリの 6 桁コードを入力してください」を提案。レビューで確定。
- **エラーメッセージのフォーマット** — `AuthErrorMessages.forMfa(error)` 既存ロジックの
  文言が Figma の見た目（赤テキスト）に合うか実機で確認。冗長なら短縮検討。
