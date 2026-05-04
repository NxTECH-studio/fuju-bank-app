# 銀行アプリクライアント：プライバシー設定画面 Android 実装

## 概要

client-bank-4 で導入した準備中画面（`AccountComingSoonScreen("プライバシー設定")`）を本実装画面 `PrivacySettingsScreen` に置換する。Figma 確定デザイン（[798:12559](https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=798-12559&m=dev)）に従い、以下の 2 セクション構成で実装する:

1. **トラッキング**: 「アプリのトラッキングを許可」トグル + サブ説明（既定値 `false`、`multiplatform-settings` でローカル永続化）
2. **法的情報**: 「プライバシーポリシー」「利用規約」の 2 行リスト（タップで外部ブラウザを開く）

`:shared` 側に `PrivacyPreferences` を `NotificationSettingsPreferences` と同パターンで追加し、`PrivacyContent`（URL 定数）を `commonMain` に置く。後続 iOS タスクおよびアカウントハブ「情報」セクション（後述）が同じ shared API を再利用できる状態にする。

## 背景・目的

### 経緯

- client-bank-4 でアカウントハブの「設定」セクション 3 行のうち、「プライバシー設定」行のタップ先は `AccountComingSoonScreen("プライバシー設定")` で準備中表示のまま
- 同タスクの「アウトオブスコープ」で「プライバシー設定の本実装」が次タスク以降と明記されていた
- アナリティクス基盤や同意管理プラットフォーム（CMP）はまだ未導入だが、UI は Figma で確定したため、画面実装としては本実装に位置付ける（トグルの実 SDK 連動は別タスク）
- 法務確定までプライバシーポリシー本文・利用規約本文は確定していないが、Figma で「法的情報」セクションの遷移先動線が **外部ブラウザ（外部 URL を開く）** であることを前提に、URL 定数（仮）を持って導線だけ完成させる

### 目的

- 準備中表示を廃止し、`PrivacySettingsScreen` を表示する
- トラッキング許諾トグル（既定値: `false` = オプトイン方式）を `multiplatform-settings` に永続化する
- 「法的情報」セクションから外部ブラウザでプライバシーポリシー / 利用規約 URL を開ける状態にする（URL は仮、後続タスクで確定原稿の URL に差し替え）
- shared API（`PrivacyPreferences` / `PrivacyContent`）を本タスクで凍結し、後続の iOS 版タスクおよびアカウントハブ刷新タスク（「情報」セクション、後述）が同じ API を参照できる状態にする

### Figma 確定デザインの主な仕様

[798:12559](https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=798-12559&m=dev) より（参考スクリーンショット: [`docs/figma-assets/bank-redesign-798-12559.png`](../figma-assets/bank-redesign-798-12559.png)）:

- ヘッダー: 戻る `<`（48dp 円形タップ領域）+ 中央タイトル「プライバシー設定」（17sp Bold）
- セクション見出しは 12sp Bold、白カード外に左寄せ
- 白カード: `rounded-[32px]`、`px:18 / py:20`、`gap:16`（カード間は外側 `gap:8`）
- セクション 1「トラッキング」:
  - 単一カード内に `アプリのトラッキングを許可`（14sp SemiBold）+ サブテキスト `利用状況の分析と改善に使用されます`（12sp Regular、`#8E8E93`）+ 右端トグル（44×24dp）
- セクション 2「法的情報」:
  - リストカード内に `プライバシーポリシー` / `利用規約` の 2 行、各行右端に chevron `>`（16dp）
  - 行間に `Line2`（薄い divider）

## スコープ

- **`:shared` 側の追加**
  - `PrivacyPreferences`（commonMain、新規）: `NotificationSettingsPreferences` と同パターン
    - `analyticsOptInEnabled: StateFlow<Boolean>`（既定値: `false`）
    - `setAnalyticsOptInEnabled(value: Boolean)`
  - `PrivacyContent`（commonMain、新規）: プライバシーポリシー / 利用規約の URL 定数
  - Koin 登録（既存パターンに合わせて `accountModule` に集約）
- **`PrivacySettingsScreen`（Android）**: 既存 `AccountComingSoonScreen("プライバシー設定")` を置換
  - ヘッダー: 戻る `<` + 「プライバシー設定」タイトル
  - セクション 1「トラッキング」: トラッキング許諾トグルカード（タイトル + サブテキスト + 右端トグル）
  - セクション 2「法的情報」: 「プライバシーポリシー」「利用規約」の 2 行リストカード、各行タップで `Intent.ACTION_VIEW` 外部ブラウザ起動
