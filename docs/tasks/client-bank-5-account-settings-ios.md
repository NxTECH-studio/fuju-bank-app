# 銀行アプリクライアント：アカウント設定画面（ハブ + 通知設定）iOS 実装

## 概要

client-bank-4 で Android 側に実装した **アカウントハブ画面**（Figma `697:8394`）/ **通知設定画面**（Figma `718:7332`）/ **準備中画面** を iOS (SwiftUI) に移植する。`:shared` 側の `NotificationSettingsPreferences` / `AccountProfileProvider` は前タスクで導入済みのため、本タスクでは iOS 画面の実装と `RootTabView` の配線変更のみを行う。

## 背景・目的

### 経緯

- client-bank-4 (`client-bank-4-account-settings-android.md`) で Android 側のアカウントハブ / 通知設定 / 準備中画面、および両プラットフォーム共通の `:shared` 基盤（`NotificationSettingsPreferences`、`AccountProfileProvider`）が完了している
- iOS のアカウントタブは `AccountPlaceholderView` でダミー表示のままであり、Android と iOS で画面構成がずれている状態
- KMP プロジェクトとして「両プラットフォーム同等の体験を提供する」というプロジェクト方針（メモリ: KMP は iOS/Android 両対応必須）に沿って、iOS 側にも同じ画面構成を載せる必要がある

### 目的

- 「アカウント」タブの iOS プレースホルダを廃止し、Figma 準拠のハブ画面と各子画面を表示する
- Android で確立したパターン（`AccountProfileProvider.current()` 購読、`NotificationSettingsPreferences` の `StateFlow` を `IosStateFlowWrapper` 経由で `@Published` に橋渡し）を踏襲し、Android との挙動差を最小化する
- 通知設定トグルの永続化値を Android と同じキーで共有し、ユーザーがプラットフォームをまたいでも一貫した状態を保てる土台を維持する

## スコープ

- **アカウントハブ画面** (`AccountHubView`): Figma `697:8394` 準拠
  - プロフィールカード（円形アバター、ユーザー名、SNS 出典バッジ + サブテキスト）
  - アカウント情報セクション（表示名、メールアドレス）
  - 設定セクション 3 行（プライバシー設定 / 通知設定 / アカウント情報変更）
- **通知設定画面** (`NotificationSettingsView`): Figma `718:7332` 準拠
  - 着金通知トグル（「ふじゅ〜が届いたとき」相当）
  - 転送通知トグル（「送金が完了したとき」相当）
  - 値は client-bank-4 で導入済みの `NotificationSettingsPreferences` 経由で永続化
- **準備中画面** (`ComingSoonView`)
  - 中央に「準備中です」表示、ナビバー戻るボタン付き
  - タイトル文字列のみ差し替えてプライバシー設定 / アカウント情報変更から再利用
- **タブ配線**
  - `RootTabView` のアカウントタブを `NavigationStack { AccountHubView() }` に差し替え、`navigationDestination(for:)` で子画面遷移を組む
- **iOS 用 Figma アセット配置**
  - `docs/figma-assets/697-8394/` / `docs/figma-assets/718-7332/` に保存済みの SVG を PDF (vector) に変換し、`iosApp/iosApp/Assets.xcassets/` に追加

### アウトオブスコープ

- **`:shared` への変更は本タスクではしない**（client-bank-4 で完了済みのため）。もし API 不足が見つかった場合は、本タスク内で `:shared` を追記して構わないが、原則として既存 API のまま完結させる
- Android 側の追加実装（client-bank-4 で完了済み）
- プライバシー設定 / アカウント情報変更の本実装（次タスク以降）
- バックエンド API による実プロフィール取得
- iOS の OS 通知許可ダイアログ連携（トグル状態の永続化のみ）

## 着手条件

**client-bank-4 (`client-bank-4-account-settings-android.md`) 完了後に着手**。
具体的には以下が `main` にマージ済みであること:

- `:shared/commonMain` に `NotificationSettingsPreferences` / `AccountProfileProvider` / `AccountProfile` が追加され、Koin に登録されている
- `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が通る状態
- `docs/figma-assets/697-8394/` / `docs/figma-assets/718-7332/` に Figma アセットが保存済み（client-bank-4 で書き出したものを再利用）

## 影響範囲

- モジュール: `iosApp`
  - `iosApp/iosApp/Features/Account/` 配下に SwiftUI 画面 3 種追加
  - `RootTabView` 配線変更
  - `Assets.xcassets` にアセット追加
- 破壊的変更:
  - アカウントタブの destination が `AccountPlaceholderView` から `AccountHubView` に置き換わる
- 追加依存:
  - なし。`:shared` 側は client-bank-4 で導入済みのものをそのまま参照する

## 技術アプローチ

### Android で確立したパターンを踏襲

client-bank-4 の Android 実装で以下の構造が確定している。iOS 側もこの構造に揃える:

- 画面 = Hub / NotificationSettings / ComingSoon の 3 種
- ViewModel = `AccountProfileProvider.current()` を購読する Hub 用、`NotificationSettingsPreferences` の `StateFlow` を購読する NotificationSettings 用、ComingSoon は state を持たない
- 遷移 = ハブから 3 行のうち 2 行（プライバシー / アカウント情報変更）は ComingSoon、1 行（通知設定）は専用画面
- ComingSoon は `title: String` を引数に取り、本文は固定「準備中です」

### iOS 側設計

`iosApp/iosApp/Features/Account/` 配下に追加:

```
Features/Account/
├── AccountHubView.swift
├── ObservableAccountHubViewModel.swift
├── NotificationSettingsView.swift
├── ObservableNotificationSettingsViewModel.swift
├── ComingSoonView.swift
└── Components/
    ├── ProfileCardView.swift
    ├── AccountInfoSectionView.swift
    └── SettingsRowView.swift
```

#### `ObservableAccountHubViewModel`

```swift
@MainActor
final class ObservableAccountHubViewModel: ObservableObject {
    @Published private(set) var profile: AccountProfile

    init(provider: AccountProfileProvider = SharedDI.resolve()) {
        self.profile = provider.current()
    }
}
```

`AccountProfileProvider.current()` を一度呼んで保持するだけのシンプルな構造（Android の `AccountHubViewModel` と同等）。

#### `ObservableNotificationSettingsViewModel`

```swift
@MainActor
final class ObservableNotificationSettingsViewModel: ObservableObject {
    @Published var depositEnabled: Bool
    @Published var transferEnabled: Bool

    private let preferences: NotificationSettingsPreferences
    private var depositCancellable: AnyCancellable?
    private var transferCancellable: AnyCancellable?

    init(preferences: NotificationSettingsPreferences = SharedDI.resolve()) {
        self.preferences = preferences
        self.depositEnabled = ...   // initial value from preferences.depositEnabled.value
        self.transferEnabled = ...

        // IosStateFlowWrapper で StateFlow → Publisher に橋渡し
        depositCancellable = IosStateFlowWrapper(preferences.depositEnabled)
            .publisher
            .receive(on: DispatchQueue.main)
            .assign(to: \.depositEnabled, on: self)
        transferCancellable = ...
    }

    func setDepositEnabled(_ value: Bool) { preferences.setDepositEnabled(value: value) }
    func setTransferEnabled(_ value: Bool) { preferences.setTransferEnabled(value: value) }
}
```

`IosStateFlowWrapper`（Task 3 で導入済み）を使って `:shared` の `StateFlow<Boolean>` を Combine の `Publisher` に橋渡しする。SwiftUI の `Toggle` は `Binding` が必要なので、`Toggle("...", isOn: Binding(get: { vm.depositEnabled }, set: { vm.setDepositEnabled($0) }))` のように `setDepositEnabled` / `setTransferEnabled` 経由で書き戻す。

#### `RootTabView` 配線変更

```swift
TabView {
    HomeView()...
    TransactionsView()...
    NavigationStack {
        AccountHubView()
            .navigationDestination(for: AccountDestination.self) { dest in
                switch dest {
                case .notifications:        NotificationSettingsView()
                case .privacy:              ComingSoonView(title: "プライバシー設定")
                case .accountEdit:          ComingSoonView(title: "アカウント情報変更")
                }
            }
    }
    .tabItem { ... }
}

