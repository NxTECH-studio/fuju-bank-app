# 銀行アプリクライアント：プライバシー設定画面（仮実装）Android 実装

## 概要

client-bank-4 で導入した準備中画面（`AccountComingSoonScreen("プライバシー設定")`）を本実装画面 `PrivacySettingsScreen` に置換する。トラッキング許諾トグル（アプリ利用状況の収集を許可）、プライバシーポリシー本文のスクロール表示、関連リンク（利用規約 / 特定商取引法表記）を Compose で構成し、トグル値は `multiplatform-settings` でローカル永続化する **仮実装レベル ii**。`:shared` 側に `PrivacyPreferences` を `NotificationSettingsPreferences` と同パターンで追加し、後続 iOS タスクが同じ shared API を再利用できる状態にする。

## 背景・目的

### 経緯

- client-bank-4 でアカウントハブ（Figma `697:8394`）の「設定」セクション 3 行のうち、「プライバシー設定」行のタップ先は `AccountComingSoonScreen("プライバシー設定")` で準備中表示のまま
- 同タスクの「アウトオブスコープ」で「プライバシー設定の本実装」が次タスク以降と明記されていた
- アナリティクス基盤や同意管理プラットフォーム（CMP）はまだ未導入のため、本タスクは「ユーザーの意思表明をローカルに保存する」ところまでで仮実装とする
- プライバシーポリシー本文・利用規約本文は法務確定までダミー（プレースホルダ文）を埋め込み、後続タスクで差し替える

### 目的

- 準備中表示を廃止し、プライバシー設定画面を表示する
- トラッキング許諾トグル（既定値: false = オプトイン方式）を `multiplatform-settings` に永続化する
- プライバシーポリシー本文をアプリ内に埋め込み、スクロール表示できるようにする
- 関連リンク（利用規約 / 特定商取引法表記）は **外部 URL を開くダミー導線** として用意（URL は仮、後続タスクで差し替え）
- shared API（`PrivacyPreferences`）を本タスクで凍結し、client-bank-5 マージ後の iOS 版タスクが同じ API を参照できる状態にする

## スコープ

- **`:shared` 側の追加**
  - `PrivacyPreferences`（commonMain、新規）: `NotificationSettingsPreferences` と同パターン
    - `analyticsOptInEnabled: StateFlow<Boolean>`（既定値: `false`）
    - `setAnalyticsOptInEnabled(value: Boolean)`
  - Koin 登録（`accountModule` に追加 or `privacyModule` 新設、既存パターンに合わせて `accountModule` 集約案）
  - プライバシーポリシー本文 / 関連リンク URL の定数置き場（`PrivacyContent` または `PrivacyTexts` を `commonMain` に置き、Android / iOS で共有）
- **`PrivacySettingsScreen`（Android）**: 既存 `AccountComingSoonScreen("プライバシー設定")` を置換
  - ヘッダー: 戻る `<` + 「プライバシー設定」タイトル
  - セクション 1「データ収集」: トラッキング許諾トグル（タイトル + サブテキスト「アプリ利用状況の収集を許可」相当）
  - セクション 2「プライバシーポリシー」: 白角丸カード内に本文を `verticalScroll` でスクロール表示
  - セクション 3「関連リンク」: 利用規約 / 特定商取引法表記の 2 行リスト、タップで `Intent.ACTION_VIEW` で外部 URL を開く
- **`PrivacySettingsViewModel`（Android）**: `PrivacyPreferences` の StateFlow を購読してトグル状態を公開
- **`RootScaffold` 配線変更**
  - `RootDestination.PrivacySettings` の遷移先を `AccountComingSoonScreen("プライバシー設定")` から `PrivacySettingsScreen` に置換

### アウトオブスコープ

- **iOS 実装**: client-bank-5 (iOS) が `main` にマージされた後、本タスクで凍結された `PrivacyPreferences` を参照する形で別タスク化（仮: `client-bank-11-privacy-settings-ios`）
- **アナリティクス SDK の連動**: トグルの値を実際にアナリティクス送出ガードに使う処理は別タスク（SDK 導入時）。本タスクではトグル値の永続化と StateFlow 配信までで止める
- **同意管理プラットフォーム (CMP) 連携 / GDPR 準拠の包括同意 UI**
- **プライバシーポリシー / 利用規約の確定原稿差し込み**（プレースホルダ文を入れておき、原稿確定タスクで差し替え）
- **アカウント削除 / データダウンロード等の DSAR 機能**
- **アプリ内 WebView でのポリシー表示**（外部ブラウザで開くダミーで OK）

