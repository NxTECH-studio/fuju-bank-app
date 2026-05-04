# 銀行アプリクライアント：アカウント情報編集（表示名 / メール）ボトムシート iOS 実装

## 概要

client-bank-10 で Android 側に実装した **アカウント情報編集ボトムシート**（Figma [697:8394](https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=697-8394)、両プラットフォーム共用デザイン）を iOS (SwiftUI) に移植する。`:shared` 側の `AccountProfileProvider` は client-bank-10 で **`profile: StateFlow<AccountProfile>` + `updateProfile(displayName, email)`** に進化済み。本タスクでは iOS 画面の実装、`AccountIos.kt` への薄い Flow 観測ブリッジ追加、`RootTabView` の `AccountDestination.accountEdit` 廃止を行う。

## 背景・目的

### 経緯

- client-bank-10 (`client-bank-10-account-info-edit-android.md`) で Android 側のアカウント情報編集ボトムシート、および両プラットフォーム共通の `:shared` 基盤（`AccountProfileProvider` の `StateFlow` 化 + `updateProfile`）が完了している
- iOS 側のアカウントタブは現状:
  - 「アカウント情報」セクションに鉛筆アイコンがない
  - 「設定」セクションに「アカウント情報」行があり、`AccountDestination.accountEdit` → `AccountComingSoonView(title: "アカウント情報変更")` への準備中遷移を仮配線
  - `ObservableAccountHubViewModel` が `KoinIosKt.accountProfileProvider().current()` を **1 回だけ** 呼んで保持（client-bank-10 で互換のため `current()` は一時残置されているが、本タスクで完全削除する前提）
- KMP プロジェクトとして「両プラットフォーム同等の体験を提供する」という方針（メモリ: KMP は iOS/Android 両対応必須）に沿い、iOS 側にも同じ編集 UI を載せる必要がある

### 目的

- アカウントハブ画面の「アカウント情報」セクションに鉛筆アイコンを追加し、タップで `AccountInfoEditSheetView`（SwiftUI `.sheet` ベース）を開く
- ボトムシート上で「表示名 / メールアドレス」を編集 → 保存ボタンで `AccountProfileProvider.updateProfile(...)` を呼び、カード表示が即時更新される
- 設定セクションから「アカウント情報」行および `AccountDestination.accountEdit` 配線を削除する
- `ObservableAccountHubViewModel` を `current()` ベースから **`observeFlow` ベース** に書き換え（`profile: StateFlow<AccountProfile>` を購読して `@Published var profile` に橋渡し）。`AccountIos.kt` に `observeAccountProfile(provider:onChange:)` を追加
- client-bank-10 で互換のため一時残置されていた `AccountProfileProvider.current()` を完全削除する

### Figma 確定デザインの主な仕様（697:8394）

参考スクリーンショット候補: `docs/figma-assets/bank-redesign-697-8394.png`（client-bank-10 で書き出し済み）。

- 「アカウント情報」セクションのカード右上に鉛筆アイコン（既存 `EditPencil.imageset`、18pt、`textTertiary` tint）
- ボトムシート（編集 UI）
  - SwiftUI `.sheet(isPresented:)` ベース。プレゼンテーション detents は `.medium` を試して、足りなければ `.large` にフォールバック
  - タイトル `アカウント情報を編集`（`FujuBankTypography.title` または既存準拠）
  - `TextField` 2 つ（`表示名` / `メールアドレス`）。メールは `.keyboardType(.emailAddress)` + `.textContentType(.emailAddress)` + `.autocapitalization(.none)`
  - 下部に `保存` ボタン（既存ボタンスタイル準拠）
  - シート外スワイプダウン / `キャンセル` 相当（必要なら toolbar の `Cancel` ボタン）で閉じる
  - バリデーション: 表示名 1 文字以上 / メールに `@` を含む。NG なら保存ボタン disabled

## スコープ

- **`:shared` 側の追加（最小）**
  - `shared/src/iosMain/kotlin/studio/nxtech/fujubank/account/AccountIos.kt`（既存）に
    `observeAccountProfile(provider: AccountProfileProvider, onChange: (AccountProfile) -> Unit): FlowToken` を追加（`observeDepositEnabled` / `observeTransferEnabled` と同パターン、`observeFlow` を共用）
  - `shared/src/commonMain/kotlin/studio/nxtech/fujubank/account/AccountProfileProvider.kt`: client-bank-10 で互換維持のため残置されていた `current()` を削除
