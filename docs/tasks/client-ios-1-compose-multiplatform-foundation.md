# 銀行アプリクライアント：iOS ターゲット追加 + commonMain 化基盤 + iosApp からの Compose 起動経路

## 概要

`composeApp` モジュールに iOS ターゲット（`iosArm64` / `iosSimulatorArm64`）を追加し、Android 専用に書かれている UI レイヤーを `commonMain` に移植可能な状態にする。`iosApp` から Compose Multiplatform UI を起動できる経路（`MainViewController`）を整備する。本タスクは「ホーム画面の commonMain 移植 (Task 2)」の前提となる基盤整備タスクであり、画面の見た目変更は含まない。

## 背景・目的

- 現状 `composeApp` は `androidTarget()` のみを宣言しており、`HomeScreen` を含む全 UI が `androidMain` に存在する。iOS 側は `iosApp/` 配下で SwiftUI 画面を個別に実装しており、UI 仕様の二重メンテが発生している。
- Figma 新デザイン（`709-8658` ホーム / `697-7601` / `702-6440`）を Android / iOS 両方に適用するにあたり、画面ごとに Swift 実装を起こすのは現実的ではない。Compose Multiplatform で commonMain 化する方針に揃える。
- まず本タスクで「iOS で Compose UI が起動するところまで」を確立し、画面単位の移植は後続タスク（Task 2 以降）で個別に進められるようにする。

## スコープ

- `composeApp/build.gradle.kts` に `iosArm64()` / `iosSimulatorArm64()` ターゲットと framework 設定を追加
- `composeApp` 用の `iosMain` / `commonMain` ソースセットの新設と依存追加（compose runtime 等は既に commonMain にあるため iOS 向けランタイムを通す）
- `App()` Composable の `commonMain` 化（Android 固有 import: `BuildConfig.DEBUG` / `SystemClock` / `androidx.lifecycle.viewmodel.compose.viewModel` 等の expect/actual 切り出し or 引数化）
- `MainKt`（仮）相当の `MainViewController()` を `iosMain` に追加し、`ComposeUIViewController { App() }` を返す
- `iosApp/iosApp/iOSApp.swift` の起動経路を「Compose `MainViewController()` を SwiftUI に埋め込んで表示できる」状態にする（既存 `SplashGate` / SwiftUI 画面群はそのまま温存し、フラグ or 別 Scene として切替可能にする）
- iOS 側の Koin 初期化が `composeApp` 経由でも問題なく動くことを確認

### アウトオブスコープ

- 個別画面（HomeScreen / Login / Signup / Splash 等）の commonMain 移植 → Task 2 以降で順次
- Figma 新デザインの適用 → Task 2 以降
- Navigation ライブラリ（Voyager / Decompose / Compose Navigation Multiplatform）の本格導入 → 別タスクで切り出し
- iOS 既存 SwiftUI 画面（`LoginView` 等）の削除 → 移植完了時に別タスクで実施

## 影響範囲

- モジュール: `composeApp` / `iosApp`
- ソースセット:
  - 新設: `composeApp/src/commonMain/kotlin/`, `composeApp/src/iosMain/kotlin/`
  - 移動 or 共有化: `App.kt` および周辺ファイル（depend の少ないもののみ。詳細はステップ参照）
- 破壊的変更: `composeApp` の framework が iOS 側にリンクされるようになる。`Shared` framework との衝突がないか要確認（`composeApp` は別 framework として export する）
- 追加依存:
  - `gradle/libs.versions.toml` への新規追加は基本的に不要（compose runtime / foundation / material3 は既に commonMain で参照済み）
  - 必要に応じて `compose-ui-uikit`（Compose Multiplatform 1.10 系では `org.jetbrains.compose.ui:ui` に同梱されているはず）の追加可否を実装時に確認

## 技術アプローチ

### `composeApp` の iOS ターゲット追加

`shared/build.gradle.kts` の iOS ターゲット宣言（`iosArm64()` / `iosSimulatorArm64()` + `binaries.framework { baseName = "Shared"; isStatic = true }`）と同じパターンを `composeApp/build.gradle.kts` にも追加する。framework 名は `ComposeApp` を想定。

### `commonMain` 化方針