## 着手条件

**client-bank-4 (`client-bank-4-account-settings-android.md`) が `main` にマージ済みであること**。

具体的には:

- `RootScaffold` の `RootDestination.PrivacySettings` 配線が `AccountComingSoonScreen` を表示している
- `:shared` の `accountModule` が存在し、`Settings` シングルトンが Koin に登録されている

`client-bank-7` との並走可否: `:shared/accountModule` を両タスクで触るため、後勝ちになるとマージ衝突が発生する。可能なら **client-bank-7 の `:shared` 変更マージ後** に着手すると安全（純追加のみのため衝突は限定的だが、`accountModule` の `single` 登録順だけは要注意）。

## 影響範囲

- モジュール: `:shared` / `:composeApp`
  - `:shared/commonMain`:
    - `account/PrivacyPreferences.kt` 新規
    - `account/PrivacyContent.kt`（定数置き場）新規
    - `di/accountModule.kt` に `single { PrivacyPreferences(get<Settings>()) }` 追加
  - `:composeApp/androidMain`:
    - `features/account/PrivacySettingsScreen.kt` 新規
    - `features/account/PrivacySettingsViewModel.kt` 新規
    - `features/shell/RootScaffold.kt` 配線変更
- 破壊的変更:
  - `RootDestination.PrivacySettings` の表示画面が置換される
  - `:shared` の Koin に `PrivacyPreferences` が追加される（純追加）
- 追加依存:
  - なし（`multiplatform-settings` 既存）

## 技術アプローチ

### `:shared` 側設計

#### `PrivacyPreferences`

`NotificationSettingsPreferences` と完全に同パターンで `commonMain` に新規追加:

```kotlin
class PrivacyPreferences(private val settings: Settings) {
    private val _analyticsOptInEnabled = MutableStateFlow(
        settings.getBoolean(KEY_ANALYTICS_OPT_IN, false),
    )
    val analyticsOptInEnabled: StateFlow<Boolean> = _analyticsOptInEnabled.asStateFlow()

    fun setAnalyticsOptInEnabled(value: Boolean) {
        settings.putBoolean(KEY_ANALYTICS_OPT_IN, value)
        _analyticsOptInEnabled.value = value
    }

    private companion object {
        // 既定値 false（オプトイン）。GDPR / 改正個人情報保護法準拠の安全側デフォルト。
        const val KEY_ANALYTICS_OPT_IN = "privacy.analytics.optIn.enabled"
    }
}
```

**既定値が `false`** な点が `NotificationSettingsPreferences`（既定 `true`）との違い。データ収集はユーザーの明示同意がない限りオフが安全側のため。

#### `PrivacyContent`

```kotlin
object PrivacyContent {
    /** プライバシーポリシー本文（仮）。法務確定後に差し替え。 */
    const val POLICY_BODY: String = """
        【プライバシーポリシー（仮）】

        本ポリシーは、ふじゅ〜銀行アプリ（以下「本アプリ」）が...
        ...（本文）...
    """

    /** 利用規約 URL（仮）。確定後に差し替え。 */
    const val TERMS_OF_SERVICE_URL: String = "https://example.com/terms"

    /** 特定商取引法表記 URL（仮）。確定後に差し替え。 */
    const val SCT_LAW_URL: String = "https://example.com/sct"
}
```

ポリシー本文は `commonMain` 内に文字列で持つ（Android / iOS 両対応のため、リソースではなく Kotlin 定数）。長文になるので `trimIndent()` を使用。

#### iOS 用に凍結する shared API

後続 iOS タスクで参照する API:

- `PrivacyPreferences` クラスとそのメソッド: `setAnalyticsOptInEnabled(value: Boolean)`
- `PrivacyPreferences.analyticsOptInEnabled: StateFlow<Boolean>`
- `PrivacyContent.POLICY_BODY` / `TERMS_OF_SERVICE_URL` / `SCT_LAW_URL`
- Koin での `PrivacyPreferences` 取得経路（`SharedDI.resolve()`）

iOS 側は `IosStateFlowWrapper(preferences.analyticsOptInEnabled)` で Combine `Publisher` に橋渡しし、SwiftUI `Toggle` の `Binding` に流す（client-bank-5 で確立したパターン踏襲）。

### Android 側設計

#### `PrivacySettingsViewModel`

