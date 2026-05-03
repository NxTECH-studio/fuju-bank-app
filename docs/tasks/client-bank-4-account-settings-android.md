# 銀行アプリクライアント：アカウント設定画面（ハブ + 通知設定）Android 実装

## 概要

ボトムナビ「アカウント」タブから到達する **アカウントハブ画面** を Figma `697:8394` ベースで Android (Compose) に実装し、その配下に **通知設定画面**（Figma `718:7332`）と **準備中画面**（プライバシー設定 / アカウント情報変更の遷移先兼用）を追加する。あわせて両プラットフォームで再利用する `:shared` 側の永続化基盤・プロフィール提供ヘルパもこのタスクで先行整備する。iOS 実装は後続の client-bank-5 で実施する。

## 背景・目的

### 経緯

- Task 2 / Task 3 でホーム / 取引履歴 / 取引詳細を Android (Compose) と iOS (SwiftUI) の両方で Figma 準拠に確定済み
- ボトムナビの「アカウント」タブは Android では `AccountPlaceholderScreen` でダミー表示のままになっている
- Figma 上では `697:8394`（アカウント）と `718:7332`（通知設定）の 2 画面が確定しており、これを実装してプレースホルダを置き換える必要がある

### 目的

- 「アカウント」タブの Android プレースホルダを廃止し、Figma 準拠のハブ画面と各子画面を表示する
- 通知設定（着金通知 / 転送通知）のオン・オフをユーザーが切り替えられるようにし、`multiplatform-settings` で永続化する
- まだ画面が確定していないプライバシー設定 / アカウント情報変更には共通の **準備中画面** を表示し、後続タスクで本実装に差し替える土台を作る
- iOS でも同じ画面構成を載せられるよう、`:shared` 側の基盤（`NotificationSettingsPreferences`、`AccountProfileProvider`）を本タスクで整える

## スコープ

- **`:shared` 側の追加（Android / iOS 両方で使う共通基盤）**
  - `NotificationSettingsPreferences`（commonMain、`SignupWelcomePreferences` と同パターン、`StateFlow` 公開）
  - Koin への登録
  - `AccountProfileProvider` 的なダミー切替ヘルパ（`BuildKonfig.USE_DUMMY_PROFILE` 連動）
- **アカウントハブ画面** (`AccountHubScreen`): Figma `697:8394` 準拠
  - プロフィールカード（円形アバター、ユーザー名、SNS 出典バッジ + サブテキスト）
  - アカウント情報セクション（表示名、メールアドレス）
  - 設定セクション 3 行（プライバシー設定 / 通知設定 / アカウント情報変更）
- **通知設定画面** (`NotificationSettingsScreen`): Figma `718:7332` 準拠
  - 着金通知トグル（「ふじゅ〜が届いたとき」相当）
  - 転送通知トグル（「送金が完了したとき」相当）
  - 値は `multiplatform-settings` で永続化
- **準備中画面** (`ComingSoonScreen`)
  - 中央に「準備中です」表示、戻るボタン付きヘッダー
  - タイトル文字列のみ差し替えてプライバシー設定 / アカウント情報変更から再利用
- **タブ配線**
  - `RootScaffold` のアカウントタブ destination を `AccountPlaceholderScreen` から `AccountHubScreen` に差し替え、子画面への手動スタック遷移を組む

### アウトオブスコープ

- **iOS 実装は client-bank-5 (`client-bank-5-account-settings-ios.md`) で実施**
- プライバシー設定 / アカウント情報変更の本実装（次タスク以降）
- バックエンド API による実プロフィール取得（本タスクではダミーまたは Figma 文字列）
- 通知設定の OS 通知許可ダイアログ連携（トグル状態の永続化のみ）
- ログアウト導線の追加（Figma 上に存在しないため対象外）

## 着手条件

**Task 3 (`client-bank-3-ios-multiplatform-integration.md`) 完了後に着手**。
理由: `:shared` 側を本タスクで拡張するため、iOS SwiftUI 基盤が安定している（`IosStateFlowWrapper` / Koin / Theme が確定している）状態でスタートする方が、後続 client-bank-5 で参照する API が固まりやすい。