- 純 Compose な UI コンポーネント（`features/welcome/WelcomeScreen.kt` 等の依存が薄いもの）から順に `commonMain` へ移動する。本タスクでは `App.kt` のスケルトンのみ commonMain 化し、各画面 Composable は androidMain に残置 → Task 2 以降で個別移植する戦略を取る。
- Android 固有 API は以下の方針で抽象化する:
  - `BuildConfig.DEBUG` → `expect val isDebugBuild: Boolean` を新設し、`androidMain` で `BuildConfig.DEBUG` を返す / `iosMain` で `Platform.isDebugBinary` 相当 or BuildKonfig flavor を返す
  - `android.os.SystemClock.elapsedRealtime()` → `kotlinx.datetime.Clock` または `kotlin.time.TimeSource.Monotonic` に置換（commonMain で完結）
  - `androidx.lifecycle.viewmodel.compose.viewModel` → 既に commonMain に `lifecycle-viewmodel-compose` (Multiplatform 版) を入れているため commonMain でそのまま使える想定。要動作確認
  - `androidx.lifecycle.compose.collectAsStateWithLifecycle` → 同上（`lifecycle-runtime-compose` の Multiplatform 版）
- 本タスクではまず `App()` を「ロジックは androidMain 側に残し、commonMain 側に薄いエントリポイント `MainApp()` を作って androidMain / iosMain から呼び出す」中間状態を許容する。完全な commonMain 化は画面移植と同時に進める。

### `iosMain` のエントリポイント

```
// composeApp/src/iosMain/kotlin/studio/nxtech/fujubank/MainViewController.kt
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
```

- `KoinIosKt.doInitKoin()` は引き続き `iosApp/iOSApp.swift` の `init()` で呼ばれる前提（重複初期化防止のため `composeApp` 側では Koin init しない）。

### `iosApp` の切替

- 既存 `SplashGate` SwiftUI 経路は壊さない。`iOSApp.swift` に「Compose 経路を起動するデバッグスイッチ or ビルドフラグ」を追加し、Compose 経路の動作確認ができるようにする。Task 2 完了後にデフォルト切替する。
- `iosApp.xcodeproj` の framework search path / `embedAndSignAppleFrameworkForXcode` 経路に `composeApp` の framework が含まれるよう Xcode 側設定を更新する必要がある可能性 → 実装時に検証。

## 実装手順

1. `composeApp/build.gradle.kts` に `iosArm64()` / `iosSimulatorArm64()` を追加し、`binaries.framework { baseName = "ComposeApp"; isStatic = true }` を設定する。
2. `composeApp/src/commonMain/kotlin/` 配下に空のパッケージディレクトリを作成し、ビルドが通ることを確認 (`./gradlew :composeApp:compileKotlinIosSimulatorArm64`)。
3. `expect val isDebugBuild: Boolean` を `commonMain` に定義し、`androidMain` で `BuildConfig.DEBUG`、`iosMain` で `BuildKonfig` の flavor 判定 or 暫定 `true` を返す actual を実装する。
4. `App.kt` 内の `SystemClock.elapsedRealtime()` を `TimeSource.Monotonic.markNow()` ベースに置換する（Android 側の挙動が変わらないことを既存ビルドで確認）。
5. `App.kt` を `commonMain` に移動 — ただし画面 Composable（LoginScreen 等）の参照は androidMain に残ったままで OK。`App()` 内で参照する画面群を「androidMain にある実装を呼ぶ」状態のまま、import 解決のために `expect @Composable fun PlatformScreens(...)` のような薄い抽象を一時的に挟むか、まずは `App()` を androidMain に残して `MainApp()` という commonMain エントリーから呼び出すラッパーを作る形を取る（実装時に楽な方を選択）。
6. `composeApp/src/iosMain/kotlin/studio/nxtech/fujubank/MainViewController.kt` を新設し、`ComposeUIViewController { /* 暫定: シンプルな "Hello iOS" Composable */ }` を返す関数を作る。まず最小構成で iOS ビルドを通す。
7. `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` が通ることを確認。
8. `iosApp/iosApp/` 側に Compose 起動用の SwiftUI ラッパー（`ComposeView: UIViewControllerRepresentable`）を追加し、`iOSApp.swift` から「環境変数 or デバッグメニューで Compose 経路に切替可能」にする。
9. Xcode で iOS Simulator (Arm64) を起動し、`MainViewController()` 経由で「Hello iOS」Composable が描画されることを確認。
10. `App()` 本体を commonMain 化（手順 5 のラッパーを本物の `App()` に切替）。Android ビルド + iOS Simulator 両方で起動確認。
11. CI/`build.gradle.kts` の release flavor 切替が iOS framework link でも維持されていることを `./gradlew :composeApp:linkReleaseFrameworkIosSimulatorArm64` で確認。