- **iOS 画面実装**
  - `iosApp/iosApp/Features/Account/Components/AccountInfoSectionView.swift`: 鉛筆アイコン追加 + `onEditTap: () -> Void` クロージャを受け取る
  - `iosApp/iosApp/Features/Account/Components/AccountInfoEditSheetView.swift`（新規）: SwiftUI `.sheet` 内で表示する編集 UI
  - `iosApp/iosApp/Features/Account/AccountHubView.swift`: `@State private var showEditSheet = false` を持ち、鉛筆タップで開く / 保存・破棄で閉じる。`onSelectDestination` シグネチャから `accountEdit` 関連の責務を抜く
  - `iosApp/iosApp/Features/Account/ObservableAccountHubViewModel.swift`: `observeAccountProfile` で `profile` を購読する形に書き換え + `updateProfile(displayName:email:)` 追加。`init` で `FlowToken` を保持し、`deinit` で `cancel()`
- **配線の削除**
  - `RootTabView.swift` の `AccountDestination.accountEdit` ケースを削除
  - `AccountDestination` enum から `accountEdit` ケースを削除
  - `AccountHubView` の SettingsCardView の rows 配列から「アカウント情報」行を削除（→ 通知 / プライバシー設定 の 2 行）

### アウトオブスコープ

- **「ユーザー ID」（Twitter ハンドル相当）行の編集**: client-bank-10 と同様、別タスク扱い
- **プロフィールカード上部の `ID: xxxx`（内部 ID）の編集**: 表示のみ
- **パスワード変更**: 別タスク
- **「情報」セクション（プライバシーポリシー / 利用規約）の本実装**: client-bank-8 / 9 の `PrivacyContent` を参照する別タスク
- **実 API 連携**: 別タスク
- **アバター画像変更**: Figma 上にも編集 UI なし
- **`AccountComingSoonView` の削除**: 引き続きプライバシー設定（client-bank-9 マージ前）等で利用される可能性があるため残置

## 着手条件

- **client-bank-10 (`client-bank-10-account-info-edit-android.md`) が `main` にマージ済み**
  - `:shared` の `AccountProfileProvider` が `profile: StateFlow<AccountProfile>` + `updateProfile(displayName, email)` を提供している
  - `DummyAccountProfileProvider` が in-memory state 化されている
  - Android 側の対応は完了済みで、iOS 側だけ追従すれば KMP 同等になる状態

## 影響範囲

- モジュール: `:shared` / `:iosApp`
  - `:shared/commonMain`:
    - `account/AccountProfileProvider.kt`: `current()` 完全削除
  - `:shared/iosMain`:
    - `account/AccountIos.kt`: `observeAccountProfile(...)` 追加
  - `:iosApp`:
    - `Features/Account/Components/AccountInfoSectionView.swift` 改修（鉛筆アイコン追加）
    - `Features/Account/Components/AccountInfoEditSheetView.swift` 新規
    - `Features/Account/AccountHubView.swift` 改修（シート開閉 + accountEdit 廃止）
    - `Features/Account/ObservableAccountHubViewModel.swift` 改修（StateFlow 購読 + updateProfile 追加）
    - `Features/Shell/RootTabView.swift` 改修（AccountDestination.accountEdit ケース削除）
- 破壊的変更:
  - `AccountProfileProvider.current()` 完全削除（client-bank-10 で互換残置を予告済み）
  - `AccountDestination.accountEdit` ケース削除（外部から参照されている箇所がないか念のため Grep する）
- 追加依存:
  - なし

## 技術アプローチ

### `:shared` 側設計

#### `AccountIos.kt` 拡張

```kotlin
import studio.nxtech.fujubank.session.FlowToken
import studio.nxtech.fujubank.session.observeFlow

fun observeAccountProfile(
    provider: AccountProfileProvider,
    onChange: (AccountProfile) -> Unit,
): FlowToken = observeFlow(provider.profile) { value -> onChange(value) }
```

