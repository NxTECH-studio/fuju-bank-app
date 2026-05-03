# 銀行アプリクライアント：iOS Multiplatform 統合（framework 戦略再設計 + ホーム画面 commonMain 移植 + iOS 動作確認）

## 概要

Task 1 で発生した「`Shared.framework` と `ComposeApp.framework` の二重リンクによる Kotlin/Native ランタイム重複で iOS 起動不能」という構造的問題を解決するため、**KMP の framework 統合戦略を再設計**する。そのうえで Task 2 で Figma 準拠に確定した Android ホーム画面を commonMain に移植し、iOS Simulator で同等の見た目・挙動が得られるところまで持っていく。

## 背景・目的

### 経緯

- 当初の計画では Task 1 (`client-ios-1-compose-multiplatform-foundation`) で `composeApp` に iOS ターゲットを追加して Compose 起動経路を作り、Task 2 でホーム画面を commonMain 移植する想定だった
- Task 1 を実装した結果、`Shared.framework`（既存）と `ComposeApp.framework`（新規追加）の両方を `isStatic = true` で 1 つの iOS アプリに embed すると、Kotlin/Native ランタイムシンボル (`_Kotlin_*` / `_ktypew:*`) が重複し、ld の duplicate symbol warning は出るが**ビルドは通る** → 実行時に Koin 等のシングルトン解決が破綻し iOS スプラッシュで永久停止する、という構造的問題が発覚（詳細は [Task 1 計画書末尾](./client-ios-1-compose-multiplatform-foundation.md#失敗経緯と方針変更2026-05-03-追記)）
- Task 1 を revert し、方針を「Android 先行 → 後続で iOS」に変更
- Task 2 (`client-bank-2-home-screen-figma-redesign-android.md`) で Android のみ Figma `709-8658` を適用済み（前提）
- 本タスク (Task 3) で framework 戦略を決定し直し、commonMain 移植 + iOS 対応を完遂する

### 目的

- Kotlin/Native の framework 統合戦略を確定し、iOS 起動不能問題を恒久解決する
- Task 2 で確定した Android ホーム画面を commonMain に移植し、iOS でも同じ Compose 実装で表示する
- 「KMP は iOS/Android 両対応必須」という大方針を満たす

## スコープ

- **framework 統合戦略の決定**（後述の案A/B/Cを比較し推奨案を選定して実装）
- `composeApp` への iOS ターゲット (`iosArm64` / `iosSimulatorArm64`) 追加（戦略に応じて、または `:shared` 側に Compose UI を取り込む形に変更）
- `commonMain` ソースセットの整備と、`App()` を含む基盤 Composable の commonMain 化
- ホーム画面 (`HomeScreen` + 関連 Composable + テーマ + リソース) の commonMain 移植
- iOS 側 SwiftUI 起動経路 (`SplashGate`) からの Compose UI 表示への切替（既存 SwiftUI 経路は当面温存し、フラグ等で切替可能に）
- iOS Simulator (Arm64) での動作確認

### アウトオブスコープ

- Login / Signup / Splash / MFA など他画面の commonMain 移植 → 個別タスク
- Figma `697-7601` / `702-6440` の関連画面適用 → 個別タスク
- iOS 既存 SwiftUI 画面 (`LoginView` 等) の削除 → Compose 経路がデフォルトになったあと別タスクで cleanup
- Navigation ライブラリ (Voyager / Decompose 等) の本格導入 → 別タスクで切り出し
- Android 側のロジック変更（Task 2 で確定した Android 実装をそのまま移植する）

## 着手条件

**Task 2 (`client-bank-2-home-screen-figma-redesign-android.md`) 完了後に着手**。
理由: Task 2 で Android `HomeScreen` を Figma 準拠に確定させてから commonMain に移植する方が、移植中に「デザイン差分の取り込み」と「commonMain 化」を同時にやらずに済み、問題切り分けが容易になるため。

## 影響範囲

- モジュール: `composeApp` / `shared` / `iosApp`（戦略に応じて影響範囲が変わる。後述）
- ソースセット:
  - 戦略確定後に `commonMain` / `iosMain` を新設または再編
  - `composeApp/src/androidMain/.../features/home/` から `commonMain` 配下への移動
  - リソース: `composeApp/src/androidMain/res/drawable/` → `composeResources/drawable/` への移行
- 破壊的変更: framework 構成（`baseName` / `isStatic` / 依存方向）が変わる。Xcode 側 Build Phases の `embedAndSignAppleFrameworkForXcode` 設定を更新する必要あり
- 追加依存:
  - 戦略 A の場合、`:shared` に Compose Multiplatform 関連依存（`compose-runtime` / `compose-ui` / `compose-foundation` / `compose-material3` / `compose-components-resources` 等）を追加
  - 戦略 B の場合、`:composeApp` から `:shared` への依存を整理し、`transitiveExport` / `export()` の設定を追加
  - 戦略 C の場合、依存追加なし（`isStatic` を `false` にするだけ）

## framework 統合戦略：3 案の比較

### 案A: CMP UI を `:shared` に取り込む（framework を 1 つに統合）

**概要**:
- `:composeApp` を Android 専用 (`androidTarget()` のみ) に戻し、Compose Multiplatform UI 実装はすべて `:shared` の `commonMain` / `androidMain` / `iosMain` に置く
- iOS 側は `Shared.framework` だけを embed し、その中の `MainViewController()` を呼ぶ
- `:composeApp` (Android) は `:shared` を依存し、`shared` 内の commonMain Composable を `Activity` から呼び出す

**メリット**:
- framework が 1 つになるので Kotlin/Native ランタイム重複問題が**根本解決**
- Compose Multiplatform 公式テンプレート (KMP Wizard) と同じ構成。サンプル / ドキュメントが豊富
- iOS 側の Xcode 設定変更が最小（既存の `Shared.framework` embed のままで済む）

**デメリット**:
- `:shared` に Compose 依存を入れることになり、現状「`:shared` は Compose に依存しないドメイン層」という分離が崩れる
- `:composeApp/src/androidMain/` にある全 Compose 実装 (`App.kt` / `features/*/` / `theme/*` / `RootScaffold` 等) を `:shared` に移動する大規模リファクタが必要
- Android 側の依存方向が変わる（`composeApp` → `shared` に Compose 経由の依存が増える）。既存の `composeApp` 固有の Android 設定（`AndroidManifest.xml` / `Activity` / lifecycle / Hilt 等があれば）との切り分けが必要

**実装規模**: 大（`:composeApp/androidMain` の Compose 実装を全部 `:shared` に引っ越し）

### 案B: `:composeApp` から `:shared` を依存し、`ComposeApp.framework` だけを iOS に embed（再エクスポート）

**概要**:
- `:composeApp` に iOS ターゲットを追加し、`:shared` を `api()` または `export()` で依存
- iOS 側は `ComposeApp.framework` **だけ**を embed する。`Shared.framework` は単独 embed をやめ、`ComposeApp.framework` から `transitiveExport = true` または明示的 `export(project(":shared"))` で再エクスポートして Swift から `:shared` の API を見えるようにする
- `:shared` 単体の `binaries.framework { ... }` 宣言は維持してもよい（テスト用 / 単独利用用）が、iOS アプリ本体には embed しない

**メリット**:
- `:shared` の純粋なドメイン層という性格を維持できる（Compose 依存は `:composeApp` だけ）
- framework が 1 つだけ embed されるので Kotlin/Native ランタイム重複問題を回避
- `:composeApp/androidMain` の Compose 実装を移動せずに済む（`:composeApp/commonMain` に commonMain 化していくだけ）

**デメリット**:
- `transitiveExport` / `export()` の設定が複雑。`:shared` の公開 API が Swift から `ComposeAppKt` 経由で見えるようになるため、Swift 側の import 文が変わる可能性 (`import Shared` → `import ComposeApp`)
- `iosApp/iOSApp.swift` の `KoinIosKt.doInitKoin()` 等の呼び出しシンボルが framework 名変更で壊れる可能性。Swift 側を全面的に書き換える必要があるかもしれない
- Xcode の Build Phases で `embedAndSignAppleFrameworkForXcode` のターゲット framework を `:shared` から `:composeApp` に変更する必要あり

**実装規模**: 中（Gradle 設定 + Xcode Build Phases + Swift 側 import 変更）

### 案C: dynamic framework (`isStatic = false`) 化で延命する

**概要**:
- `:shared` と `:composeApp` の両方の framework を `isStatic = false`（dynamic framework）にする
- dynamic framework は実行時にシンボルを動的解決するため、ld の duplicate symbol warning が抑制される

**メリット**:
- Gradle 設定 1 行（`isStatic = false`）の変更だけで済む
- 既存の framework 構成（`:shared` と `:composeApp` を独立 embed）を維持できる

**デメリット**:
- **Kotlin/Native ランタイムが両 framework の中に依然として存在**するため、実行時に 2 つのランタイムインスタンスがロードされるリスクは残る。Koin のシングルトン解決問題が dynamic 化で解消される保証はない（ld 警告が消えるだけで、runtime レベルの分裂は同じ）
- Compose Multiplatform 公式は iOS 向けに static framework を推奨しており、dynamic framework は **本番環境での実績が薄い**
- アプリ起動時間 / バイナリサイズの増加リスク
- 本番でも問題が起きた場合、案A or 案B にやり直しになるため二度手間

**実装規模**: 小（ただし問題が再発した場合のリカバリーコストは大）

### 推奨案：**案B**

**理由**:
1. `:shared` の「Compose に依存しないドメイン層」という性格を維持できる（既存アーキテクチャの破壊が最小）
2. `:composeApp/androidMain` の Compose 実装を物理移動せずに、commonMain 化を段階的に進められる（Task 2 で確定した Android `HomeScreen` をそのまま `commonMain` に上げるだけ）
3. framework が 1 つだけ embed されるので Kotlin/Native ランタイム重複問題を**根本解決**できる
4. 案A は大規模リファクタが必要、案C は問題再発リスクが残るため、案B が最もリスクと工数のバランスが良い

**懸念点と対策**:
- `transitiveExport` の設定とインクリメンタルビルドの相性が悪いケースがあるため、最初は `export(project(":shared"))` で明示的に export する
- Swift 側の `import Shared` を `import ComposeApp` に変更する必要があるなら、`iosApp/` の Swift ファイル全体に sed で一括置換 → ビルド確認 → 必要箇所だけ修正するアプローチを取る
- 万一案Bで詰まった場合のフォールバックは案A（CMP UI を `:shared` に取り込む大規模リファクタ）。案Cは実行時リスクが残るためフォールバックには採用しない

## 技術アプローチ（推奨案B 前提）

### Gradle 設定変更

`composeApp/build.gradle.kts`:
```kotlin
kotlin {
    androidTarget()
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            export(project(":shared"))
            // 必要に応じて Koin / Ktor 等の Swift から見たい依存も export
        }
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":shared"))
            // compose-runtime / foundation / material3 / resources 等は既存
        }
        // ...
    }
}
```

`shared/build.gradle.kts`:
- 既存の `binaries.framework { baseName = "Shared"; isStatic = true }` 宣言は**残してもよい**（`:shared:linkDebugFrameworkIosSimulatorArm64` で単体ビルドできる状態を維持）が、iOS アプリには embed しない

### Xcode 側変更

- `iosApp/iosApp.xcodeproj` の Build Phases:
  - `embedAndSignAppleFrameworkForXcode` で embed する framework を `:shared` から `:composeApp` に変更
  - `FRAMEWORK_SEARCH_PATHS` を `composeApp/build/xcode-frameworks/` に向ける
- Swift 側 import 文:
  - `import Shared` → `import ComposeApp` に置換（`:shared` の公開クラスは `export(project(":shared"))` で再エクスポートされるため `ComposeApp` モジュール経由で参照可能）

### commonMain 化

- `expect val isDebugBuild: Boolean` を `commonMain` に定義し、`androidMain` で `BuildConfig.DEBUG` / `iosMain` で適切な値（`Platform.isDebugBinary` または BuildKonfig）を返す
- `android.os.SystemClock.elapsedRealtime()` を `kotlin.time.TimeSource.Monotonic.markNow()` に置換
- `androidx.lifecycle.viewmodel.compose.viewModel` / `collectAsStateWithLifecycle` は Multiplatform 版 (`org.jetbrains.androidx.lifecycle:*`) で commonMain 対応している想定。動かない場合のフォールバック: `viewModel { }` → `remember { HomeViewModel(...) }`、`collectAsStateWithLifecycle` → `collectAsState`
- `App.kt` を commonMain に移し、画面参照は中間ラッパー (`expect @Composable fun PlatformScreens(...)` または `MainApp()` という commonMain エントリ) を介して段階的に移植

### ホーム画面の commonMain 移植

Task 2 で Figma 準拠に確定した `composeApp/src/androidMain/.../features/home/` 配下を `commonMain` に移動:

1. `theme/FujuBankColors.kt` / `FujuBankTypography.kt` を `commonMain` に移動
2. `features/home/components/` 配下 (`BalanceCard` / `ActionTiles` / `FujuBankHeader`) を 1 ファイルずつ `commonMain` に移動
3. `HomeViewModel` / `HomeUiState` を `commonMain` に移動
4. `HomeScreen.kt` 本体を `commonMain` に移動
5. Android 固有 import を Multiplatform 版に置換:
   - `androidx.compose.ui.tooling.preview.Preview` → `org.jetbrains.compose.ui.tooling.preview.Preview`
   - `androidx.compose.ui.res.painterResource` → Compose Resources (`org.jetbrains.compose.resources.painterResource`)
6. リソース移行:
   - `composeApp/src/androidMain/res/drawable/*.xml` (Vector Drawable) を SVG に変換し `composeApp/src/commonMain/composeResources/drawable/` に配置
   - 文字列リソース (`R.string.*`) は本画面で参照していなければハードコードのまま、参照があれば Compose Resources の `Res.string.*` に移行

### iOS からの到達経路

- `composeApp/src/iosMain/kotlin/.../MainViewController.kt` を新設し `ComposeUIViewController { App() }` を返す
- `iosApp/iosApp/iOSApp.swift` で Compose 経路を起動するデバッグスイッチ or ビルドフラグを追加（既存 `SplashGate` SwiftUI 経路は温存）
- Koin は `iosApp` の `init()` で `KoinIosKt.doInitKoin()` を呼ぶ前提を維持。`composeApp` 側で再 `initKoin` しない（重複初期化防止）

## 実装手順

1. **framework 戦略の最終決定**: 本計画書の案A/B/C 比較を再確認し、案B で進める判断を確定する（実装中に詰まった場合は案A にフォールバック）
2. **Gradle 設定変更**: `composeApp/build.gradle.kts` に iOS ターゲット追加 + `export(project(":shared"))` 設定
3. **`shared` の framework embed を Xcode から外す準備**: ビルド可能状態を維持しつつ `composeApp` の framework が出力されることを確認 (`./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64`)
4. **Xcode Build Phases 更新**: `embedAndSignAppleFrameworkForXcode` のターゲットを `:composeApp` に変更
5. **Swift 側 import 変更**: `iosApp/` 配下の `import Shared` を `import ComposeApp` に置換し、ビルド (`xcodebuild`) を通す
6. **iOS Simulator 起動確認 (最小構成)**: `MainViewController()` が「Hello iOS」Composable を返す状態で iOS Simulator から起動し、SwiftUI 既存経路 (`SplashGate`) と Compose 経路の切替が動作することを確認。**ここで Koin 二重初期化等の runtime 問題が出ないことを確認するのが最重要マイルストーン**
7. **commonMain 化基盤**: `expect val isDebugBuild` / `SystemClock` 置換 / `App()` の commonMain 化（中間ラッパー方式可）
8. **テーマの commonMain 化**: `theme/` 配下を移動
9. **ホーム画面コンポーネントの commonMain 化**: `BalanceCard` / `ActionTiles` / `FujuBankHeader` を 1 ファイルずつ移動、各移動でビルド確認
10. **`HomeViewModel` / `HomeUiState` の commonMain 化**
11. **`HomeScreen.kt` 本体の commonMain 化**
12. **リソース移行**: Vector Drawable を SVG に変換し `composeResources/drawable/` に配置
13. **iOS からホーム画面への到達経路を整備**: `App()` 経由で `RootScaffold` → `HomeScreen` まで辿れるようにする。重い場合は暫定で `MainViewController()` から直接 `HomeScreen` を表示するデバッグ経路を追加
14. **iOS Simulator (Arm64) で動作確認**: Android 側と同等の見た目になっていることを screenshot 比較で確認
15. **`useDummyProfile=true` でバックエンド未起動でも HomeScreen が動くことを Android / iOS 両方で確認**
16. **release flavor の iOS framework link 確認**: `./gradlew :composeApp:linkReleaseFrameworkIosSimulatorArm64`

## 完了条件

- [ ] framework 統合戦略が決定され、計画書内で選定理由が文書化されている（本計画書の「推奨案」セクション）
- [ ] `./gradlew build` が通る
- [ ] `./gradlew :composeApp:assembleDebug` が通り、Android 実機/エミュレータで Task 2 と同等のホーム画面が表示される（regression なし）
- [ ] `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` が通る
- [ ] `./gradlew :composeApp:linkReleaseFrameworkIosSimulatorArm64` が通る
- [ ] iOS Simulator (Arm64) で `iosApp` を起動し、Compose 経路でホーム画面が Android と同等のレイアウト・色・余白で表示される
- [ ] iOS Simulator で従来の SwiftUI 経路（`SplashGate`）も引き続き動作する（切替可能な状態）
- [ ] **Koin の二重初期化 / シングルトン解決破綻が iOS で発生しない**（Task 1 の失敗の再発がない）
- [ ] `useDummyProfile=true` でバックエンド未起動でもホーム画面が起動する（Android / iOS 両方）
- [ ] 残高マスク表示トグル / 取引履歴遷移コールバック / 各アクションタイル押下のハンドラが Android / iOS 両方で動作する

## 想定される懸念・リスク

- **案B の `transitiveExport` 設定**: `export(project(":shared"))` の設定でも Swift から見える API に欠落が出る可能性。最悪 `:shared` の主要型を `:composeApp/iosMain` で typealias して export する追加対応が必要
- **Xcode 側の framework 切替**: `embedAndSignAppleFrameworkForXcode` のターゲット framework 変更を Xcode プロジェクトファイル (`project.pbxproj`) で正しく行わないとビルドが通らない。手作業で慎重に
- **Swift 側 import 文の機械的置換による副作用**: `import Shared` を `import ComposeApp` に置換する際、コメント等に `Shared` という単語が含まれていると誤置換のリスク。git diff で慎重にレビュー
- **Koin の二重初期化**: `composeApp` 側で `initKoin` を呼ばない (iOS は `iosApp` の `init()` で `KoinIosKt.doInitKoin()` のみ) ことを徹底。Task 1 で失敗した部分なので最優先で確認
- **lifecycle-viewmodel-compose の iOS 互換性**: `viewModel { ... }` が iOS で動かない場合は `remember { HomeViewModel(...) }` パターンに切り替え
- **`collectAsStateWithLifecycle` の commonMain 動作**: 動かなければ `collectAsState` にフォールバック（Lifecycle aware ではなくなるトレードオフあり）
- **Vector Drawable → SVG 変換**: Compose Resources では Vector Drawable XML が使えないため SVG 変換が必要。Figma から直接書き出し直すのが楽
- **`qrose` の iOS サポート**: `BarcodeCard` / QR 表示で `qrose` を使用しているが、iOS で正しく描画されるか実機確認。動かない場合は `expect`/`actual` で iOS 用代替実装を入れる
- **iOS の SafeArea**: `Modifier.safeContentPadding()` / `WindowInsets.safeContent` を使って Android との差分を吸収
- **案B でも runtime 問題が再発した場合**: 案A（CMP UI を `:shared` に取り込む大規模リファクタ）にフォールバック。本計画書の「推奨案」セクションに記録した判断を見直し、再計画する

## 参考リンク

- Figma ホーム画面: https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=709-8658&m=dev
- 前提タスク 1 (revert 済み、失敗経緯記録): [`client-ios-1-compose-multiplatform-foundation.md`](./client-ios-1-compose-multiplatform-foundation.md)
- 前提タスク 2 (Android 先行 Figma 適用): [`client-bank-2-home-screen-figma-redesign-android.md`](./client-bank-2-home-screen-figma-redesign-android.md)
- 既存 Android 実装 (Task 2 完了後の Figma 準拠版): `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/home/`
- 既存 iOS エントリ: `iosApp/iosApp/iOSApp.swift`
- Compose Multiplatform iOS ガイド: https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform-and-swiftui-integration.html
- Compose Multiplatform Resources ガイド: https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-multiplatform-resources.html
- Kotlin/Native multiple frameworks (公式 issue / ガイドライン): 実装着手時に最新情報を要確認
