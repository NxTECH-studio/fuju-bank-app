# 銀行アプリクライアント：プライバシー設定画面 iOS 実装

## 概要

client-bank-8 で Android 側に実装した **プライバシー設定画面**（Figma [798:12559](https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=798-12559&m=dev)、両プラットフォーム共用デザイン）を iOS (SwiftUI) に移植する。`:shared` 側の `PrivacyPreferences` / `PrivacyContent` は client-bank-8 で導入済みのため、本タスクでは iOS 画面の実装、`AccountIos.kt` / `KoinIos.kt` への薄いブリッジ追加、`RootTabView` の `AccountDestination.privacy` 配線変更を行う。

## 背景・目的

### 経緯

- client-bank-8 (`client-bank-8-privacy-settings-android.md`) で Android 側のプライバシー設定画面、および両プラットフォーム共通の `:shared` 基盤（`PrivacyPreferences`、`PrivacyContent`）が完了している
- iOS 側のアカウントタブは現状 `AccountDestination.privacy` → `AccountComingSoonView(title: "プライバシー設定")` のままであり、Android と iOS で挙動がずれている
- KMP プロジェクトとして「両プラットフォーム同等の体験を提供する」という方針（メモリ: KMP は iOS/Android 両対応必須）に沿い、iOS 側にも同じ画面を載せる必要がある
- Figma 確定デザイン [798:12559](https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=798-12559&m=dev) は iOS chrome（status bar / home indicator）のモックを含むため、iOS でほぼそのままの寸法で実装できる

### 目的

- `AccountDestination.privacy` の遷移先を `AccountComingSoonView` から `PrivacySettingsView` に置換
- Android で確立したパターン（`SharedDI.resolve()` 相当でグラフから取得 / `IosStateFlowWrapper`（本プロジェクトでは `observeFlow` ベースの `FlowToken` 方式）で `StateFlow` を `@Published` に橋渡し）を踏襲し、Android との挙動差を最小化
- トラッキング許諾トグルの永続化値を Android と同じキー（`privacy.analytics.optIn.enabled`、既定値 `false`）で共有し、ユーザーがプラットフォームをまたいでも一貫した状態を保つ
- 「法的情報」セクションの 2 行（プライバシーポリシー / 利用規約）タップで OS の標準ブラウザ（Safari）を開く

### Figma 確定デザインの主な仕様

[798:12559](https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=798-12559&m=dev) より（参考スクリーンショット: [`docs/figma-assets/bank-redesign-798-12559.png`](../figma-assets/bank-redesign-798-12559.png)）:

- ヘッダー: 戻る `<`（48pt 円形タップ領域）+ 中央タイトル「プライバシー設定」（17pt Bold）
- セクション見出しは 12pt Bold、白カード外に左寄せ
- 白カード: `cornerRadius:32`、`px:18 / py:20`、`spacing:16`（カード間は外側 `spacing:8`）
- セクション 1「トラッキング」:
  - 単一カード: `アプリのトラッキングを許可`（14pt SemiBold）+ サブテキスト `利用状況の分析と改善に使用されます`（12pt Regular、`#8E8E93`）+ 右端 SwiftUI `Toggle`（システムスタイル）
- セクション 2「法的情報」:
  - リストカード: `プライバシーポリシー` / `利用規約` の 2 行、各行右端に SF Symbol `chevron.right`（16pt）
  - 行間に divider

## スコープ

- **`:shared` 側の追加（最小）**
  - `shared/src/iosMain/.../account/AccountIos.kt`（or 同階層の新規 `PrivacyIos.kt`）に `observeAnalyticsOptInEnabled(preferences: PrivacyPreferences, onChange: (Boolean) -> Unit): FlowToken` を追加（既存 `observeDepositEnabled` / `observeTransferEnabled` と同パターン）
  - `shared/src/iosMain/.../di/KoinIos.kt` に `privacyPreferences(): PrivacyPreferences = KoinPlatform.getKoin().get()` を追加
- **iOS 画面**
  - `PrivacySettingsView`（新規、SwiftUI）: ヘッダー / トラッキングセクション / 法的情報セクションを描画
  - `ObservablePrivacySettingsViewModel`（新規）: `PrivacyPreferences` を購読し `@Published var analyticsOptInEnabled: Bool` で公開、書き戻しメソッド `setAnalyticsOptInEnabled(_:)`
  - 既存 `SettingsRowView`（client-bank-5 で実装済み）の chevron 行を再利用してセクション 2 を構築