`observeDepositEnabled` / `observeTransferEnabled` と同じパターン。Swift 側からは `AccountIosKt.observeAccountProfile(provider:onChange:)` で呼べる。

#### `AccountProfileProvider.current()` 削除

client-bank-10 では Android / iOS 双方のビルドを壊さないために `current()` を `default { return profile.value }` 相当で残していたが、本タスクで iOS 呼び出し側を `observeFlow` に切り替えるため、`current()` を削除して API を最小化する。

### iOS 側設計

#### `ObservableAccountHubViewModel`

```swift
@MainActor
final class ObservableAccountHubViewModel: ObservableObject {
    @Published private(set) var profile: AccountProfile

    private let provider: AccountProfileProvider
    private var profileToken: FlowToken?

    init() {
        let p = KoinIosKt.accountProfileProvider()
        self.provider = p
        // Swift `init` 内で self をクロージャに渡す前に必ず初期値を埋めておく
        self.profile = p.profile.value as! AccountProfile
        self.profileToken = AccountIosKt.observeAccountProfile(provider: p) { [weak self] next in
            // observeFlow は collect スレッドで呼ばれるため UI 反映は MainActor で
            Task { @MainActor in
                self?.profile = next
            }
        }
    }

    deinit {
        profileToken?.cancel()
    }

    func updateProfile(displayName: String, email: String) {
        provider.updateProfile(displayName: displayName, email: email)
    }
}
```

注意: `provider.profile.value` の型キャストは Kotlin `StateFlow.value` の Swift 露出形に応じて調整（`Any?` 経由になる場合あり）。同等パターンが既に `NotificationSettingsPreferences` 周りで確立されているため、それを参照する。

#### `AccountInfoSectionView`

```swift
struct AccountInfoSectionView: View {
    let displayName: String
    let email: String
    let onEditTap: () -> Void
    // 既存 body の最上部に ZStack(alignment: .topTrailing) を被せ、右上に編集ボタンを置く
}
```

鉛筆は `Image("EditPencil").renderingMode(.template).foregroundStyle(FujuBankPalette.textTertiary)` で 18pt。タップ領域は `.frame(width: 36, height: 36)` 確保。

#### `AccountInfoEditSheetView`

```swift
struct AccountInfoEditSheetView: View {
    let initialDisplayName: String
    let initialEmail: String
    let onSave: (String, String) -> Void
    let onCancel: () -> Void

    @State private var displayName: String
    @State private var email: String

    init(initialDisplayName: String, initialEmail: String,
         onSave: @escaping (String, String) -> Void, onCancel: @escaping () -> Void) {
        self.initialDisplayName = initialDisplayName
        self.initialEmail = initialEmail
        self.onSave = onSave
        self.onCancel = onCancel
        _displayName = State(initialValue: initialDisplayName)
        _email = State(initialValue: initialEmail)
    }

    private var isValid: Bool { !displayName.isEmpty && email.contains("@") }

    var body: some View {
        NavigationStack {
            // VStack: TextField x2 + 余白
        }
        .toolbar {
            ToolbarItem(placement: .cancellationAction) { Button("キャンセル", action: onCancel) }
            ToolbarItem(placement: .confirmationAction) {
                Button("保存") { onSave(displayName, email) }.disabled(!isValid)
            }
        }
        .presentationDetents([.medium, .large])
    }
}
```

`presentationDetents` で半開きを許容しつつ、入力中にキーボードが出ても自動的に大きくなる。

#### `AccountHubView`

```swift
struct AccountHubView: View {
    @StateObject private var viewModel = ObservableAccountHubViewModel()
    let onSelectDestination: (AccountDestination) -> Void
    @State private var showEditSheet = false

    var body: some View {
        ScrollView {
            // ProfileCardView ...
            sectionLabel("アカウント情報")
            AccountInfoSectionView(
                displayName: viewModel.profile.displayName,
                email: viewModel.profile.email,
                onEditTap: { showEditSheet = true },
            )
            // SettingsCardView: rows = 通知 / プライバシー設定 のみ
        }
        .sheet(isPresented: $showEditSheet) {
            AccountInfoEditSheetView(
                initialDisplayName: viewModel.profile.displayName,
                initialEmail: viewModel.profile.email,
                onSave: { name, mail in
                    viewModel.updateProfile(displayName: name, email: mail)
                    showEditSheet = false
                },
                onCancel: { showEditSheet = false },
            )
        }
    }
}
```