- **`PrivacySettingsViewModel`（Android）**: `PrivacyPreferences` の StateFlow を購読してトグル状態を公開
- **`RootScaffold` 配線変更**
  - `RootDestination.PrivacySettings` の遷移先を `AccountComingSoonScreen("プライバシー設定")` から `PrivacySettingsScreen` に置換

### アウトオブスコープ

- **iOS 実装**: 本タスクで凍結された `PrivacyPreferences` / `PrivacyContent` を参照する形で [`client-bank-9-privacy-settings-ios`](./client-bank-9-privacy-settings-ios.md) にて別タスク化（client-bank-5 マージ済みのため即着手可）
- **アナリティクス SDK の連動**: トグルの値を実際にアナリティクス送出ガードに使う処理は別タスク（SDK 導入時）。本タスクではトグル値の永続化と StateFlow 配信までで止める
- **同意管理プラットフォーム (CMP) 連携 / GDPR 準拠の包括同意 UI**
- **プライバシーポリシー / 利用規約の確定原稿差し込み**: 仮 URL（`https://example.com/...`）を入れておき、原稿確定タスクで定数を差し替え
- **アカウント削除 / データダウンロード等の DSAR 機能**
- **アプリ内 WebView でのポリシー表示**: Figma デザイン通り外部ブラウザで開く方針
- **アカウントハブ刷新**（[697:8394](https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=697-8394&m=dev)、後述）の本実装は別タスク。本タスクでは shared `PrivacyContent` を凍結する範囲のみ責務を持つ
- **「パスワード変更」行の本実装**: 新アカウントハブで「設定」セクションに追加されているが別タスク（仮: `client-bank-10-password-change-android`）
- **「特定商取引法表記」**: 確定デザインに含まれないため対象外

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
    - `account/PrivacyContent.kt`（URL 定数置き場）新規
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
    /** プライバシーポリシー URL（仮）。法務確定後に差し替え。 */
    const val PRIVACY_POLICY_URL: String = "https://example.com/privacy-policy"

    /** 利用規約 URL（仮）。法務確定後に差し替え。 */
    const val TERMS_OF_SERVICE_URL: String = "https://example.com/terms-of-service"
}
```

ポリシー本文をアプリに埋め込まず、外部ブラウザで開く方針（Figma 確定デザイン準拠）。本文の保守はアプリリリースから切り離せるメリットあり。

#### iOS 用 / アカウントハブ刷新タスク用に凍結する shared API

後続タスクで参照する API:

- `PrivacyPreferences` クラスとそのメソッド: `setAnalyticsOptInEnabled(value: Boolean)`
- `PrivacyPreferences.analyticsOptInEnabled: StateFlow<Boolean>`
- `PrivacyContent.PRIVACY_POLICY_URL` / `TERMS_OF_SERVICE_URL`
- Koin での `PrivacyPreferences` 取得経路（`SharedDI.resolve()`）

iOS 側は `IosStateFlowWrapper(preferences.analyticsOptInEnabled)` で Combine `Publisher` に橋渡しし、SwiftUI `Toggle` の `Binding` に流す（client-bank-5 で確立したパターン踏襲）。

アカウントハブ刷新タスクの「情報」セクションは、本タスクで凍結した `PrivacyContent.PRIVACY_POLICY_URL` / `TERMS_OF_SERVICE_URL` をそのまま参照する。

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
            // セクション 1: トラッキング
            TrackingOptInSection(
                checked = optIn,
                onCheckedChange = viewModel::setAnalyticsOptInEnabled,
            )
            // セクション 2: 法的情報
            LegalLinksSection(
                onPrivacyPolicyClick = { openUrl(context, PrivacyContent.PRIVACY_POLICY_URL) },
                onTermsOfServiceClick = { openUrl(context, PrivacyContent.TERMS_OF_SERVICE_URL) },
            )
        }
    }
}

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}
```

- **セクション見出し**: 12sp Bold、`px:8 py:8`、白カード外に左寄せ（Figma 準拠）
- **トラッキングセクション**: `NotificationSettingsScreen` の `NotificationCard` / `ToggleRow` 相当を再利用 or 同パターンで書く。タイトル 14sp SemiBold + サブテキスト 12sp `#8E8E93`
- **法的情報セクション**: `SettingsCard` / `SettingsRowSpec`（client-bank-4 で実装済み）の chevron `>` 行リストパターンを再利用可能か確認。可能なら同じコンポーネントを使ってアカウントハブと視覚的に揃える
- カードは `rounded-[32px]`、`px:18 py:20`、`gap:16`（Figma 準拠）