```kotlin
class PrivacySettingsViewModel(
    private val preferences: PrivacyPreferences,
) : ViewModel() {
    val analyticsOptInEnabled: StateFlow<Boolean> = preferences.analyticsOptInEnabled
    fun setAnalyticsOptInEnabled(value: Boolean) = preferences.setAnalyticsOptInEnabled(value)
}
```

`NotificationSettingsViewModel` と完全に同構造。

#### `PrivacySettingsScreen`

```kotlin
@Composable
fun PrivacySettingsScreen(
    viewModel: PrivacySettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val optIn by viewModel.analyticsOptInEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(modifier.fillMaxSize().background(FujuBankColors.Background)) {
        Header(title = "プライバシー設定", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // セクション 1: データ収集
            AnalyticsOptInCard(checked = optIn, onCheckedChange = viewModel::setAnalyticsOptInEnabled)
            // セクション 2: プライバシーポリシー
            PolicyBodyCard(body = PrivacyContent.POLICY_BODY)
            // セクション 3: 関連リンク
            RelatedLinksCard(
                onTermsClick = { openUrl(context, PrivacyContent.TERMS_OF_SERVICE_URL) },
                onSctClick = { openUrl(context, PrivacyContent.SCT_LAW_URL) },
            )
        }
    }
}

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}
```

`Header` は `AccountComingSoonScreen` のものをコピー or 共通化（既に `NotificationSettingsScreen` でも同等パターンを持っているため、軽い重複は許容。リファクタは別タスク）。

トグルカードのスタイルは `NotificationSettingsScreen.NotificationCard` / `ToggleRow` と完全に揃え、視覚的一貫性を担保する。

ポリシーカードはシンプルに `Text` を白角丸カード内に配置。本文が長いので外側 `Column.verticalScroll` で全体スクロールに任せる（カード内独立スクロールは UX が悪いので避ける）。

関連リンクカードは `SettingsCard` / `SettingsRowSpec`（client-bank-4 で実装済み）を再利用可能か確認。可能なら同じコンポーネントを使ってアカウントハブと視覚的に揃える。

## 実装手順

1. **`:shared` 拡張**
   1. `shared/src/commonMain/kotlin/studio/nxtech/fujubank/account/PrivacyPreferences.kt` 新規
   2. `shared/src/commonMain/kotlin/studio/nxtech/fujubank/account/PrivacyContent.kt` 新規（仮テキスト + 仮 URL）
   3. `shared/src/commonMain/kotlin/studio/nxtech/fujubank/di/accountModule.kt` に `single { PrivacyPreferences(get<Settings>()) }` を追記
   4. `./gradlew :shared:allTests` 通過
   5. `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` 通過
2. **`PrivacySettingsViewModel` 実装**
   1. `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/account/PrivacySettingsViewModel.kt` 新規
   2. `NotificationSettingsViewModel` と同構造で実装
3. **`PrivacySettingsScreen` 実装**
   1. `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/account/PrivacySettingsScreen.kt` 新規
   2. ヘッダー / トラッキング許諾トグルカード / ポリシー本文カード / 関連リンクカードを構築
   3. 関連リンクは `Intent.ACTION_VIEW` で外部ブラウザを開く
   4. `SettingsCard` / `SettingsRowSpec` の再利用可否を確認し、可能なら使う
4. **`RootScaffold` 配線変更**
   1. `RootDestination.PrivacySettings` の表示を `AccountComingSoonScreen("プライバシー設定")` から `PrivacySettingsScreen(viewModel = ...)` に置換
   2. VM 生成は他画面と同じ `viewModelFactory { initializer { ... } }` パターンで `PrivacyPreferences` を Koin から取得
5. **動作確認**
   1. `./gradlew :composeApp:assembleDebug` 通過
   2. アカウントハブから「プライバシー設定」タップ → `PrivacySettingsScreen` 表示
   3. トラッキング許諾トグルがオフで初期表示される（既定値）
   4. トグル操作 → アプリ再起動 → 値が保持される
   5. ポリシー本文がスクロール表示される
   6. 利用規約 / 特定商取引法表記タップで外部ブラウザが起動する（仮 URL）
6. **PR 作成**: `feature/client-bank-8-privacy-settings-android` → `main`

## 完了条件