### `RootTabView` 縮退

```swift
.navigationDestination(for: AccountDestination.self) { dest in
    switch dest {
    case .notifications: NotificationSettingsView(...)
    case .privacy: AccountComingSoonView(title: "プライバシー設定") // client-bank-9 マージ後は本実装に置換
    // .accountEdit を削除
    }
}
```

`AccountDestination` enum から `accountEdit` を削除（`AccountHubView` 側でも参照しない）。

## 実装手順

1. **`:shared` 改修**
   1. `shared/src/iosMain/kotlin/studio/nxtech/fujubank/account/AccountIos.kt` に `observeAccountProfile(provider, onChange)` を追加
   2. `shared/src/commonMain/kotlin/studio/nxtech/fujubank/account/AccountProfileProvider.kt` から `current()` を削除（互換のための残置を撤去）
   3. `./gradlew :shared:allTests` 通過
   4. `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` 通過
2. **iOS VM 改修**
   1. `ObservableAccountHubViewModel.swift` を `observeAccountProfile` 経由の購読 + `updateProfile` 追加に書き換え
   2. `init` で初期値を `provider.profile.value` から取得し、`FlowToken` を保持
   3. `deinit` で `profileToken?.cancel()`
3. **コンポーネント実装**
   1. `AccountInfoSectionView.swift` に鉛筆アイコン + `onEditTap` 追加
   2. `AccountInfoEditSheetView.swift` 新規作成（NavigationStack + toolbar + TextField x2）
4. **画面配線**
   1. `AccountHubView.swift` に `@State private var showEditSheet` 追加
   2. `AccountInfoSectionView` の `onEditTap` でシートを開く
   3. `.sheet(isPresented:)` で `AccountInfoEditSheetView` を表示し、`onSave` で VM 経由更新 + シート閉じ
   4. `SettingsCardView` の rows から「アカウント情報」を削除
5. **`RootTabView` 縮退**
   1. `AccountDestination` enum から `accountEdit` ケースを削除
   2. `navigationDestination(for: AccountDestination.self)` の `.accountEdit` ケースを削除
6. **動作確認（iOS Simulator）**
   1. `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` 通過
   2. iOS Simulator で起動 → アカウントタブ → 「アカウント情報」セクションに鉛筆が表示される
   3. 鉛筆タップで編集シートが立ち上がる
   4. 表示名 / メールを変更して「保存」 → シートが閉じてカードの値が更新される
   5. 編集後にシートを閉じる→再度開くと、保存済みの最新値が初期値として入っている（in-memory 永続）
   6. 設定セクションが「通知 / プライバシー設定」の 2 行に縮退している
   7. シート外スワイプダウンで閉じる
7. **PR 作成**: `feature/client-bank-11-account-info-edit-ios` → `main`（メモリ: feature ブランチ + PR 必須）

## 完了条件

- [ ] `./gradlew build` が通る
- [ ] `./gradlew :shared:allTests` が通る
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が通る
- [ ] iOS Simulator で `iosApp` がビルド・起動する（Xcode）
- [ ] アカウントハブの「アカウント情報」セクションに鉛筆アイコンが表示される
- [ ] 鉛筆タップでシートが立ち上がり、表示名 / メールアドレスを編集できる
- [ ] 「保存」で `AccountProfileProvider.updateProfile(...)` が呼ばれ、カード上の表示が即時更新される
- [ ] 設定セクションから「アカウント情報」行が削除され、`AccountDestination.accountEdit` が削除されている
- [ ] `AccountProfileProvider.current()` が完全削除されている

## 想定される懸念・リスク

