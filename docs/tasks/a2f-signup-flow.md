# A2f サインアップ導線 + 画面実装

## 概要

サインアップフローを Compose Multiplatform / SwiftUI 双方で実装し、Welcome (A2d) からの導線を完成させる。Figma の 5 ノード（NxTECH ファイル）に準拠する。実画面は **3 つ**（残り 2 ノードは同じ画面のキーボード状態違い・OTP 入力中状態）。

## 背景・目的

- Welcome (A2d) と Login (A2e) は実装済みだが、Welcome から「新規登録」に進む先のサインアップ画面群が未実装で、導線がデッドエンドになっている。
- ユーザー初回獲得フローを通すには、Figma に定義済みの画面群を一通り作る必要がある。
- スコープは UI 実装が主軸（API 連携・永続化・本番バリデーションは段階的に別タスク化する想定）。完了画面後の遷移先はモックでホーム or Welcome に戻す。

## フロー全体像

```
Welcome (A2d, 既存) ──「新規登録」ボタン────┐
                                          ↓
Login (A2e, 既存)  ──「新規登録」リンク────┤   ※ 本タスクで追加
                                          ↓
                    [1] アカウント作成 (signup_create)        ← 383-12951 / 296-2092
                            │  ↑「ログイン」リンクで Login へ戻れる（既存導線）
                            ↓ 「次へ」(メール・パスワード入力後)
                    [2] 二段階認証 (signup_otp)               ← 383-14941 (空) / 383-16473 (入力中)
                            ↓ 「確認する」(6桁OTP入力後)
                    [3] 認証成功 (signup_success)             ← 383-16105
                            ↓ 「次へ」
                    ホーム / Welcome（モック遷移、本実装は別タスク）
```

サインアップ画面の入口は **2 つ**:
1. Welcome 画面の「新規登録」ボタン（A2d で配置済み、本タスクで遷移先を実画面に差し替え）
2. **Login 画面の「新規登録」テキストにタップ遷移を配線**
   - A2e で「アカウントをお持ちでない方は **新規登録**」のテキストは既に配置済み（Compose: `LoginScreen.kt:347` 周辺、SwiftUI: `LoginView.swift:178` 周辺）
   - A2e のコードコメントにも `// 「新規登録」 のタップ動線は A2f で配線する` と明示されている
   - 本タスクで `新規登録` 部分の onClick / `.onTapGesture` を有効化し `signup_create` に遷移させる（文言・配色は変更しない）

ページインジケータの実装上の意味:
- 画面1: 4 ドット中 1 つ目アクティブ（フローを 4 ステップとして見せる UI 上の見栄え。実画面は 3 つだが、Figma の指定通り表示する）
- 画面2: 3 ドット中 2 つ目アクティブ
- 画面3: 3 ドット中 3 つ目アクティブ

→ 実装は **画面ごとに固定の Pager 風インジケータ** をそのまま埋め込む（実 Pager は不要）。

## 対象 Figma ノード

`https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH` の 5 ノード:

| 順 | node-id | 役割 | 状態 |
| --- | --- | --- | --- |
| 1 | `383-12951` | アカウント作成 | キーボード非表示（初期表示） |
| 2 | `296-2092`  | アカウント作成 | キーボード表示中（フォーカス時） |
| 3 | `383-14941` | 二段階認証 OTP | OTP 空・キーボード表示 |
| 4 | `383-16473` | 二段階認証 OTP | OTP 入力途中（4桁入った状態） |
| 5 | `383-16105` | 認証成功 | 完了画面 |

→ **実装すべき画面は 3 つ**。1/2 は同じ画面でキーボード制御の違い、3/4 は同じ画面で OTP 入力進捗の違いを示すだけ。

## デザイントークン（Figma 共通）

| トークン | 値 |
| --- | --- |
| 背景 | `#F6F7F9` |
| カード/入力背景 | `#FFFFFF` |
| プライマリ（CTA・リンク） | `#FF1E9E` |
| プライマリテキスト | `#111111` |
| セカンダリテキスト | `#6E6F72` |
| プレースホルダ | `#DADBDF` |
| サブテキスト | `#64748B` |
| リンク（規約） | `#006CD7` |
| Divider 線 | `#E9E9EC`、ラベル文字 `#C5C5CB` |
| ページインジケータ Active | `#111111`（35×6 角丸20px） |
| ページインジケータ Inactive | `#E1E2E4`（6×6 円） |
| OTP ボックス枠線（下線アクティブ） | `#333436`（高さ6px、幅36px or 6px円） |
| OTP ボックス未入力 | 下線 `#E8E9ED` |

