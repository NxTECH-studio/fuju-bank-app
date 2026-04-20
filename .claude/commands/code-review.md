---
description: KMP 観点でコードレビューを実行する
allowed-tools: [Agent, Bash, Read, Grep, Glob]
---

現在のブランチの変更内容に対して、Kotlin Multiplatform 特化のコードレビューを実行する。

## 手順

1. ベースブランチを特定する（`develop` → `main` の順で存在するものを使う。以降 `<base>` と表記）
2. `git diff origin/<base>...HEAD` で変更差分を取得する
3. `git diff --name-only origin/<base>...HEAD` で変更ファイル一覧を取得する
4. 以下の4つの観点でレビューを **並列に** 実行する（Agent ツールで並列起動）

### 観点1: セキュリティ

- ハードコードされたシークレット / APIキー / 認証情報（`local.properties` 以外への混入）
- インジェクション脆弱性（SQL / コマンド / テンプレート / WebView の `loadUrl`）
- 信頼できない入力（ユーザー入力 / API レスポンス / ディープリンク）のバリデーション不足
- 危険な動的評価や安全でないデシリアライズ
- 認可・権限チェックの漏れ
- Android: 権限要求 / `exported` 設定 / 機密情報の `SharedPreferences` 保存
- iOS: Keychain 利用の妥当性

### 観点2: KMP 構造・モジュール境界

- `commonMain` に Android / iOS SDK 依存（`android.*`, `platform.UIKit.*` 等）が混入していないか
- プラットフォーム固有処理が `expect`/`actual` で適切に切られているか
- `expect` / `actual` のシグネチャ整合（visibility / 型引数 / デフォルト引数）
- モジュール責務: UI は `composeApp`、ドメイン / プラットフォーム抽象は `shared`
- 依存が `gradle/libs.versions.toml` 経由で追加されているか（直書き禁止）
- Swift から使う公開 API の扱いやすさ（`sealed class` / `suspend` / `Throws` の扱い）

### 観点3: Compose / 正確性・並行性・パフォーマンス

- `@Composable` 関数の副作用が `LaunchedEffect` / `DisposableEffect` 経由か
- `remember` / `rememberSaveable` の使い分け、`@Stable` / `@Immutable` の付与
- 再コンポーズ誘発（毎回新しいラムダ / リスト / オブジェクト）の有無
- コルーチン: `GlobalScope` 禁止、`viewModelScope` / `rememberCoroutineScope` / 明示スコープ
- `Dispatchers` の選択妥当性、構造化並行性（`supervisorScope` / `coroutineScope`）
- `StateFlow` / `SharedFlow` の購読リーク、`DisposableEffect` のクリーンアップ
- リソース（`Closeable` / Ktor Client / ファイルハンドル）の解放
- N+1 / 不要なループ / 非効率なアルゴリズム

### 観点4: プロジェクト規約・静的検査

- プロジェクトで設定されている検査を実行する
  - `./gradlew check`（lint / ktlint / detekt が設定されていれば）
  - `./gradlew build` でコンパイル警告を確認
- Kotlin コーディング規約に沿っているか（`!!` 多用 / プラットフォーム型 / 曖昧な `Any`）
- `sealed` の `when` が `else` に逃げず網羅されているか
- 命名（`camelCase` / Composable は `PascalCase` / boolean プレフィックス）
- マジックナンバー / マジックストリング / 未使用 import / 未使用コード

5. 4つの観点の結果を統合し、以下の形式でレポートを出力する

## レポート形式

```
## コードレビュー結果

### Critical（必ず修正）
- [ ] ...

### Warning（修正推奨）
- [ ] ...

### Info（検討事項）
- [ ] ...

### Good（良い点）
- ...
```

6. Critical・Warning の指摘事項を修正し、コミットする