- **タブ配線**
  - `RootTabView` の `case .privacy` を `AccountComingSoonView(title: "プライバシー設定")` から `PrivacySettingsView()` に差し替え
- **外部 URL 起動**
  - 法的情報の各行タップで `UIApplication.shared.open(URL(string: PrivacyContent.shared.privacyPolicyURL)!)` 等を呼び、Safari で開く
  - もしくは SwiftUI `Link` を使うが、行全体タップ領域・SettingsRowView のスタイル維持のためボタンアクション内で `openURL` 環境値（`@Environment(\.openURL)`）を呼ぶ方が一貫する

### アウトオブスコープ

- **Android 側の追加実装**（client-bank-8 で完了済み）
- **アナリティクス SDK の連動**: トグル値を実際にアナリティクス送出ガードに使う処理は別タスク
- **同意管理プラットフォーム (CMP) 連携 / GDPR 準拠の包括同意 UI**
- **プライバシーポリシー / 利用規約の確定原稿差し込み**: `PrivacyContent` の URL を後続タスクで差し替え。本タスクでは仮 URL のまま導線が動けば OK
- **アプリ内 WebView でのポリシー表示**: Figma 準拠で外部ブラウザで開く方針（Android と同じ）
- **アカウントハブ刷新**（[697:8394](https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=697-8394&m=dev) の「情報」セクション追加など）は別タスク
- **iOS の OS トラッキング許可ダイアログ（ATT, App Tracking Transparency）連携**: 本タスクではアプリ内のトグル状態の永続化のみ。`AppTrackingTransparency.framework` の `ATTrackingManager.requestTrackingAuthorization` 連動は SDK 連動と合わせて別タスク化

## 着手条件

**client-bank-8 (`client-bank-8-privacy-settings-android.md`) が `main` にマージ済みであること**。

具体的には:

- `:shared/commonMain` に `PrivacyPreferences` / `PrivacyContent` が追加され、Koin に登録されている
- `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が通る状態
- Android 側で `PrivacyPreferences` の永続化キー / 既定値 / 公開シグネチャが確定している（本タスクは凍結された API を再利用する）

## 影響範囲

- モジュール: `iosApp` / `:shared`（iosMain のみ）
  - `iosApp/iosApp/Features/Account/PrivacySettingsView.swift` 新規
  - `iosApp/iosApp/Features/Account/ObservablePrivacySettingsViewModel.swift` 新規
  - `iosApp/iosApp/Features/Shell/RootTabView.swift` の `case .privacy` 配線変更
  - `shared/src/iosMain/kotlin/studio/nxtech/fujubank/account/AccountIos.kt` に `observeAnalyticsOptInEnabled` 追加（or 新規 `PrivacyIos.kt`）
  - `shared/src/iosMain/kotlin/studio/nxtech/fujubank/di/KoinIos.kt` に `privacyPreferences()` 追加
- 破壊的変更:
  - `AccountDestination.privacy` の表示画面が置換される（旧 `AccountComingSoonView` は他 `case` でも使われているのでファイル自体は残す）
- 追加依存:
  - なし

## 技術アプローチ

### Android で確立したパターンを踏襲

client-bank-8 の Android 実装で以下が確定:

- `PrivacyPreferences.analyticsOptInEnabled: StateFlow<Boolean>` を購読してトグル UI に流す
- `PrivacyPreferences.setAnalyticsOptInEnabled(value: Boolean)` で書き戻す
- `PrivacyContent.PRIVACY_POLICY_URL` / `PrivacyContent.TERMS_OF_SERVICE_URL` で外部 URL を取得

iOS 側もこの構造に揃える。`NotificationSettingsPreferences` の iOS ブリッジ・購読パターン（`AccountIos.kt` の `observeDepositEnabled` ＋ `ObservableNotificationSettingsViewModel`）と完全に同型で実装する。

### `:shared/iosMain` ブリッジ追加

```kotlin
// shared/src/iosMain/kotlin/studio/nxtech/fujubank/account/AccountIos.kt （or PrivacyIos.kt 新規）

/**
 * Swift 側から `PrivacyPreferences.analyticsOptInEnabled` を観測するための薄い API。
 * `observeDepositEnabled` と同じスタイルで、初期値も subscribe 直後に 1 回 emit される。
 */