## 完了条件

- [ ] `./gradlew build` が通る
- [ ] `./gradlew :composeApp:assembleDebug` が通り、Android 実機/エミュレータで従来通り起動する（既存の Splash → Login / Home フロー無変化）
- [ ] `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` が通る
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が引き続き通る（既存 iOS ビルドへの regression なし）
- [ ] iOS Simulator (Arm64) で `iosApp` を起動し、Compose 経路に切替えると `App()`（または暫定 Hello Composable）が描画される
- [ ] iOS Simulator で従来の SwiftUI 経路（`SplashGate`）も引き続き動作する
- [ ] release flavor の iOS framework link (`linkReleaseFrameworkIosSimulatorArm64`) が通る

## 想定される懸念・リスク

- **`Shared` framework と `ComposeApp` framework の二重リンク**: `iosApp` 側で両方の framework を embed する構成になる。Xcode の Build Phases で `embedAndSignAppleFrameworkForXcode` を 2 回（shared 用 / composeApp 用）走らせる必要があるかもしれない。
- **lifecycle-viewmodel-compose の iOS 互換性**: バージョン 2.10.0 (`androidx-lifecycle = "2.10.0"`) は org.jetbrains.androidx.lifecycle 系の Multiplatform 版を入れているはずだが、iOS で `viewModel { ... }` が想定通り動くかは要検証。動かない場合は ViewModel をやめて `remember { HomeViewModel(...) }` パターンに切り替える代替案を持っておく。
- **Koin の `KoinPlatform.getKoin()`**: `iosApp` 側の `KoinIosKt.doInitKoin()` で初期化された Koin インスタンスを Compose 側 (`MainViewController`) からも参照できることを確認する。`composeApp` で再 `initKoin` してしまうと「すでに開始されている」エラーになるので注意。
- **BuildKonfig flavor の伝播**: `composeApp` 側に新規 BuildKonfig を入れる必要は基本ないが、`BuildConfig.DEBUG` 相当を iOS で取りたい場合は `shared` の BuildKonfig をそのまま参照する（重複定義を避ける）。
- **iOS 側 SwiftUI 画面群の扱い**: 本タスクでは温存する方針だが、Compose 経路がデフォルトになるタイミング（Task 2 以降）で削除タスクを別途切る必要がある。

## 参考リンク

- Figma ホーム画面: https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/?node-id=709-8658 ※Task 2 で適用
- Figma 関連画面: 697-7601, 702-6440（後続タスク）
- 既存 Android 実装: `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/App.kt`
- 既存 iOS エントリ: `iosApp/iosApp/iOSApp.swift`
- Compose Multiplatform iOS ガイド: https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform-and-swiftui-integration.html

---

## 失敗経緯と方針変更（2026-05-03 追記）

### 結論

本タスクは `feature/client-ios-1-compose-multiplatform-foundation` ブランチで実装したが、**iOS 起動不能**となったため revert した。原因は KMP プロジェクトに Kotlin/Native framework を 2 つ（`Shared.framework` と `ComposeApp.framework`）静的リンクで同居させたことによる構造的問題であり、Task 1 のスコープ内の局所的バグではない。framework 統合戦略の再設計が必要であるため、後続タスク（Task 3）に持ち越す。

### 何をやって何が起きたか

実装した内容（revert 済み）:

- `composeApp/build.gradle.kts` に `iosArm64()` / `iosSimulatorArm64()` ターゲットを追加し、`binaries.framework { baseName = "ComposeApp"; isStatic = true }` を設定
- `composeApp/src/iosMain/kotlin/.../MainViewController.kt` を新設し `ComposeUIViewController { App() }` を返す関数を実装
- `iosApp/iosApp/iOSApp.swift` から環境変数フラグで Compose 経路に切替可能にする SwiftUI ラッパーを追加
- 既存の `Shared.framework` (isStatic = true) はそのまま温存し、`ComposeApp.framework` も並行して embed する構成

