# 銀行アプリクライアント：iOS SwiftUI 実装（Task 2 で確定した Android 画面群を SwiftUI で再構築）

## 概要

Task 2 で Figma 準拠に確定した **ホーム画面 / 取引履歴画面 / 取引詳細画面 / ボトムナビ** を、iOS 側は **SwiftUI で再実装** する。Compose Multiplatform を iOS に入れる方針（旧 Task 3）は破棄。`:shared` モジュール（ドメイン / リポジトリ / ViewModel）は KMP のまま再利用し、UI 層のみ Android 用 Compose と iOS 用 SwiftUI を別々に書く。

## 背景・目的

### 経緯

- Task 1 (`client-ios-1-compose-multiplatform-foundation`) で `composeApp` に iOS ターゲットを追加して Compose を iOS に持ち込んだが、`Shared.framework` と `ComposeApp.framework` の二重 embed で Kotlin/Native ランタイム重複が起き起動不能になり revert
- 当初の Task 3 案では framework 統合戦略を再設計して CMP UI を iOS に入れる方針だったが、framework 切替や `transitiveExport` 設定が複雑で詰まるリスクが高い
- 方針変更: **iOS UI は SwiftUI で実装する**（Compose Multiplatform は iOS では使わない）。`:shared` の ViewModel / Repository / Domain は KMP のまま Swift から呼ぶ。これで framework 重複問題は構造的に発生せず、Apple のネイティブ UX も担保しやすい

### 目的

- Task 2 で確定したデザイン (`709-8658` ホーム / `697-7601` 取引履歴 / `702-6440` 取引詳細 + ボトムナビ) を iOS Simulator / 実機でも同じ見た目で表示する
- `:shared` の ViewModel を Swift 側から購読して Android と同じビジネスロジックで動かす
- iOS のネイティブな挙動（NavigationStack / SafeArea / ジェスチャ）を素直に SwiftUI で享受する

## スコープ

- 以下 Android 画面群（Task 2 で Figma 準拠に確定済み）を **SwiftUI で再実装**
  - ホーム画面 (`HomeView`): Figma `709-8658`
  - 取引履歴画面 (`TransactionListView`): Figma `697-7601`
  - 取引詳細画面 (`TransactionDetailView`): Figma `702-6440`
  - ルートシェル (`RootView`): TabView ベースのボトムナビ（支払い FAB 撤去済み 2 タブ均等配置）
- `:shared` の ViewModel (`HomeViewModel` / `TransactionListViewModel` / `TransactionDetailViewModel`) を Swift から購読する仕組みを整備
  - Kotlin の `StateFlow<T>` を Swift で `@Published` 的に扱える wrapper を `iosMain` に追加（KMP-NativeCoroutines or 自前 wrapper）
- Figma アセット (`ic_logo_fuju_bank` / `ic_chevron_right` / `ic_chevron_left` / `ic_notifications` / `ic_account_circle` 等) を `iosApp/Assets.xcassets/` に SVG / PDF で配置
- iOS デザイントークン (Colors / Typography) を Swift で定義（Android の `FujuBankColors` / `FujuBankTypography` と値を揃える）
- iOS 既存 SwiftUI 画面 (`LoginView` 等) との接続: ログイン後に新 `RootView` へ遷移
- iOS Simulator (Arm64) での動作確認

### アウトオブスコープ

- Compose Multiplatform を iOS に取り込む試み（旧 Task 3 案）→ 破棄
- `composeApp` への iOS ターゲット (`iosArm64` / `iosSimulatorArm64`) 追加 → やらない
- Login / Signup / Splash / MFA など他画面の SwiftUI 化 → 個別タスク（既存 SwiftUI 実装をそのまま使う）
- 取引履歴 / 取引詳細のバックエンド統合（Task 2 ではモックデータで見た目だけ確定。リアル API 接続は後続タスク）
- Android 側のロジック変更（Task 2 で確定した実装をそのまま参照する）

## 着手条件

**Task 2 (`client-bank-2-home-screen-figma-redesign-android.md`) 完了後に着手**。
理由: Task 2 で Android 側を Figma 準拠に確定 + ViewModel / UiState を整理してから iOS に持ち込むほうが、見た目とロジックを同時に試行錯誤せずに済むため。

## 影響範囲