fun observeAnalyticsOptInEnabled(
    preferences: PrivacyPreferences,
    onChange: (Boolean) -> Unit,
): FlowToken = observeFlow(preferences.analyticsOptInEnabled) { value -> onChange(value) }
```

```kotlin
// shared/src/iosMain/kotlin/studio/nxtech/fujubank/di/KoinIos.kt 末尾に追加
fun privacyPreferences(): PrivacyPreferences = KoinPlatform.getKoin().get()
```

ファイル分割の方針: 既存 `AccountIos.kt` に privacy 系も同居させる案と、新規 `PrivacyIos.kt` に分離する案がある。プロジェクトの既存粒度（`AccountIos.kt` は notification/account 系をまとめている）に合わせて **`AccountIos.kt` に同居** する。後にファイル肥大化したら分割する。

### iOS 側設計

`iosApp/iosApp/Features/Account/` 配下に追加:

```
Features/Account/
├── (既存) AccountHubView.swift / NotificationSettingsView.swift / AccountComingSoonView.swift / ...
├── PrivacySettingsView.swift                       ← 新規
├── ObservablePrivacySettingsViewModel.swift        ← 新規
└── Components/
    └── (既存) SettingsRowView.swift                ← 法的情報セクションの 2 行で再利用
```

#### `ObservablePrivacySettingsViewModel`

```swift
@MainActor
final class ObservablePrivacySettingsViewModel: ObservableObject {
    @Published private(set) var analyticsOptInEnabled: Bool

    private let preferences: PrivacyPreferences
    private var optInToken: FlowToken?

    init() {
        let prefs = KoinIosKt.privacyPreferences()
        self.preferences = prefs
        // observe は subscribe 直後に現在値を 1 回 emit するので、初期値は仮 false で OK。
        self.analyticsOptInEnabled = false

        optInToken = AccountIosKt.observeAnalyticsOptInEnabled(preferences: prefs) { [weak self] value in
            let on = value.boolValue
            Task { @MainActor in
                self?.analyticsOptInEnabled = on
            }
        }
    }

    deinit {
        optInToken?.close()
    }

    func setAnalyticsOptInEnabled(_ value: Bool) {
        preferences.setAnalyticsOptInEnabled(value: value)
    }
}
```

`ObservableNotificationSettingsViewModel` と完全に同パターン。SwiftUI の `Toggle` は `Binding<Bool>` を要求するため、画面側では `Binding(get: { vm.analyticsOptInEnabled }, set: { vm.setAnalyticsOptInEnabled($0) })` の形で書き戻し API を経由する（`@Published` の直接バインドは shared への永続化を伴わないので避ける）。

#### `PrivacySettingsView`

```swift
struct PrivacySettingsView: View {
    @StateObject private var viewModel = ObservablePrivacySettingsViewModel()
    @Environment(\.openURL) private var openURL

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                // セクション 1: トラッキング
                section(title: "トラッキング") {
                    TrackingOptInCard(
                        isOn: Binding(
                            get: { viewModel.analyticsOptInEnabled },
                            set: { viewModel.setAnalyticsOptInEnabled($0) },
                        ),
                    )
                }
                // セクション 2: 法的情報
                section(title: "法的情報") {
                    LegalLinksCard(
                        onPrivacyPolicy: { openURL(URL(string: PrivacyContent.shared.privacyPolicyURL)!) },
                        onTermsOfService: { openURL(URL(string: PrivacyContent.shared.termsOfServiceURL)!) },
                    )
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 10)
        }
        .background(FujuBankPalette.background)
        .navigationTitle("プライバシー設定")
        .navigationBarTitleDisplayMode(.inline)
    }
}
```

- セクション見出し（12pt Bold）、白カード（`cornerRadius:32`、`px:18 py:20`）、カード間 `spacing:8` を Figma 準拠で実装
- ナビゲーションタイトルは `RootTabView` の `NavigationStack` から自動的に戻るボタン付きで描画される（`NotificationSettingsView` と同じ）
- 法的情報の 2 行は既存 `SettingsRowView` を再利用（`chevron.right` 表示があるバリアントがあれば流用、なければ Figma 通りに新規 `LegalLinkRowView` を `SettingsRowView` パターンで作る）

**`PrivacyContent` の Swift 側参照**: Kotlin `object PrivacyContent` 内の `const val` は Swift から `PrivacyContent.shared.privacyPolicyURL` / `PrivacyContent.shared.termsOfServiceURL` のようにアクセスできる（Kotlin/Native の Obj-C 名前マングリングで `PRIVACY_POLICY_URL` → `privacyPolicyURL`）。実機で名前が想定通り出ているかをビルド後に確認する。揺れがあれば `KoinIos.kt` の隣に `privacyPolicyUrl(): String = PrivacyContent.PRIVACY_POLICY_URL` のような薄い関数を追加するフォールバックを準備しておく。

#### `RootTabView` 配線変更

```swift
.navigationDestination(for: AccountDestination.self) { dest in
    switch dest {
    case .notifications:
        NotificationSettingsView(
            onNotificationTap: { toast.send("通知機能は実装中です") },
        )
    case .privacy:
        PrivacySettingsView()        // ← 旧: AccountComingSoonView(title: "プライバシー設定")
    case .accountEdit:
        AccountComingSoonView(title: "アカウント情報変更")
    }
}
```

`AccountComingSoonView` は `.accountEdit` でまだ使われているので削除しない。

### 外部ブラウザ起動

SwiftUI 環境値 `@Environment(\.openURL)` を使う。`Link("プライバシーポリシー", destination: ...)` でも実現できるが、`SettingsRowView` の見た目（`chevron.right` 含む）を維持するためボタン押下アクション内で `openURL` を呼ぶ。

仮 URL（`https://example.com/...`）でも Safari は開けるが「ページが見つかりません」になる。これは想定挙動（後続タスクで本物の URL に差し替え）。