- [ ] `./gradlew build` が通る
- [ ] `./gradlew :shared:allTests` が通る
- [ ] `./gradlew :composeApp:assembleDebug` が通る
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が通る
- [ ] アカウントハブから「プライバシー設定」タップで `PrivacySettingsScreen` が表示される
- [ ] トラッキング許諾トグルが既定値オフで初期表示される
- [ ] トグル操作後、アプリ再起動でも値が保持される
- [ ] プライバシーポリシー本文（仮）がスクロール表示される
- [ ] 利用規約 / 特定商取引法表記の 2 行をタップで外部ブラウザが開く
- [ ] `:shared` の `PrivacyPreferences` API が iOS から `SharedDI.resolve()` で取得できる（コンパイル可能性まで担保）

## 想定される懸念・リスク

- **オプトイン既定値の確定**: 本タスクでは `false`（明示同意までデータ収集オフ）で実装するが、プロダクト方針として「既定オン + オフ可能」のオプトアウト方式を採る場合は要相談。法務確認後に変更する場合は単に既定値を切り替えるだけで済む構造にしておく
- **プライバシーポリシー本文の保守**: `PrivacyContent.POLICY_BODY` を Kotlin 定数に持つと、本文更新のたびにアプリのリリースが必要。長期的には CMS / リモート JSON に切り出す案もあるが、本タスクでは MVP として埋め込み採用。法務原稿確定タスクで本文だけ差し替える運用前提
- **`accountModule` の責務肥大**: 通知設定 / プロフィール / プライバシーが全て `accountModule` に集約されるため、将来分割する可能性あり。本タスクでは集約方針を維持
- **`PrivacyContent` の URL がダミー**: `https://example.com/...` を入れる。本番リリース前に必ず差し替えチェックを行う（本計画書の「想定される懸念」に明記しておくことで、レビュー時の見落としを防ぐ）
- **iOS API 凍結リスク**: `PrivacyPreferences` のシグネチャを後で変更すると iOS タスクが追従コストを払うことになる。`StateFlow<Boolean>` 公開と `setAnalyticsOptInEnabled(Boolean)` のシグネチャは本タスク内で確定し、以降は変更しない方針
- **トグルがアナリティクスに実連動していないことの可視化不足**: ユーザーは「トグルをオンにした = データ送信される」と誤認するリスクがある。仮実装段階では UI 上の補足注記（「※将来のアプリ更新で有効化されます」等）を入れるか、トグル無効化（disabled 表示）して「準備中」を示すかは実装時に確認。本計画では「機能上は永続化のみ動く」前提で UI に注記文を入れる方向
- **Compose の `Modifier.verticalScroll` ネスト**: 全体 Column に `verticalScroll` をかけ、その中に長文 `Text` を入れるパターンは Compose で正しく動作する。ポリシー本文用カードに別途 `verticalScroll` を入れると無限高制約エラーになるので注意

## 参考リンク

- 前提タスク 4 (Android アカウント設定): [`client-bank-4-account-settings-android.md`](./client-bank-4-account-settings-android.md)
- 前提タスク 5 (iOS アカウント設定): [`client-bank-5-account-settings-ios.md`](./client-bank-5-account-settings-ios.md)
- 既存 `NotificationSettingsPreferences`（パターン参考）: `shared/src/commonMain/kotlin/studio/nxtech/fujubank/account/NotificationSettingsPreferences.kt`
- 既存 Koin モジュール: `shared/src/commonMain/kotlin/studio/nxtech/fujubank/di/accountModule.kt`
- 差し替え対象 Android 画面呼び出し元: `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/shell/RootScaffold.kt`（`RootDestination.PrivacySettings`）
- 既存 `SettingsCard` / `SettingsRowSpec`（再利用候補）: `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/account/components/`

---

## Notion タスク登録用サマリ

- **タイトル**: 銀行アプリクライアント：プライバシー設定画面（仮実装）Android 実装
- **プレフィックス**: client-bank-8
- **ブランチ命名**: `feature/client-bank-8-privacy-settings-android`
- **メモ欄に貼る計画書パス**: `docs/tasks/client-bank-8-privacy-settings-android.md`
- **依存タスク**: client-bank-4 (Android アカウント設定) 完了。client-bank-7 と `:shared/accountModule` を共通変更するため、可能なら client-bank-7 マージ後に着手
- **後続タスク**: iOS 版プライバシー設定（client-bank-5 iOS 完了後に別タスク化、shared `PrivacyPreferences` を再利用）/ プライバシーポリシー原稿確定 + 本文差し替え / アナリティクス SDK 連携
- **PR 構成**: 1 本（Android + 共通 `:shared` 基盤拡張）
