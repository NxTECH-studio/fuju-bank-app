# `<TBD>` fuju 銀行リブランディング foundation (1/5) — `Fujupay*` → `FujuBank*` リネーム

> Notion タスク ID: `<TBD>` （Notion 起票時に追記する。ブランチ名は `feature/<TBD>-bank-rebranding-foundation-1-rename`）
>
> **全体像** は `docs/task-plans/TBD-bank-rebranding-foundation.md`（インデックス）を参照。
> 本 PR は foundation を 5 分割した **1/5 番目** で、後続 4 PR の前提となる純粋リネーム作業。

## 概要

`FujupayColors` をはじめとする `Fujupay*` 命名のクラス / オブジェクトを `FujuBank*` にリネームする **機械的置換のみの PR**。値の変更（カラーパレット差し替え）や API 追加（`CurrencyFormatter`）は本 PR では行わない。

## 背景・目的

- リブランディング後続作業で「テーマトークンの値変更」「画面差し替え」を行う際、同じ PR にリネーム差分が混ざるとレビューが破綻する。
- 値変更の前に **クラス名だけ** を一括変換しておくことで、後続 PR の diff を最小化する。
- ファイル名・型名・参照 import まで一括で揃えるため、IDE の Rename Refactor を使いつつ最後に `grep` で 0 件を確認する。

## スコープ（この PR でやること）

| # | 項目 | 内容 |
|---|------|------|
| (a) | クラス / オブジェクト名のリネーム | `FujupayColors` → `FujuBankColors`。他に `Fujupay*` 命名があれば併せて更新 |
| (a) | ファイル名のリネーム | `FujupayColors.kt` → `FujuBankColors.kt` |
| (a) | プロジェクト全体の参照更新 | `composeApp/` 配下の import / 参照を全て新名に置換 |

**値の変更はしない**: カラーパレットのプロパティ名・値はそのまま据え置く（後続 PR 3 で再定義）。

## スコープ外（後続 PR で対応）

- 通貨フォーマッタ追加（PR 2/5）
- カラー / タイポトークンの値再定義（PR 3/5）
- スプラッシュ差し替え（PR 4/5）
- アプリ表示名 / 仕上げ（PR 5/5）

## 前提となる先行 PR

- なし（5 PR の最初）

## 後続 PR

- `<TBD>-bank-rebranding-foundation-2-currency-formatter`
- 以降のすべてが本 PR のリネーム結果を前提とする

## 影響範囲

### モジュール / ソースセット

- `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/` 配下のみ。
- `shared/`・`iosApp/` には現状 `Fujupay*` 命名は存在しない想定（実装時に `grep` で再確認）。

### 破壊的変更

- **公開 API（モジュール内のみ）**: `FujupayColors` → `FujuBankColors`。プロジェクト内のみで参照されているため外部影響なし。
- **Shared framework ABI**: 変更なし。
- **applicationId** (`studio.nxtech.fujubank`): 既に bank なので **据え置き**。

### 追加依存 (`gradle/libs.versions.toml`)

- なし。

## 影響ファイル一覧

確認済み：

- `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/theme/FujupayColors.kt` — リネーム対象本体（15 個の Color 定義）。
- `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/App.kt` — `FujupayColors.Background` を 2 箇所参照。
- `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/splash/SplashScreen.kt` — `FujupayColors` 参照（実装時に確認）。

未確認だが影響想定（実装時に `grep -R "Fujupay" composeApp/` で全件洗い出し）：

- `features/auth/`, `features/signup/`, `features/welcome/`, `features/shell/`, `features/home/`, `features/transactions/`, `features/account/` 配下の参照。

> 実装前に `grep -R "Fujupay" composeApp/ shared/ iosApp/` を 1 回流して全箇所を洗い出し、PR Description にファイル数を記載する。

## 実装ステップ

1. `grep -R "Fujupay" composeApp/ shared/ iosApp/` で対象ファイルを全件リストアップする。
2. `FujupayColors.kt` を `FujuBankColors.kt` にファイル名ごとリネーム。`object FujupayColors` → `object FujuBankColors`。プロパティ名 / 値は据え置く。
3. プロジェクト全体で `Fujupay` → `FujuBank` の機械的置換（IDE Refactor → Rename を推奨。import まで一括更新される）。
4. その他 `Fujupay*` 命名（`FujupayTheme` など、もしあれば）も同様にリネーム。
5. `grep -R "Fujupay" .` で 0 件になったことを確認。
6. ビルド確認：`./gradlew :composeApp:assembleDebug`。
7. （任意）Android エミュレータでログイン画面まで起動し、見た目が崩れていないことを目視確認。

## 完了条件

- [ ] `grep -R "Fujupay" .` の結果が 0 件（コメント・ドキュメント含めても残らないこと。`docs/task-plans/` 内の `Fujupay*` 言及だけは履歴として OK）。
- [ ] `FujupayColors.kt` が削除され、`FujuBankColors.kt` が存在する。
- [ ] `./gradlew :composeApp:assembleDebug` が成功する。
- [ ] `./gradlew build` が通る。
- [ ] 認証系（auth / signup / welcome）画面の見た目が崩れていない（値変更していないため自明だが、エミュレータで一度起動して確認）。

## リスク / 注意点

- **IDE Rename を使うこと**: 手動置換だと import 漏れが起きやすい。Android Studio の Refactor → Rename を推奨。
- **`docs/task-plans/` 内の言及**: 履歴ドキュメントに `FujupayColors` の記述が残っても本 PR の grep 確認では除外して構わない（あくまでコード側で 0 件にする）。
- **後続 PR との衝突**: この PR を最初に必ずマージしてから後続に着手する。並走させない。

## ブランチ / PR 運用（メモリ準拠）

- ブランチ: `feature/<TBD>-bank-rebranding-foundation-1-rename`
- コミット例: `refactor: FujupayColors を FujuBankColors にリネーム`
- レビュー強制は不要（ソロ開発）。セルフレビュー後にマージ。