## 実装手順

1. **`:shared/iosMain` ブリッジ追加**
   1. `shared/src/iosMain/kotlin/studio/nxtech/fujubank/account/AccountIos.kt` に `observeAnalyticsOptInEnabled` を追加
   2. `shared/src/iosMain/kotlin/studio/nxtech/fujubank/di/KoinIos.kt` に `privacyPreferences()` を追加
   3. `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` 通過
2. **`ObservablePrivacySettingsViewModel` 実装**
   1. `iosApp/iosApp/Features/Account/ObservablePrivacySettingsViewModel.swift` 新規
   2. `ObservableNotificationSettingsViewModel` と同構造で実装、`FlowToken` を `deinit` で close
3. **`PrivacySettingsView` 実装**
   1. `iosApp/iosApp/Features/Account/PrivacySettingsView.swift` 新規
   2. ヘッダーは `NavigationStack` の `navigationTitle` 任せ
   3. トラッキングセクション（タイトルカード + サブ + Toggle）を Figma 寸法（`cornerRadius:32 / px:18 / py:20 / spacing:16`）で組む
   4. 法的情報セクション（2 行、各行 chevron.right、divider）を組む。可能なら既存 `SettingsRowView` を流用
   5. 各行タップで `@Environment(\.openURL)` 経由で `PrivacyContent.shared.*URL` を Safari で開く
4. **`RootTabView` 配線変更**
   1. `case .privacy` を `PrivacySettingsView()` に差し替え
   2. ハブ → プライバシー設定 → 戻る、が `NavigationStack` の標準遷移で動作することを確認
5. **動作確認**
   1. `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` 通過
   2. Xcode で iOS Simulator (Arm64) ビルド成功
   3. Simulator で Figma `798:12559` と並べて screenshot 比較
   4. トラッキングトグルが既定値オフで初期表示される
   5. トグル操作 → アプリ再起動 → 値が保持される
   6. Android（client-bank-8 マージ済み）でトグルを操作 → iOS 側で同じ既定キーの値が読めることを確認（同じ `multiplatform-settings` キー `privacy.analytics.optIn.enabled` を使う）
   7. 「プライバシーポリシー」「利用規約」タップで Safari が起動する（仮 URL で OK）
6. **PR 作成**: `feature/client-bank-9-privacy-settings-ios` → `main`

## 完了条件

- [ ] `./gradlew build` が通る
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が通る
- [ ] Xcode で iOS Simulator (Arm64) ビルドが通り、アプリが起動する（目視確認）
- [ ] iOS: アカウントハブ → 「プライバシー設定」タップで `PrivacySettingsView` が表示される
- [ ] iOS: 画面が Figma `798:12559` と見た目が揃っている（セクション見出し / 白カード `cornerRadius:32` / トグル / chevron 等）
- [ ] トラッキング許諾トグルが既定値オフで初期表示される
- [ ] トグル操作後、アプリ再起動でも値が保持される
- [ ] Android（client-bank-8 マージ済み）と iOS で、トグル状態の永続化キーが共有されている（プラットフォーム間で値が一貫）
- [ ] 「プライバシーポリシー」「利用規約」タップで Safari が起動する
- [ ] `RootTabView` の `case .privacy` が `AccountComingSoonView` から `PrivacySettingsView` に差し替わっている