## 影響範囲

- モジュール: `:shared` / `:composeApp`
  - `:shared/commonMain`: `NotificationSettingsPreferences` 追加、Koin モジュール拡張、`AccountProfileProvider` 追加
  - `:composeApp/androidMain`: `features/account/` 配下に画面 3 種追加、`RootScaffold` 配線変更
- 破壊的変更:
  - アカウントタブの destination が `AccountPlaceholderScreen` から `AccountHubScreen` に置き換わる
  - `:shared` の Koin モジュールに `NotificationSettingsPreferences` / `AccountProfileProvider` が追加される（公開 API 拡張、既存呼び出しへの影響なし）
- 追加依存:
  - なし。`multiplatform-settings` は既存利用 (`SignupWelcomePreferences`) のものをそのまま使う

## 技術アプローチ

### `:shared` 側設計

#### `NotificationSettingsPreferences`

`commonMain` に `SignupWelcomePreferences` と同じパターンで実装:

```kotlin
class NotificationSettingsPreferences(private val settings: Settings) {
    private val _depositEnabled = MutableStateFlow(settings.getBoolean(KEY_DEPOSIT, true))
    val depositEnabled: StateFlow<Boolean> = _depositEnabled.asStateFlow()

    private val _transferEnabled = MutableStateFlow(settings.getBoolean(KEY_TRANSFER, true))
    val transferEnabled: StateFlow<Boolean> = _transferEnabled.asStateFlow()

    fun setDepositEnabled(value: Boolean) { settings.putBoolean(KEY_DEPOSIT, value); _depositEnabled.value = value }
    fun setTransferEnabled(value: Boolean) { settings.putBoolean(KEY_TRANSFER, value); _transferEnabled.value = value }

    private companion object {
        const val KEY_DEPOSIT = "notification.deposit.enabled"
        const val KEY_TRANSFER = "notification.transfer.enabled"
    }
}
```

Koin の shared モジュールに `single { NotificationSettingsPreferences(get()) }` を登録。デフォルトは両方 `true`（OS 側通知許可とは独立、UI 上の意図表現）。

#### プロフィール表示用ヘルパ

Figma `697:8394` のプロフィールカード文字列をそのまま使うため、`commonMain` に薄いヘルパを置く（将来の API 接続時に差し替えやすくするため）:

```kotlin
data class AccountProfile(
    val displayName: String,
    val email: String,
    val avatarLabel: String,        // 「Linked from Google」等のサブ表記
    val socialBadgeLabel: String,    // 「Google」等のバッジ
)

class AccountProfileProvider(private val useDummy: Boolean) {
    fun current(): AccountProfile = if (useDummy) DUMMY else TODO("API 確定時に実装")
    private companion object {
        val DUMMY = AccountProfile(
            displayName = "山田 花子",
            email = "hanako@example.com",
            avatarLabel = "...",
            socialBadgeLabel = "Google",
        )
    }
}
```

`useDummy` は `BuildKonfig.USE_DUMMY_PROFILE` を渡す。Android / iOS とも `AccountProfileProvider.current()` を呼ぶだけで Figma 文字列が返る構造（iOS 側の利用は client-bank-5 で実施）。

### Android 側設計

`composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/account/` 配下に追加:

```
features/account/
├── AccountHubScreen.kt
├── AccountHubViewModel.kt          (AccountProfileProvider を購読)
├── NotificationSettingsScreen.kt
├── NotificationSettingsViewModel.kt (NotificationSettingsPreferences の StateFlow を購読)
├── ComingSoonScreen.kt
└── components/
    ├── ProfileCard.kt
    ├── AccountInfoSection.kt
    └── SettingsRow.kt
```

`RootScaffold` のアカウントタブを `AccountPlaceholderScreen` から `AccountHubScreen` に置き換え、`AccountHubScreen` 内で手動の `when (currentDestination)` スタックで `NotificationSettingsScreen` / `ComingSoonScreen` への遷移を表現する（既存の取引履歴 → 取引詳細と同じパターン）。