`Header` は `AccountComingSoonScreen` のものをコピー or 共通化（既に `NotificationSettingsScreen` でも同等パターンを持っているため、軽い重複は許容。リファクタは別タスク）。

## 実装手順

1. **`:shared` 拡張**
   1. `shared/src/commonMain/kotlin/studio/nxtech/fujubank/account/PrivacyPreferences.kt` 新規
   2. `shared/src/commonMain/kotlin/studio/nxtech/fujubank/account/PrivacyContent.kt` 新規（仮 URL 2 件）
   3. `shared/src/commonMain/kotlin/studio/nxtech/fujubank/di/accountModule.kt` に `single { PrivacyPreferences(get<Settings>()) }` を追記
   4. `./gradlew :shared:allTests` 通過
   5. `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` 通過
2. **`PrivacySettingsViewModel` 実装**
   1. `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/account/PrivacySettingsViewModel.kt` 新規
   2. `NotificationSettingsViewModel` と同構造で実装
3. **`PrivacySettingsScreen` 実装**
   1. `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/account/PrivacySettingsScreen.kt` 新規
   2. ヘッダー / トラッキングセクション / 法的情報セクションを構築
   3. `SettingsCard` / `SettingsRowSpec` の再利用可否を確認し、可能なら使う
   4. 法的情報の各行タップで `Intent.ACTION_VIEW` 外部ブラウザ起動
4. **`RootScaffold` 配線変更**
   1. `RootDestination.PrivacySettings` の表示を `AccountComingSoonScreen("プライバシー設定")` から `PrivacySettingsScreen(viewModel = ...)` に置換
   2. VM 生成は他画面と同じ `viewModelFactory { initializer { ... } }` パターンで `PrivacyPreferences` を Koin から取得
5. **動作確認**
   1. `./gradlew :composeApp:assembleDebug` 通過
   2. アカウントハブから「プライバシー設定」タップ → `PrivacySettingsScreen` 表示
   3. トラッキング許諾トグルがオフで初期表示される（既定値）
   4. トグル操作 → アプリ再起動 → 値が保持される
   5. 「プライバシーポリシー」タップで外部ブラウザが起動する（仮 URL）
   6. 「利用規約」タップで外部ブラウザが起動する（仮 URL）
6. **PR 作成**: `feature/client-bank-8-privacy-settings-android` → `main`

## 完了条件

- [ ] `./gradlew build` が通る
- [ ] `./gradlew :shared:allTests` が通る
- [ ] `./gradlew :composeApp:assembleDebug` が通る
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が通る
- [ ] アカウントハブから「プライバシー設定」タップで `PrivacySettingsScreen` が表示される
- [ ] トラッキング許諾トグルが既定値オフで初期表示される
- [ ] トグル操作後、アプリ再起動でも値が保持される
- [ ] 「プライバシーポリシー」「利用規約」の 2 行をタップで外部ブラウザが開く
- [ ] `:shared` の `PrivacyPreferences` / `PrivacyContent` API が iOS から `SharedDI.resolve()` で取得できる（コンパイル可能性まで担保）

## アカウントハブ刷新（参考、本タスク対象外）

[697:8394](https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=697-8394&m=dev) でアカウントハブの構成が更新されている。本タスクのスコープ外だが、shared API 設計に影響するため記録する:

- **「設定」セクション**: 通知 / プライバシー設定 / **パスワード変更（新規）** の 3 行
- **「情報」セクション（新規）**: プライバシーポリシー / 利用規約 の 2 行（外部ブラウザで開く想定）
- **プロフィールカード**: アバター + 名前 + ID 表示の刷新

→ 「情報」セクションは本タスクで定義する `PrivacyContent.PRIVACY_POLICY_URL` / `TERMS_OF_SERVICE_URL` を参照する前提。本タスクの shared API 命名・公開範囲は、アカウントハブ刷新タスク側からも自然に再利用できることを意識する。

→ アカウントハブ刷新の本実装、「パスワード変更」行の本実装はそれぞれ別タスクで扱う。

## 想定される懸念・リスク