## 想定される懸念・リスク

- **`PrivacyContent.shared.*` の Swift 側名前マングリング**: Kotlin の `const val PRIVACY_POLICY_URL` が Swift から `privacyPolicyURL` で見える前提だが、実プロジェクトでの名前マングリングは生成 framework に依存する。Xcode で auto-complete を確認し、想定と違えば（a）`KoinIos.kt` に薄い getter 関数を追加するか、（b）名前を Kotlin 側で `@Suppress` 込みでリネームするか、本タスク内で解決
- **`FlowToken` のライフサイクル**: `ObservableNotificationSettingsViewModel` と同様に `deinit` で `close()` しないとリークする。`@StateObject` の所有権切れと `deinit` 呼び出しタイミングに注意
- **`Toggle` の `Binding` 経由書き戻し**: `@Published` を直接 `Toggle($vm.analyticsOptInEnabled)` で渡すと `:shared` への永続化がされない。必ず `Binding(get:set:)` で `setAnalyticsOptInEnabled` を呼ぶ形にする（既存 `NotificationSettingsView` と同じ）
- **外部 URL の `URL(string:)` 強制 unwrap**: 仮 URL は `https://example.com/...` で nil にはならないが、後で空文字や不正 URL に差し替えられた場合のクラッシュを避けるため、`if let url = URL(string: ...)` で安全に開く方が望ましい
- **ATT (App Tracking Transparency) との混同**: アプリ内のトグルは「ふじゅ〜銀行アプリ独自のアプリ利用状況分析の許諾」であり、iOS の OS レベル ATT 許諾とは別物。レビュー時に App Store Review Guidelines の文脈で誤読されないよう、トグル文言「アプリのトラッキングを許可」「利用状況の分析と改善に使用されます」は Figma 通りで維持しつつ、SDK 連動タスクで ATT との関係を改めて整理する
- **`accountPath` 状態**: `RootTabView` でアカウントタブの `NavigationStack` パスを `@State` 保持しているため、プライバシー設定画面でタブを切り替えて戻るとプライバシー設定画面のままになる（client-bank-5 で確立済みの挙動）。Android の手動スタックと挙動が揃うことを確認
- **Figma アセット不要**: 本画面は SwiftUI 標準コンポーネント（`Toggle`、SF Symbol `chevron.left` / `chevron.right`）で完結するため、`docs/figma-assets/798-12559/` への SVG/PDF 書き出しは不要

## 参考リンク

- Figma 確定デザイン（プライバシー設定、両プラットフォーム共用）: [798:12559](https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=798-12559&m=dev)
- Figma 参考スクリーンショット: [`docs/figma-assets/bank-redesign-798-12559.png`](../figma-assets/bank-redesign-798-12559.png)
- 前提タスク 8 (Android 実装 + `:shared` 基盤): [`client-bank-8-privacy-settings-android.md`](./client-bank-8-privacy-settings-android.md)
- 前提タスク 5 (iOS アカウント設定、本タスクが踏襲する iOS 実装パターン): [`client-bank-5-account-settings-ios.md`](./client-bank-5-account-settings-ios.md)
- 既存 `ObservableNotificationSettingsViewModel`（パターン参考）: `iosApp/iosApp/Features/Account/ObservableNotificationSettingsViewModel.swift`
- 既存 `AccountIos.kt`（ブリッジ追加先）: `shared/src/iosMain/kotlin/studio/nxtech/fujubank/account/AccountIos.kt`
- 既存 `KoinIos.kt`（ファサード関数追加先）: `shared/src/iosMain/kotlin/studio/nxtech/fujubank/di/KoinIos.kt`
- 差し替え対象 iOS 配線箇所: `iosApp/iosApp/Features/Shell/RootTabView.swift`（`case .privacy`）

---

## Notion タスク登録用サマリ

- **タイトル**: 銀行アプリクライアント：プライバシー設定画面 iOS 実装
- **プレフィックス**: client-bank-9
- **ブランチ命名**: `feature/client-bank-9-privacy-settings-ios`
- **メモ欄に貼る計画書パス**: `docs/tasks/client-bank-9-privacy-settings-ios.md`
- **依存タスク**: client-bank-8 (Android 実装 + `:shared` 基盤) 完了
- **PR 構成**: 1 本（iOS + `:shared/iosMain` の薄いブリッジ追加。`commonMain` は前タスクで完了済み）
- **参考 Figma**: [プライバシー設定 798:12559](https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=798-12559&m=dev)