| 寸法 | 値 |
| --- | --- |
| 画面幅基準 | 393px（iPhone 想定。Compose は dp、SwiftUI は points で同値扱い） |
| プライマリボタン | 高さ 48dp / 角丸 16dp / 横 24dp パディング / フォント 16sp Semibold（白） |
| 入力フィールド | 高さ 48dp / 角丸 16dp / 横 24dp パディング / 白背景 |
| 画面パディング | 横 24dp |
| ヘッダ（戻る + ロゴ） | 上 53dp、戻るアイコン 48dp 円形、ロゴ画像 ~108×29dp |
| OTP ボックス | 52×60dp、6 個並び、ボックス間 gap = 0（content-stretch flex） |

フォント:
- タイトル: SF Pro Bold 20sp（日本語は Noto Sans JP Bold）
- サブタイトル: SF Pro Medium 14sp
- ボタン: SF Pro Semibold 16sp
- 規約リンク: SF Pro Medium 12sp

## 影響範囲

- モジュール: `composeApp/`（Android & 共通 UI）/ `iosApp/`（SwiftUI）
- ソースセット:
  - `composeApp/src/commonMain` — サインアップ画面群、ナビゲーション分岐の追加
  - `iosApp/iosApp/` — SwiftUI 版サインアップ View 群
  - `shared/src/commonMain` — メール形式・パスワード強度などの簡易検証ユーティリティ（必要なら）。API 連携は本タスクでスコープ外。
- 破壊的変更: なし。Welcome の「新規登録」ボタンの遷移先を Stub から実画面に差し替え。
- 追加依存: 原則なし。

## アーキテクチャ方針（A2d / A2e 踏襲）

- Compose 側
  - 各画面 = `*Screen.kt`（Composable, stateless 寄り）+ `*UiState`
  - サインアップフロー全体の入力（email / password / otp）を保持する `SignUpFlowViewModel`（`androidx.lifecycle.viewmodel`）を 1 つ用意し、3 画面で共有
  - ナビゲーションは A2d の `AppNavHost` 相当に `signup_create` / `signup_otp` / `signup_success` ルートを追加
- SwiftUI 側
  - 各画面 = `SignUpCreateView.swift` / `SignUpOtpView.swift` / `SignUpSuccessView.swift`
  - `SignUpFlowState`（`ObservableObject`）をフローのルートで `@StateObject` 保持、子ビューに `@EnvironmentObject` で配布
- 状態は当面ローカル `MutableStateFlow` / `@Published`。サーバ連携は別タスク。

## 画面別仕様

### Screen 1: アカウント作成（`383-12951` / `296-2092`）

- **役割**: メールアドレス（または ユーザーID）+ パスワードを入力して次へ進む。代替手段として「Googleで続ける」を提示。
- **レイアウト**:
  - ヘッダ: 戻る（`<`）アイコン左、中央にロゴ画像
  - タイトル「アカウントの作成」（20sp Bold、`#111`、中央寄せ）
  - サブタイトル「メールを入力」（14sp Medium、`#6E6F72`、中央寄せ）
  - 入力フィールド × 2（縦に 8dp gap）
    - プレースホルダ「メールアドレス または ユーザーID」
    - プレースホルダ「パスワード」（`visualTransformation = PasswordVisualTransformation()` / SwiftUI は `SecureField`）
  - Divider「または」（左右に細い線、中央テキスト 12sp `#C5C5CB`）
  - 「Googleで続ける」ボタン（白背景、影付き、左に Google アイコン、中央テキスト「Googleで続ける」16sp `#111`）
  - 下部リンク群:
    - 「アカウントをお持ちの方は **ログイン**」（`ログイン` は `#FF1E9E` 下線、Login 画面に遷移）
    - 「登録することで、**利用規約** と **プライバシーポリシー** に同意します」（`#006CD7` リンク）
  - ページインジケータ（4 ドット中 1 つ目アクティブ、画面下部）
  - プライマリ CTA「次へ」（画面下部、`#FF1E9E`、横 24dp パディング、enable は両フィールドが空でない時）
