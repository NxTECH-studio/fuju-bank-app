# レビューパターン集（KMP）

Kotlin Multiplatform / Compose Multiplatform プロジェクトで特に注意するレビュー観点。
汎用観点は残しつつ、KMP 固有の罠を厚めに列挙している。プロジェクト固有の学びはこのファイルに追記して蓄積する。

## KMP ソースセット / expect-actual

- `commonMain` に Android SDK（`android.*`）や Foundation/UIKit 依存を混入させていないか
- プラットフォーム固有処理は `expect` 宣言で抽象化し、`androidMain` / `iosMain` の `actual` で実装しているか
- `expect` / `actual` のシグネチャが完全一致しているか（visibility / 型引数 / デフォルト引数含む）
- `commonMain` のテストは `commonTest` に、プラットフォーム依存テストは `androidUnitTest` / `iosTest` に置かれているか
- `shared` モジュールが不必要に UI ライブラリに依存していないか（UI は `composeApp` 側）

## Compose Multiplatform

- `@Composable` 関数が副作用を持っていないか（副作用は `LaunchedEffect` / `rememberCoroutineScope`）
- `remember` / `rememberSaveable` の使い分けが妥当か（構成変更をまたぐ状態は Saveable）
- 再コンポーズ安全性: `@Stable` / `@Immutable` の付与、`State<T>` の読み方が不要な再コンポーズを招いていないか
- ラムダのキャプチャが毎回新しいインスタンスを生成して再コンポーズを誘発していないか（`remember { { ... } }`）
- `Modifier` の順序・再利用の観点で問題がないか
- プレビューは `androidMain`（`@Preview`）／共通側（`@Preview` from compose-multiplatform）で適切に用意されているか

## コルーチン・並行性

- `GlobalScope` を使っていないか（`viewModelScope` / `rememberCoroutineScope` / 明示的な `CoroutineScope` を使う）
- `Dispatchers.Main` / `Dispatchers.IO` / `Dispatchers.Default` の使い分けが妥当か（iOS では `Dispatchers.Main` は `Main.immediate` 相当に注意）
- 構造化並行性が崩れていないか（`supervisorScope` / `coroutineScope` の選択、例外の伝播）
- `StateFlow` / `SharedFlow` の購読でリーク・重複購読が発生していないか
- キャンセルに対する後片付け（`finally` / `invokeOnCompletion`）が行われているか

## iOS 連携（Kotlin/Native）

- `shared` の公開 API が Swift から扱いやすいか（Kotlin の `sealed class` / `data class` は Swift 側ではオブジェクト階層になる）
- suspend 関数を iOS 側から呼ぶ設計になっていないか（Swift から直接は呼べない。`Flow`/コールバック/`NativeCoroutines` 等のラッパーが必要）
- `Throws` アノテーションが必要な例外に付いているか（付かないと Swift 側で `NSError` に変換されない）
- `Shared` フレームワーク名 / `baseName` の変更が iOS プロジェクト側と整合しているか

## Gradle / ビルド

- 依存は `gradle/libs.versions.toml`（Version Catalog）経由で追加されているか（直書き禁止）
- `commonMain.dependencies` と `androidMain.dependencies` / `iosMain.dependencies` の置き場所が正しいか
- 新規プラグインが `settings.gradle.kts` の `pluginManagement` と整合しているか
- 生成物（`build/`）や `local.properties` がコミットされていないか

## 設計・責務分離

- UI (`composeApp`) / ドメイン（`shared`）/ プラットフォーム実装（`androidMain`/`iosMain`）の境界が曖昧になっていないか
- ViewModel / State Holder の責務が肥大化していないか
- 公開 API（`public` / `internal` の使い分け）が最小限か

## エラーハンドリング

- 例外を握り潰していないか（空 `catch`、Result を握り潰す）
- ユーザーに見せるエラーと内部ログで残すエラーが区別されているか
- ネットワーク / I/O 失敗時のリトライ・タイムアウト・フォールバック方針

## リソース管理

- コルーチン / `Flow` の購読 / `DisposableEffect` のクリーンアップ漏れ
- `Closeable` (ファイル / `Ktor Client` 等) の解放

## セキュリティ

- APIキー・シークレットがソース / `local.properties` 以外にハードコードされていないか
- 外部入力（ユーザー / API / ディープリンク）のバリデーション
- Android: `WebView` の `loadUrl` に信頼できない入力を渡していないか
- iOS: `NSURL` / `URLSession` に外部入力を直接渡していないか
- 認可チェックの抜け漏れ

## 型・静的検査

- `Any` / `Nothing?` / プラットフォーム型 (`String!`) を安易に使っていないか
- `!!` によるヌル強制を多用していないか（`requireNotNull` / `checkNotNull` / スマートキャスト）
- `sealed class` / `sealed interface` で網羅すべき `when` が `else` に逃げていないか

## 命名・可読性

- Composable は `PascalCase`、通常関数は `camelCase`
- boolean は `is` / `has` / `should` プレフィックス
- マジックナンバー・マジックストリングが残っていないか
- 不要な import / 未使用コードが残っていないか

## テスト

- `commonTest` に書けるロジックはプラットフォームテストに寄せていないか
- 境界条件・異常系がカバーされているか
- テストが実装詳細に密結合しすぎていないか

## レビューチェックリスト

- [ ] `commonMain` にプラットフォーム依存が漏れていないか
- [ ] `expect`/`actual` のシグネチャ整合
- [ ] Compose の再コンポーズ・状態管理が妥当か
- [ ] コルーチンのスコープ・ディスパッチャ選択が妥当か
- [ ] `libs.versions.toml` 経由で依存が追加されているか
- [ ] `./gradlew build` が通るか
- [ ] （UI変更時）`./gradlew :composeApp:assembleDebug` が通るか
- [ ] （iOS 影響時）`./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が通るか
- [ ] 必要なテストが揃っているか