### Figma アセット

`697:8394` / `718:7332` から書き出される SVG / PNG（プロフィールアバターのプレースホルダ画像、SNS バッジ、トグルアイコン等）は **`docs/figma-assets/697-8394/` / `docs/figma-assets/718-7332/` に保存** する（プロジェクトメモリの「Figma アセット保存先」ルール準拠）。Android は `composeApp/src/androidMain/res/drawable/` に Vector Drawable として配置する。iOS 用 PDF への変換と `Assets.xcassets` への配置は client-bank-5 で実施。

### 準備中画面の再利用

```kotlin
@Composable
fun ComingSoonScreen(title: String, onBack: () -> Unit) { /* タイトルと戻るボタンだけ受け取り、本文は固定「準備中です」 */ }
```

プライバシー設定タップ時は `title = "プライバシー設定"`、アカウント情報変更タップ時は `title = "アカウント情報変更"` で同じコンポーネントを呼ぶ。

## 実装手順

1. **`:shared` の追加**
   1. `NotificationSettingsPreferences` を `commonMain` に追加
   2. `AccountProfileProvider` と `AccountProfile` を `commonMain` に追加
   3. shared Koin モジュールに `single { NotificationSettingsPreferences(get()) }` / `single { AccountProfileProvider(BuildKonfig.USE_DUMMY_PROFILE) }` を登録
   4. `./gradlew :shared:allTests` でテストが通ることを確認
   5. `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` で iOS 側のリンクも壊れていないことを確認（client-bank-5 で参照する前提のため、ここで担保しておく）
2. **Figma アセット書き出し**
   1. Figma `697:8394` / `718:7332` から必要な SVG / PNG を書き出し、`docs/figma-assets/697-8394/` / `docs/figma-assets/718-7332/` に保存
   2. Android Vector Drawable に変換し `composeApp/src/androidMain/res/drawable/` に配置
3. **共通コンポーネント作成**
   1. `ProfileCard` / `AccountInfoSection` / `SettingsRow` を `features/account/components/` に実装
   2. プレビュー (`@Preview`) で Figma と並べて目視確認
4. **`AccountHubScreen` 実装**
   1. Figma `697:8394` 準拠でレイアウト組み
   2. `AccountHubViewModel` で `AccountProfileProvider.current()` を購読し、`AccountProfile` を UI に渡す
   3. 設定行 3 つのタップで `onNavigatePrivacy` / `onNavigateNotifications` / `onNavigateAccountEdit` を呼ぶ
5. **`NotificationSettingsScreen` 実装**
   1. Figma `718:7332` 準拠でレイアウト組み（着金 / 転送の 2 トグル）
   2. `NotificationSettingsViewModel` で `NotificationSettingsPreferences` の `StateFlow` を購読、トグル操作で `setDepositEnabled` / `setTransferEnabled` を呼ぶ
   3. プロセス再起動後も値が保持されることを実機で確認
6. **`ComingSoonScreen` 実装**
   1. タイトル + 中央「準備中です」+ 戻るボタンの最小構成
7. **`RootScaffold` 配線変更**
   1. アカウントタブ destination を `AccountPlaceholderScreen` → `AccountHubScreen` に置換
   2. `AccountHubScreen` から `NotificationSettingsScreen` / `ComingSoonScreen("プライバシー設定")` / `ComingSoonScreen("アカウント情報変更")` への手動スタック遷移を実装
   3. システム戻るボタン / バックジェスチャの戻り先がハブ画面になることを確認
8. **動作確認**
   1. `./gradlew :composeApp:assembleDebug` 通過
   2. Android 実機 / エミュレータで Figma `697:8394` / `718:7332` と並べて screenshot 比較
   3. 通知設定トグルがアプリ再起動後も保持されることを確認
   4. プライバシー設定 / アカウント情報変更タップ → 準備中画面が表示され戻れることを確認
9. **PR 作成**: `feature/client-bank-4-account-settings-android` → `main`

## 完了条件