- **バリデーション（簡易）**:
  - メール: 空でない、`@` を含む形式（厳密な RFC は不要）
  - パスワード: 空でない（最低 1 文字。本番では強度要件を別タスクで追加）
- **遷移**:
  - 「次へ」: `signup_otp` へ。入力値は flow state に保持
  - 「Googleで続ける」: 本タスクではログ出力のみ（OAuth 実装は別タスク）
  - 「ログイン」リンク: 既存 Login 画面 (A2e) に遷移
  - 戻る（`<`）: Welcome に戻る
- **キーボード状態**: TextField フォーカス時に表示される（OS 標準挙動）。Figma の `296-2092` はその状態を表現したもので、実装上は特別な対応不要。

### Screen 2: 二段階認証 OTP（`383-14941` / `383-16473`）

- **役割**: 登録メールに送信された 6 桁コードを入力して認証する。
- **レイアウト**:
  - ヘッダ: 戻る + ロゴ
  - タイトル「二段階認証」（20sp Bold、左寄せ、最大幅 289dp）
  - サブタイトル「登録したメールに6桁のコードを送信しました」（14sp Regular、`#64748B`、行間 21dp）
  - OTP 入力 6 ボックス（横並び、各 52×60dp）:
    - 未入力: 下に薄い円ドット（`#E8E9ED`、6×6dp）
    - 入力済み（focus 中）: 大きな数字（48sp Bold `#333436`）+ 下にピル `#333436`（36×6dp 角丸23dp）
    - 入力済み（focus 外れ）: 数字のみ + 下に薄いドット
    - フォーカス中の空ボックス: 下に長いピル（次の入力位置を示す）
  - ページインジケータ（3 ドット中 2 つ目アクティブ）
  - プライマリ CTA「確認する」（`#FF1E9E`、6 桁全部入力で enable）
- **入力挙動**:
  - 数字キーボードを表示（`KeyboardOptions(keyboardType = KeyboardType.NumberPassword)` / SwiftUI は `.keyboardType(.numberPad)`）
  - 1 文字入力で次のボックスにフォーカス自動移動（pasteable な 1 つの hidden TextField + 表示用 6 Box の構成が無難）
  - Backspace で前のボックスに戻る
- **バリデーション**: 6 桁の数字。本タスクではモックで「123456」固定 or 任意の6桁を許容。
- **遷移**:
  - 「確認する」: モックで成功扱い → `signup_success` へ
  - 戻る: `signup_create` へ（入力値を flow state で保持）

### Screen 3: 認証成功（`383-16105`）

- **役割**: 成功フィードバック → ホームへの導線。
- **レイアウト**:
  - ヘッダ: 戻る + ロゴ（Figma 上は配置されているが、本画面では戻るは無効 or 非表示が自然）
  - 中央に大きく「認証が / 成功しました」（40sp Bold `#111`、2 行表示）
  - ページインジケータ（3 ドット中 3 つ目アクティブ）
  - プライマリ CTA「次へ」
- **遷移**:
  - 「次へ」: 本タスクではモック挙動 → Welcome に戻す（ホーム画面実装は別タスク）。flow state はクリアする。

## 共通コンポーネント候補

A2e で導入された以下が再利用可能。なければ新規作成して既存 Login も移行する。
- `PrimaryButton`（高さ48dp、角丸16dp、`#FF1E9E`、enable/disable 制御）
- `BankTextField`（高さ48dp、角丸16dp、白背景、プレースホルダ `#DADBDF`）
- `PageIndicator`（active index と total を受け取り、35×6 ピル + 6×6 ドットを描画）

新規追加が必要なもの:
- `OtpInput`（6 桁、フォーカス制御、状態に応じた下線描画）
- `GoogleSignInButton`（白背景・影・Google ロゴ・テキスト）
- `LegalAgreementText`（利用規約 / プライバシーポリシーのリンク付きテキスト。クリックハンドラを props として受け取る）

## 実装ステップ