発生した症状:

- ビルド自体は通る（ld の duplicate symbol は **warning** 止まりで error にならない）
- ただし iOS Simulator で起動すると、`SplashGate` のブートストラップ（Koin 経由のセッション復元 → ホームへの遷移判定）が完了せず、スプラッシュ画面で**永久停止**
- ld のログには `_Kotlin_*` / `_ktypew:*` 等の Kotlin/Native ランタイムシンボルの重複警告が大量に出力されていた

### 根本原因

Kotlin/Native の静的 framework (`isStatic = true`) は、その framework 内に **Kotlin/Native ランタイム（GC、型情報テーブル `_ktypew:*`、ランタイムサポート関数 `_Kotlin_*` 等）を内包**してビルドされる。これにより:

1. `Shared.framework` と `ComposeApp.framework` の両方に同じ Kotlin/Native ランタイム実装が含まれる
2. iOS アプリにこれらを 2 つ同時に embed すると、リンク時にランタイムシンボルが重複する（ld は warning として通すが、実行時には**どちらの実装が使われるかが未定義**）
3. 実行時に Kotlin/Native ランタイムの内部状態が **2 つに分裂**し、片方の framework で `initKoin` した Koin コンテナをもう片方から参照できない / GC やオブジェクト識別が破綻する等の問題が発生
4. 結果として Koin のシングルトン解決が両 framework 間で食い違い、`SplashGate` が依存性を解決できず無限待機状態になる

これは 2 つの framework のどちらかにバグがあるわけではなく、**「複数の Kotlin/Native 静的 framework を 1 つの iOS アプリに同居させる構成そのもの」が KMP の制約に反している**ことが原因である。Kotlin/Native の公式ドキュメントでも「同一プロセスに複数の Kotlin framework を持つことは推奨されない」旨が記載されている。

### 対応

- `feature/client-ios-1-compose-multiplatform-foundation` ブランチでの変更を **revert**（main には未マージ）
- 現状の main は Task 1 着手前の状態（`composeApp` は Android 専用、iOS は SwiftUI のみ）に戻っている

### 後続タスクへの引継ぎ

方針を「KMP は iOS/Android 両対応必須」を維持しつつ、**Android 先行 → 後続で iOS** の段階的アプローチに変更:

- **Task 2 (Android 先行)**: [`client-bank-2-home-screen-figma-redesign-android.md`](./client-bank-2-home-screen-figma-redesign-android.md)
  - スコープを「Figma `709-8658` のデザイン適用」に絞り、`composeApp/src/androidMain/.../features/home/HomeScreen.kt` の改修のみで完結させる
  - commonMain 移植や iOS 対応は含めない。最短で Figma 反映の体感を得ることを優先
- **Task 3 (iOS 化対応)**: [`client-bank-3-ios-multiplatform-integration.md`](./client-bank-3-ios-multiplatform-integration.md)
  - **framework 統合戦略の再設計**を含む。最低でも以下 3 案を比較検討する:
    - 案A: CMP UI を `:shared` に取り込む（framework は 1 つに統合）
    - 案B: `:composeApp` から `:shared` を依存し、`ComposeApp.framework` だけを iOS に embed して `Shared` の API を再エクスポートする
    - 案C: `isStatic = false`（dynamic framework）化で延命する（runtime 二重ロードリスクは残る）
  - そのうえでホーム画面の commonMain 移植 + iOS 動作確認まで実施

### 教訓

- **KMP で複数の Kotlin framework を同一 iOS アプリに同居させる場合、事前に framework 統合戦略を決定する必要がある**。`:shared` と `:composeApp` のように Kotlin/Native ターゲットを持つモジュールが複数あるとき、それぞれを独立した framework として export する素朴な構成は、ランタイム重複により実行時に破綻する。
- ld の duplicate symbol が **warning 止まりでビルドが通ってしまう** ため、CI / ローカルビルドだけでは問題に気付けない。実機 / Simulator 起動と Koin 等のシングルトン挙動を含む実行時検証が必須。
- 段階的アプローチ（まず Android で見た目を確定 → そのあと iOS 化）の方が、framework 統合戦略の検討時間を確保しつつ、Figma 適用の成果は早く得られる。仕様変更や設計検討と実装作業を直列化せず並行できる構造を優先する。