- **`StateFlow.value` の Swift 露出**: Kotlin `StateFlow<AccountProfile>` の Swift 側型表現は `Any?` ベースになるためキャストが必要。既存の `SessionStoreIos.observeBootstrapped` 周辺に同等パターンがあるはずなので参照する。型キャストを安全に行えない場合は、`AccountIos.kt` 側に `currentAccountProfile(provider): AccountProfile` を追加して初期値取得用の薄いブリッジを提供する案も併用検討
- **`observeFlow` のスレッド**: `observeFlow` の collect は非 main スレッドで呼ばれる前提。`@MainActor` で `Task` にラップして UI 反映する。複数回連続更新時は `Task` のキューイング順序に依存するが、今回は単一値の上書きなので実害なし
- **`@StateObject` の生存期間**: `AccountHubView` は `@StateObject` で VM を生成しているため、ビューが再生成されない限り `init` が再実行されない。従って `FlowToken` も view の生存と同期する。SwiftUI のホットリロード時には `deinit` が走らないことがあるが、本番挙動には影響しない
- **`AccountInfoEditSheetView` の `init` で `@State` を初期化するパターン**: SwiftUI で外部から `@State` を注入する標準手法。ただし view 識別子が変わると state がリセットされるため、`.sheet(isPresented:)` で都度生成される今回のケースは正しく動作する
- **`presentationDetents` の挙動**: iOS 16+ で利用可。プロジェクトの最低 iOS バージョンを確認（`iosApp` の deployment target が 16.0 未満なら detents なしで `.sheet` のみで開く）。最低 iOS 16+ 前提で進める
- **`AccountComingSoonView` の i18n 文字列との整合性**: `RootTabView` の `case .accountEdit` を削除するとともに、これまで `"アカウント情報変更"` 文字列を渡していた箇所が消える。タイポチェックの観点で不要文言が残らないことを確認
- **client-bank-9 (privacy iOS) との差し合い**: `RootTabView.swift` の `navigationDestination(for: AccountDestination.self)` を両タスクが触る。`accountEdit` ケースの削除（本タスク）と `privacy` ケースの本実装置換（client-bank-9）はそれぞれ別のケースを編集するため衝突は限定的。ただしマージ順序によっては手動で `switch` の網羅チェックを行う必要あり

## 参考リンク

- Figma 確定デザイン（アカウントハブ刷新）: [697:8394](https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=697-8394)
- 前提タスク: [`client-bank-10-account-info-edit-android.md`](./client-bank-10-account-info-edit-android.md)
- 既存 `AccountIos.kt`（observeFlow パターン参考）: `shared/src/iosMain/kotlin/studio/nxtech/fujubank/account/AccountIos.kt`
- 既存 `AccountProfileProvider`: `shared/src/commonMain/kotlin/studio/nxtech/fujubank/account/AccountProfileProvider.kt`
- 改修対象 iOS VM: `iosApp/iosApp/Features/Account/ObservableAccountHubViewModel.swift`
- 改修対象 iOS 画面: `iosApp/iosApp/Features/Account/AccountHubView.swift`
- 改修対象セクション UI: `iosApp/iosApp/Features/Account/Components/AccountInfoSectionView.swift`
- 改修対象シェル配線: `iosApp/iosApp/Features/Shell/RootTabView.swift`
- 既存 `EditPencil.imageset`: `iosApp/iosApp/Assets.xcassets/EditPencil.imageset/`（`ProfileCardView` で使用中）
- 前提タスク（iOS アカウント設定 MVP）: [`client-bank-5-account-settings-ios.md`](./client-bank-5-account-settings-ios.md)
- 関連タスク（プライバシー設定 iOS）: [`client-bank-9-privacy-settings-ios.md`](./client-bank-9-privacy-settings-ios.md)

---

## Notion タスク登録用サマリ

- **タイトル**: 銀行アプリクライアント：アカウント情報編集（表示名 / メール）ボトムシート iOS 実装
- **プレフィックス**: client-bank-11
- **ブランチ命名**: `feature/client-bank-11-account-info-edit-ios`
- **メモ欄に貼る計画書パス**: `docs/tasks/client-bank-11-account-info-edit-ios.md`
- **依存タスク**: client-bank-10（Android + 共通 `:shared` 拡張）の `main` マージが必須
- **後続タスク**: ユーザー ID（ハンドル）編集 / パスワード変更 / 実 API 連携（プロフィール更新エンドポイント）
- **PR 構成**: 1 本（iOS + 共通 `:shared` の `current()` 削除 + `observeAccountProfile` ブリッジ追加）
- **参考 Figma**: [アカウントハブ 697:8394](https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=697-8394)