1. **共通コンポーネント整備**: A2e の既存 `PrimaryButton` / `BankTextField` を確認し、サインアップ用に必要なら抽出・移行。`PageIndicator` を新規追加。
2. **ナビゲーション骨組み**:
   - Compose: `AppNavHost` に `signup_create` / `signup_otp` / `signup_success` の 3 ルートを追加。Welcome の「新規登録」ボタン onClick を `signup_create` に接続。
   - SwiftUI: 既存 `RootView` の遷移分岐に `signupCreate` / `signupOtp` / `signupSuccess` を追加。
   - **Login (A2e) の「新規登録」タップ動線を配線**: 既存 `LoginScreen.kt:347` 付近および `LoginView.swift:178` 付近のスタブを解消し、`新規登録` 部分のタップで `signup_create` に遷移するようにする。Compose では `AnnotatedString` の `pushStringAnnotation` + `ClickableText`（または `inlineContent`）、SwiftUI では `Text` を分割して `.onTapGesture` を付与する形が無難。文言・配色は A2e のまま変更しない。
3. **`SignUpFlowViewModel` / `SignUpFlowState` 追加**: email / password / otp の 3 フィールドを保持し、3 画面から共有。
4. **Screen 1 実装（Compose & SwiftUI）**: アカウント作成画面。`OtpInput` 以外のコンポーネントで完結。
5. **Screen 2 実装（Compose & SwiftUI）**: OTP 入力画面。`OtpInput` 新規実装。
6. **Screen 3 実装（Compose & SwiftUI）**: 認証成功画面。
7. **画面間遷移とバリデーション**: 「次へ」の enable 制御、戻る挙動、Welcome リンク。
8. **モック挙動**: 「Googleで続ける」「次へ（成功画面）」はログ出力 + Welcome 戻し or no-op。利用規約 / プライバシーポリシーリンクも本タスクでは no-op or `Log.d`。
9. **Compose Preview / SwiftUI Preview** を 3 画面ぶん追加（OTP は空・入力途中の 2 状態を Preview）。
10. **動作確認**: Android 実機 or エミュレータ + iOS シミュレータで Welcome → Screen1 → Screen2 → Screen3 → Welcome を踏破。

## 検証

- [ ] `./gradlew build` が通る
- [ ] `./gradlew :composeApp:assembleDebug` が通る
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が通る（shared を変更した場合）
- [ ] Android: Welcome →「新規登録」→ Screen1 → Screen2 → Screen3 →「次へ」で Welcome に戻る
- [ ] Android: Login →「新規登録」リンク → Screen1 に遷移する
- [ ] iOS シミュレータ: 上記 2 ルートとも同じ挙動になる
- [ ] Screen1 で「次へ」が email/password 空のとき disable
- [ ] Screen2 で「確認する」が OTP 6 桁未満のとき disable
- [ ] Screen2 で OTP 入力時に次のボックスへフォーカス自動移動
- [ ] 各画面の Compose Preview / SwiftUI Preview が表示される
- [ ] 戻るボタンで前の画面まで戻れる（入力値は flow state で保持）
- [ ] 配色・余白・角丸が Figma に一致

## 技術的な補足

- **expect/actual は不要**: UI と画面遷移が中心で、プラットフォーム依存 API は使わない。
- **A2d / A2e との整合**: ボタン・テキストフィールド・色トークンは A2e で確立されたものを再利用。サインアップ専用の追加スタイルは `OtpInput` / `GoogleSignInButton` のみ。
- **状態保持**: 戻る・進むで入力値が保持されるよう、`SignUpFlowViewModel` / `SignUpFlowState` をフロー全体（NavGraph or NavigationStack のルート）でスコープする。
- **OTP の実装**: 6 個の `BasicTextField` を並べる方式と、1 個の hidden TextField + 表示用 6 Box の方式がある。後者の方が backspace / paste の挙動が自然になりやすい。SwiftUI も同様。
- **アクセシビリティ**: フォームには `contentDescription` / `accessibilityLabel` を必ず付与。OTP は読み上げ対応として SMS 自動入力を有効化（`autofill = SmsCodeAutofill` / SwiftUI は `.textContentType(.oneTimeCode)`）。
- **アセット**: ロゴ画像（Figma の `imgFrame50` / `imgFrame30` = 同一ロゴ、108×29dp）、Google アイコン（`imgGroup`）、戻る矢印（`imgVector`）を Figma から書き出して `composeApp/src/commonMain/composeResources/drawable/` と `iosApp/iosApp/Assets.xcassets/` に配置。すでに A2d/A2e で配置済みのものは流用。
- **Notion タスク**: 実装着手時にクライアント側プレフィックスのタスク（A2f-* 等）を Notion に登録。ブランチ名は `feature/a2f-signup-flow` を想定。