- モジュール: `:shared` / `iosApp`
  - `:shared/iosMain` に `StateFlow` 購読用 wrapper（必要なら KMP-NativeCoroutines 追加）
  - `:shared/commonMain` の ViewModel は **そのまま再利用**。Swift から呼べることを確認するための型 export 整備のみ
  - `:composeApp` は変更なし（Android 専用のまま）
- 破壊的変更:
  - iOS アプリの起動経路が変わる（`SplashGate` SwiftUI → ログイン後 → 新 `RootView`）
  - `iosApp/iosApp/` 配下に SwiftUI ファイルを多数追加
- 追加依存:
  - 必要に応じて [KMP-NativeCoroutines](https://github.com/rickclephas/KMP-NativeCoroutines) を追加（StateFlow を Swift で購読しやすくするため）。素の `Shared.framework` の `IosFlowWrapper` 自前実装でも可

## 技術アプローチ

### KMP UI 戦略：SwiftUI 採用

- Compose Multiplatform は iOS に入れない（framework 重複問題を構造的に回避）
- `:shared.framework` は従来どおり 1 つだけ embed する（Kotlin/Native ランタイムは 1 個だけ）
- iOS UI は **SwiftUI で完全に書き直す**。`:shared` の以下を Swift から参照する:
  - `HomeViewModel` / `TransactionListViewModel` / `TransactionDetailViewModel`
  - `HomeUiState` / `TransactionListUiState` / `TransactionDetailUiState`
  - `UserProfile` / `Transaction` 等のドメインモデル
  - `CurrencyFormatter` 等の共通ユーティリティ

### `StateFlow` を SwiftUI で購読する

選択肢 2 つ:

**オプション A**: KMP-NativeCoroutines を導入
- メリット: `@NativeCoroutineState` アノテーションで Swift 側に `AsyncSequence` / `Combine.Publisher` を自動生成。Swift 側のコードがクリーン
- デメリット: 依存追加とビルドフック追加。現状の `:shared` build.gradle.kts に手を入れる

**オプション B**: 自前 `IosStateFlowWrapper` を `iosMain` に書く
```kotlin
// shared/src/iosMain/kotlin/.../util/IosStateFlowWrapper.kt
@OptIn(ExperimentalForeignApi::class)
class IosStateFlowWrapper<T : Any>(private val flow: StateFlow<T>) {
    val value: T get() = flow.value
    fun watch(block: (T) -> Unit): () -> Unit {
        val job = SupervisorJob()
        val scope = CoroutineScope(Dispatchers.Main + job)
        scope.launch { flow.collect { block(it) } }
        return { job.cancel() }
    }
}
```
Swift 側で `ObservableObject` ラッパーを書いて `@Published` に橋渡し。

**推奨**: オプション B（自前 wrapper）。依存追加なし、`:shared` の構成を変えずに済む。Task 2 の ViewModel 数 (3 本) なら自前で十分。

### iOS デザイントークン

`iosApp/iosApp/Theme/` に Swift で定義:

```swift
enum FujuBankColor {
    static let background = Color(red: 0xF6/0xFF, green: 0xF7/0xFF, blue: 0xF9/0xFF)
    static let surface = Color.white
    static let textPrimary = Color(red: 0x11/0xFF, green: 0x11/0xFF, blue: 0x11/0xFF)
    static let textSecondary = Color(red: 0x6E/0xFF, green: 0x6F/0xFF, blue: 0x72/0xFF)
    static let textTertiary = Color(red: 0xB0/0xFF, green: 0xB0/0xFF, blue: 0xB0/0xFF)
    static let brandPink = Color(red: 0xFF/0xFF, green: 0x1E/0xFF, blue: 0x9E/0xFF)
    static let linkBlue = Color(red: 0x18/0xFF, green: 0x7A/0xFF, blue: 0xEA/0xFF)
    // ...
}

enum FujuBankTypography {
    static let headline = Font.system(size: 17, weight: .bold)
    static let title = Font.system(size: 14, weight: .semibold)
    static let body = Font.system(size: 14, weight: .medium)
    // ... Android の `FujuBankTextStyles` と値を揃える
}
```

Android の `FujuBankColors.kt` / `FujuBankTypography.kt` と **値を完全一致** させる（同じ Figma トークンを参照しているため）。

### アセット移行

`composeApp/src/androidMain/res/drawable/*.xml` (Android Vector Drawable) は iOS では直接使えない。Figma から SVG / PDF で書き出して `iosApp/iosApp/Assets.xcassets/` に追加する:

| Android drawable | iOS asset 名 | 形式 |
|---|---|---|
| `ic_logo_fuju_bank.xml` | `LogoFujuBank` | PDF (vector) |
| `ic_chevron_right.xml` | `ChevronRight` | PDF |
| `ic_chevron_left.xml` | `ChevronLeft` | PDF |
| `ic_notifications.xml` | `Notifications` | PDF |
| `ic_account_circle.xml` (Figma 準拠版) | `AccountCircle` | PDF |
| `ic_home.xml` | `Home` | PDF |
| Task 2 で追加した取引履歴・取引詳細用アイコン | 対応 | PDF |

`docs/figma-assets/709-8658/` / `docs/figma-assets/697-7601/` / `docs/figma-assets/702-6440/` に保存済みの SVG をベースに変換すれば再フェッチ不要。

### iOS 画面構造（SwiftUI）

```
iosApp/iosApp/
├── Theme/
│   ├── FujuBankColor.swift
│   └── FujuBankTypography.swift
├── ViewModel/                       (Shared ViewModel の Swift wrapper)
│   ├── ObservableHomeViewModel.swift
│   ├── ObservableTransactionListViewModel.swift
│   └── ObservableTransactionDetailViewModel.swift
├── Util/
│   └── IosStateFlowPublisher.swift (StateFlow → ObservableObject)
└── Features/
    ├── Root/
    │   └── RootView.swift          (TabView: ホーム / アカウント)
    ├── Home/
    │   ├── HomeView.swift
    │   ├── BalanceCardView.swift
    │   ├── FujuBankHeaderView.swift
    │   ├── RecentTransactionsSectionView.swift
    │   └── NotificationBellButtonView.swift
    ├── Transactions/
    │   ├── TransactionListView.swift
    │   ├── TransactionRowView.swift
    │   ├── TransactionDetailView.swift
    │   └── TransactionMetadataCardView.swift
    └── Account/
        └── AccountPlaceholderView.swift
```

### ボトムナビ (TabView)

SwiftUI の `TabView` を使う:
```swift
TabView {
    NavigationStack { HomeView(...) }
        .tabItem { Label("ホーム", systemImage: "house.fill") }
    NavigationStack { AccountPlaceholderView() }
        .tabItem { Label("アカウント", image: "AccountCircle") }
}
.tint(.black)  // 選択時の色
```

Figma の bottom nav は支払い FAB を撤去した 2 タブ均等配置になっているので、`TabView` のデフォルトでほぼ一致する。アイコン・ラベル・色は Figma 値に合わせて微調整。

### NavigationStack による画面遷移

- ホーム → 取引履歴: HomeView の「もっとみる」をタップで `NavigationLink(value: TransactionRoute.list)`
- 取引履歴 → 取引詳細: TransactionListView の各行タップで `NavigationLink(value: TransactionRoute.detail(id:))`
- 戻るボタン: SwiftUI の `NavigationStack` 標準スワイプ + 画面左上の `<` ボタン両方サポート

### `useDummyProfile=true` 互換

Android の `useDummyProfile=true` (local.properties) と同じ仕組みを iOS でも有効にする:
- `:shared` の `ProfileRepository` がフラグを読んで dummy データを返す既存実装を再利用
- iOS 側は何もしない（`:shared` の挙動に任せる）

## 実装手順

1. **`:shared/iosMain` に StateFlow wrapper を追加**: `IosStateFlowWrapper` を実装し、`./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` でビルド可能を確認
2. **iOS Theme を Swift で定義**: `FujuBankColor.swift` / `FujuBankTypography.swift` を作成し、Android と値が一致していることを目視確認
3. **アセット移行**: `docs/figma-assets/` の SVG を PDF (vector) に変換し `Assets.xcassets/` に追加
4. **`ObservableHomeViewModel` 等の wrapper を実装**: `IosStateFlowWrapper` を `@Published` に橋渡しする `ObservableObject` を作る
5. **`HomeView` 実装**: Figma `709-8658` を SwiftUI で再現。Android 実装をリファレンスに同じレイアウト・余白・タイポグラフィにする
6. **`TransactionListView` + `TransactionRowView` 実装**: Figma `697-7601` を再現
7. **`TransactionDetailView` 実装**: Figma `702-6440` を再現
8. **`RootView` (TabView) 実装**: ホーム / アカウントの 2 タブ
9. **`NavigationStack` で画面遷移を配線**: ホーム → 取引履歴 → 取引詳細
10. **`SplashGate` から `RootView` への遷移を配線**: ログイン成功後に `RootView` を表示
11. **iOS Simulator (Arm64) で動作確認**: Android 側と並べて screenshot 比較
12. **`useDummyProfile=true` でバックエンド未起動でも動くことを Android / iOS 両方で確認**

## 完了条件

- [ ] `./gradlew build` が通る
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が通る
- [ ] `./gradlew :shared:linkReleaseFrameworkIosSimulatorArm64` が通る
- [ ] iOS Simulator (Arm64) でアプリが起動し、ログイン → 新 `RootView` → 各画面が表示される
- [ ] Figma `709-8658` / `697-7601` / `702-6440` の見た目が iOS でも Android と同等に再現されている（screenshot 比較）
- [ ] `:shared` の `HomeViewModel` 等が Swift から購読できており、状態変化で SwiftUI が再描画される
- [ ] `useDummyProfile=true` でバックエンド未起動でもホーム画面 / 取引履歴 / 取引詳細が動作する（Android / iOS 両方）
- [ ] ホーム画面の「もっとみる」→ 取引履歴 → 行タップ → 取引詳細 の導線が iOS で動作する
- [ ] iOS で戻るジェスチャ / 戻るボタンが期待どおり動作する
- [ ] **Kotlin/Native ランタイム重複問題が再発しない**（`:shared.framework` 1 個だけ embed の構成のため構造的に回避できているはず）

## 想定される懸念・リスク

- **`StateFlow` 購読の Swift 側バグ**: 自前 wrapper でメモリリーク / コルーチンキャンセル漏れが起きる可能性。`deinit` で確実にキャンセルすることを徹底し、Instruments で leak チェック
- **Swift 側で参照する Kotlin 型のシンボル名**: `:shared` の Kotlin クラス名が長い / namespace が深い場合、Swift 側で `Shared.HomeUiState.Loaded` のように冗長になる。`typealias` で短縮するか Swift 側でラッパー型を切る
- **Android との見た目の乖離**: Figma の dp / sp と SwiftUI の point は概ね 1:1 だが、フォントレンダリング・行間 (`lineHeight`) で差が出やすい。許容範囲を screenshot 比較で擦り合わせる
- **アセット PDF のサイズ感**: PDF (vector) はレンダリング時にスケールされるため、Figma 上の dp 値で SwiftUI の `frame(width:height:)` を指定する。誤差が出たら個別調整
- **NavigationStack の戻る挙動**: iOS 16+ は `NavigationStack` だが iOS 15 以下サポートが必要なら `NavigationView` フォールバックが必要。iOS 17+ 限定で割り切るのが楽
- **TabView の `.tabItem` カスタム画像**: Figma の home / account アイコンを `.tabItem(image:)` でそのまま使うと SwiftUI の自動 tinting で色が変わる。`renderingMode(.template)` で挙動制御する
- **`:shared` の `viewModelScope`**: `:shared/commonMain` の `ViewModel` は `androidx.lifecycle.ViewModel` を継承しているため、iOS 側で lifecycle を意識する必要あり。SwiftUI の `@StateObject` の lifetime に合わせて手動で `clear()` するか、KMP の `ViewModel` の expect/actual 化を検討

## 参考リンク

- Figma ホーム画面: https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=709-8658&m=dev
- Figma 取引履歴: https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=697-7601&m=dev
- Figma 取引詳細: https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=702-6440&m=dev
- 前提タスク 1 (revert 済み、失敗経緯記録): [`client-ios-1-compose-multiplatform-foundation.md`](./client-ios-1-compose-multiplatform-foundation.md)
- 前提タスク 2 (Android 先行 Figma 適用): [`client-bank-2-home-screen-figma-redesign-android.md`](./client-bank-2-home-screen-figma-redesign-android.md)
- Android 実装 (Task 2 完了後の Figma 準拠版): `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/`
- 既存 iOS エントリ: `iosApp/iosApp/iOSApp.swift`
- KMP-NativeCoroutines (使う場合): https://github.com/rickclephas/KMP-NativeCoroutines
- Compose Multiplatform & SwiftUI 連携ガイド (本タスクでは不採用、参考のみ): https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform-and-swiftui-integration.html