- **オプトイン既定値の確定**: 本タスクでは `false`（明示同意までデータ収集オフ）で実装するが、プロダクト方針として「既定オン + オフ可能」のオプトアウト方式を採る場合は要相談。法務確認後に変更する場合は単に既定値を切り替えるだけで済む構造にしておく
- **`accountModule` の責務肥大**: 通知設定 / プロフィール / プライバシーが全て `accountModule` に集約されるため、将来分割する可能性あり。本タスクでは集約方針を維持
- **`PrivacyContent` の URL がダミー**: `https://example.com/...` を入れる。本番リリース前に必ず差し替えチェックを行う（本計画書の「想定される懸念」に明記しておくことで、レビュー時の見落としを防ぐ）
- **shared API 凍結リスク**: `PrivacyPreferences` のシグネチャを後で変更すると iOS タスクおよびアカウントハブ刷新タスクが追従コストを払うことになる。`StateFlow<Boolean>` 公開と `setAnalyticsOptInEnabled(Boolean)` のシグネチャ、および `PrivacyContent` の 2 定数は本タスク内で確定し、以降は変更しない方針
- **トグルがアナリティクスに実連動していないことの可視化不足**: ユーザーは「トグルをオンにした = データ送信される」と誤認するリスクがある。Figma デザインのコピー文言（「アプリのトラッキングを許可」「利用状況の分析と改善に使用されます」）は実機能準拠の表現になっているため、SDK 連動が入るまではトグル値が StateFlow としては流れているが「実 SDK は無い」状態を README ないし内部ドキュメントで明記する
- **外部ブラウザ起動時の `ActivityNotFoundException`**: ブラウザ未インストール環境（Android Auto 等の特殊環境）では `Intent.ACTION_VIEW` が解決失敗する可能性。本タスクでは標準 Android 端末前提で例外ハンドリングは行わない（クラッシュさせない最低限の `runCatching` は付ける方向）

## 参考リンク

- Figma 確定デザイン（プライバシー設定画面）: [798:12559](https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=798-12559&m=dev)
- Figma 参考スクリーンショット: [`docs/figma-assets/bank-redesign-798-12559.png`](../figma-assets/bank-redesign-798-12559.png)
- Figma 確定デザイン（アカウントハブ刷新、参考）: [697:8394](https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=697-8394&m=dev)
- 前提タスク 4 (Android アカウント設定): [`client-bank-4-account-settings-android.md`](./client-bank-4-account-settings-android.md)
- 前提タスク 5 (iOS アカウント設定): [`client-bank-5-account-settings-ios.md`](./client-bank-5-account-settings-ios.md)
- 既存 `NotificationSettingsPreferences`（パターン参考）: `shared/src/commonMain/kotlin/studio/nxtech/fujubank/account/NotificationSettingsPreferences.kt`
- 既存 Koin モジュール: `shared/src/commonMain/kotlin/studio/nxtech/fujubank/di/accountModule.kt`
- 差し替え対象 Android 画面呼び出し元: `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/shell/RootScaffold.kt`（`RootDestination.PrivacySettings`）
- 既存 `SettingsCard` / `SettingsRowSpec`（再利用候補）: `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/account/components/`

---

## Notion タスク登録用サマリ

- **タイトル**: 銀行アプリクライアント：プライバシー設定画面 Android 実装
- **プレフィックス**: client-bank-8
- **ブランチ命名**: `feature/client-bank-8-privacy-settings-android`
- **メモ欄に貼る計画書パス**: `docs/tasks/client-bank-8-privacy-settings-android.md`
- **依存タスク**: client-bank-4 (Android アカウント設定) 完了。client-bank-7 と `:shared/accountModule` を共通変更するため、可能なら client-bank-7 マージ後に着手
- **後続タスク**: [`client-bank-9-privacy-settings-ios`](./client-bank-9-privacy-settings-ios.md)（shared `PrivacyPreferences` / `PrivacyContent` を再利用）/ プライバシーポリシー・利用規約原稿確定 + URL 差し替え / アナリティクス SDK 連携 / アカウントハブ刷新（697:8394 本実装、別タスク）/ パスワード変更行の本実装（別タスク）
- **PR 構成**: 1 本（Android + 共通 `:shared` 基盤拡張）
- **参考 Figma**: [プライバシー設定 798:12559](https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=798-12559&m=dev) / [アカウントハブ刷新 697:8394](https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=697-8394&m=dev)