- [ ] `./gradlew build` が通る
- [ ] `./gradlew :shared:allTests` が通る
- [ ] `./gradlew :composeApp:assembleDebug` が通る
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が通る（`:shared` 拡張で iOS 側のリンクが壊れていないこと）
- [ ] Android: アカウントタブから `AccountHubScreen` が表示され、Figma `697:8394` と見た目が揃っている
- [ ] Android: 通知設定画面が Figma `718:7332` と見た目が揃っている
- [ ] 着金通知 / 転送通知のトグルがアプリ再起動後も保持される
- [ ] プライバシー設定 / アカウント情報変更タップ → 準備中画面表示 → 戻る、が動く
- [ ] アカウントタブの旧 `AccountPlaceholderScreen` が削除されている（or unused になっている）

## 想定される懸念・リスク

- **プロフィールフィールドが Figma 由来の仮確定**: ベースは Figma `697:8394` の表記をそのまま実装しており、表示名・メール・SNS バッジ等のフィールドは仮確定。実 API 確定時に `AccountProfile` モデルを差し替える前提で、各画面は `AccountProfileProvider.current()` 経由でしかプロフィールを参照しないこと。
- **`NotificationSettingsPreferences` のデフォルト値**: 両方 `true` 始まりにしているが、初回起動時に OS 側通知許可が未付与でも UI 上はオンになる。OS 側許可との整合は本タスクのスコープ外（後続タスクで OS 設定リンクを追加する想定）。
- **`multiplatform-settings` の同時書き込み**: 現状シングルユーザー想定なので競合は考慮不要。マルチアカウント対応時はキー名にアカウント ID を含める設計に変える。
- **準備中画面の戻り先**: ハブ → 準備中 → 戻る、で必ずハブに戻ることを確認（タブ切り替えで状態リセットされないか要確認）。
- **Figma `697:8394` のアバター画像**: 実画像かプレースホルダかを実装時に Figma で再確認。ダミー画像なら `docs/figma-assets/` に保存して client-bank-5 でも使い回す。
- **`:shared` API 凍結**: client-bank-5 で iOS から参照するため、本タスクのマージ後に `NotificationSettingsPreferences` / `AccountProfileProvider` のシグネチャを変更する場合は client-bank-5 側も追従が必要になる。可能な限り本タスク内で API を確定させる。
- **アクセシビリティ**: Compose の `Modifier.semantics` を Task 3 のホーム画面実装と同じレベルで揃えること（client-bank-5 の iOS 側でも同等の `accessibilityLabel` を設定する想定）。

## 参考リンク

- Figma アカウントハブ: https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=697-8394&m=dev
- Figma 通知設定: https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=718-7332&m=dev
- 前提タスク 2 (Android Figma 準拠): [`client-bank-2-home-screen-figma-redesign-android.md`](./client-bank-2-home-screen-figma-redesign-android.md)
- 前提タスク 3 (iOS SwiftUI 統合): [`client-bank-3-ios-multiplatform-integration.md`](./client-bank-3-ios-multiplatform-integration.md)
- 後続タスク 5 (iOS 実装): [`client-bank-5-account-settings-ios.md`](./client-bank-5-account-settings-ios.md)
- 既存 `SignupWelcomePreferences`（`NotificationSettingsPreferences` の参考実装）: `shared/src/commonMain/kotlin/.../SignupWelcomePreferences.kt`
- Android 既存プレースホルダ（差し替え対象）: `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/account/AccountPlaceholderScreen.kt`

---

## Notion タスク登録用サマリ

- **タイトル**: 銀行アプリクライアント：アカウント設定画面（ハブ + 通知設定）Android 実装
- **プレフィックス**: client-bank-4
- **ブランチ命名**: `feature/client-bank-4-account-settings-android`
- **メモ欄に貼る計画書パス**: `docs/tasks/client-bank-4-account-settings-android.md`
- **依存タスク**: client-bank-3 (iOS SwiftUI 統合) 完了
- **後続タスク**: client-bank-5 (iOS 側のアカウント設定実装)
- **PR 構成**: 1 本（Android + 共通 `:shared` 基盤）