enum AccountDestination: Hashable {
    case notifications, privacy, accountEdit
}
```

`AccountHubView` の各設定行は `NavigationLink(value: AccountDestination.notifications) { SettingsRowView(...) }` のように value を渡してスタック遷移する。

### Figma アセットの iOS 配置

`docs/figma-assets/697-8394/` / `docs/figma-assets/718-7332/` に保存済みの SVG を PDF (vector) に変換し、`iosApp/iosApp/Assets.xcassets/` に追加する（Task 3 で確立したフローと同じ）。

### 準備中画面

```swift
struct ComingSoonView: View {
    let title: String
    var body: some View {
        VStack { ... "準備中です" ... }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
    }
}
```

タイトルだけ差し替え、本文は固定。

## 実装手順

1. **Figma アセット iOS 配置**
   1. `docs/figma-assets/697-8394/` / `docs/figma-assets/718-7332/` の SVG を PDF (vector) に変換し `iosApp/iosApp/Assets.xcassets/` に追加
2. **共通コンポーネント作成**
   1. `ProfileCardView` / `AccountInfoSectionView` / `SettingsRowView` を `Features/Account/Components/` に実装
   2. SwiftUI Preview で Figma と並べて目視確認
3. **`AccountHubView` 実装**
   1. Figma `697:8394` 準拠でレイアウト組み
   2. `ObservableAccountHubViewModel` で `AccountProfileProvider.current()` の結果を保持
   3. 設定行 3 つのタップで `NavigationLink(value:)` を発火
4. **`NotificationSettingsView` 実装**
   1. Figma `718:7332` 準拠で `Toggle` 2 つを配置
   2. `ObservableNotificationSettingsViewModel` で `NotificationSettingsPreferences` の `StateFlow` を `IosStateFlowWrapper` 経由で購読し、`@Published` に流す
   3. トグル変更で `setDepositEnabled` / `setTransferEnabled` を呼ぶ
   4. アプリ再起動後も値が保持されることを Simulator で確認
   5. Android 側で設定した値が iOS 側でも見えることを確認（同じ `multiplatform-settings` キーを使うため）
5. **`ComingSoonView` 実装**
   1. タイトル + 中央「準備中です」+ ナビバー戻るボタン
6. **`RootTabView` 配線変更**
   1. アカウントタブを `NavigationStack { AccountHubView() }` に差し替え
   2. `navigationDestination(for:)` で `NotificationSettingsView` / `ComingSoonView(title:)` への遷移を組む
   3. スワイプバック / 戻るボタン両方で動作することを確認
   4. 旧 `AccountPlaceholderView` を削除（or unused 化）
7. **動作確認**
   1. `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` 通過
   2. Xcode で iOS Simulator (Arm64) ビルド成功
   3. iOS Simulator で Figma `697:8394` / `718:7332` と並べて screenshot 比較
   4. Android と iOS でトグル動作・遷移挙動が揃っていることを確認（client-bank-4 マージ済みの Android ビルドと並べて目視）
8. **PR 作成**: `feature/client-bank-5-account-settings-ios` → `main`

## 完了条件

- [ ] `./gradlew build` が通る
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が通る
- [ ] Xcode で iOS Simulator (Arm64) ビルドが通り、アプリが起動する（目視確認）
- [ ] iOS: アカウントタブから `AccountHubView` が表示され、Figma `697:8394` と見た目が揃っている
- [ ] iOS: 通知設定画面が Figma `718:7332` と見た目が揃っている
- [ ] 着金通知 / 転送通知のトグルがアプリ再起動後も保持される
- [ ] Android（client-bank-4 マージ済み）と iOS で、トグル動作・遷移挙動・見た目が揃っている
- [ ] プライバシー設定 / アカウント情報変更タップ → 準備中画面表示 → 戻る、が動く
- [ ] アカウントタブの旧 `AccountPlaceholderView` が削除されている（or unused になっている）

## 想定される懸念・リスク

- **`IosStateFlowWrapper` を Bool で使うのが初**: Task 3 で導入した `IosStateFlowWrapper` は他用途で実績があるが、`StateFlow<Boolean>` で使うのが初なら、Kotlin の `Boolean` が Swift 側で `KotlinBoolean` として届く点に注意。`Bool` への変換が必要になった場合は wrapper 側の generic 取り扱いを確認する。
- **トグルの 2 way binding**: SwiftUI の `Toggle` は `Binding<Bool>` を要求する。`@Published` に直接バインドすると `:shared` への書き戻しがされないため、必ず `Binding(get:set:)` で `setDepositEnabled` / `setTransferEnabled` を呼ぶ形にする。
- **`:shared` API 不足が見つかった場合**: 原則 client-bank-4 で確定しているはずだが、もし不足があれば本タスク内で追記する。その場合は Android 側のリグレッションが起きないことを `./gradlew :composeApp:assembleDebug` で確認する。
- **`NavigationStack` のタブ切り替え時のスタック保持**: `RootTabView` 内で他タブに切り替えて戻ったときに、ハブ → 通知設定の状態が維持されるべきか・リセットされるべきかは Android の挙動と揃える（Android は手動スタックなので維持される想定）。Simulator で挙動差が出た場合は要相談。
- **準備中画面の戻り先**: ハブ → 準備中 → 戻る、で必ずハブに戻ることを確認。
- **アクセシビリティ**: `Toggle` の `accessibilityLabel`、`SettingsRowView` の `accessibilityHint` を Android の `Modifier.semantics` と同レベルで設定する。
- **Figma アセットの色解決**: SVG → PDF 変換時に Figma の Variables（カラートークン）が静的色に焼き込まれるケースがある。Theme 切り替えに追従させたい場合は `Assets.xcassets` 上で Light/Dark 別に PDF を分けるか、SwiftUI 側で `.foregroundStyle` を当てる。Task 3 と同じ運用に揃える。

## 参考リンク

- Figma アカウントハブ: https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=697-8394&m=dev
- Figma 通知設定: https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=718-7332&m=dev
- 前提タスク 4 (Android 実装 + `:shared` 基盤): [`client-bank-4-account-settings-android.md`](./client-bank-4-account-settings-android.md)
- 前提タスク 3 (iOS SwiftUI 統合): [`client-bank-3-ios-multiplatform-integration.md`](./client-bank-3-ios-multiplatform-integration.md)
- 既存 `IosStateFlowWrapper`（iOS で StateFlow を購読する基盤）: `shared/src/iosMain/kotlin/.../util/IosStateFlowWrapper.kt`
- iOS 既存プレースホルダ（差し替え対象）: `iosApp/iosApp/Features/Account/AccountPlaceholderView.swift`

---

## Notion タスク登録用サマリ

- **タイトル**: 銀行アプリクライアント：アカウント設定画面（ハブ + 通知設定）iOS 実装
- **プレフィックス**: client-bank-5
- **ブランチ命名**: `feature/client-bank-5-account-settings-ios`
- **メモ欄に貼る計画書パス**: `docs/tasks/client-bank-5-account-settings-ios.md`
- **依存タスク**: client-bank-4 (Android 実装 + `:shared` 基盤) 完了
- **PR 構成**: 1 本（iOS のみ。`:shared` は前タスクで完了済み）
