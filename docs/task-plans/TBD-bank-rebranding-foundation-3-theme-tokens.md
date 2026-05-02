# `<TBD>` fuju 銀行リブランディング foundation (3/5) — テーマトークン再定義

> Notion タスク ID: `<TBD>` （Notion 起票時に追記する。ブランチ名は `feature/<TBD>-bank-rebranding-foundation-3-theme-tokens`）
>
> **全体像** は `docs/task-plans/TBD-bank-rebranding-foundation.md`（インデックス）を参照。
> 本 PR は foundation を 5 分割した **3/5 番目**。テーマの値を Figma 銀行版に合わせて再定義。

## 概要

`FujuBankColors`（PR 1 でリネーム済み）の **値** を Figma 銀行版デザインに沿って再定義する。決済アプリ専用カテゴリ色（`ActionPurple` / `ActionGreen` / `ActionBlue` 等）は削除し、後続の画面別タスクで使ってはいけない色を **コンパイルエラーで可視化** する。任意で `FujuBankTypography` も新設する。

## 背景・目的

- 現状の `FujuBankColors` の値は PayPay 風の決済アプリ用パレット。銀行アプリのトーン（落ち着いた銀行ブランドの配色）と合わない。
- 後続の home / transactions / account / notification-settings 画面実装で「新カラートークンを `FujuBankColors.xxx` で参照すれば自動的に銀行ブランドになる」状態を作る。
- 不要な色キーを削除しておくことで、後続タスクで誤って決済アプリ用パレットを引き継いでしまうのを防ぐ。

## スコープ（この PR でやること）

| # | 項目 | 内容 |
|---|------|------|
| (g) | カラー値の再定義 | Figma 銀行版 6 画面から抽出したカラーパレットを `FujuBankColors` に反映 |
| (g) | 不要カラーキーの削除 | `ActionPurple` / `ActionGreen` / `ActionBlue` など決済アプリ専用色 |
| (g) | （任意）`FujuBankTypography` | 銀行版の見出し / 本文 / 数値表示テキストスタイルを軽量 data class で |
| 補修 | foundation 残し画面の暫定パッチ | auth / signup / welcome / splash で削除色を参照していた箇所を新キーに繋ぎ替え |

**画面の構造変更（レイアウト変更 / コンポーネント追加削除）は本 PR では行わない**。あくまで色とフォントスタイルの再定義のみ。

## スコープ外（後続 PR で対応）

- スプラッシュロゴ / 背景の差し替え（PR 4/5）
- アプリ表示名 / 仕上げ（PR 5/5）
- ホーム / 取引履歴 / アカウント / 通知設定の画面実装は **別タスク**（`<TBD>-bank-rebranding-home` など）

## 前提となる先行 PR

- `1-rename`（`FujuBankColors` リネーム完了）
- `2-currency-formatter`（必須ではないが、依存順としてマージ済み推奨）

## 後続 PR

- `4-splash`

## 影響範囲

### モジュール / ソースセット

- `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/theme/FujuBankColors.kt` — 値の更新と一部キー削除。
- （任意）`composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/theme/FujuBankTypography.kt` — 新規。
- `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/{auth,signup,welcome,splash}/` — 削除した色を参照していたら新キーに繋ぎ替え。
- `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/{home,transactions,account}/` — 削除した色を参照していたら **暫定パッチ** で新カラーキーに置き換え（後続の画面別タスクで作り直すので最低限ビルドが通る形にとどめる）。

### 破壊的変更

- **公開 API（モジュール内のみ）**: `FujuBankColors.ActionPurple` などの **削除**。プロジェクト内のみで参照されているため外部影響はないが、コンパイルエラーが出る箇所は本 PR で全部潰す。
- **Shared framework ABI**: 変更なし。

### 追加依存 (`gradle/libs.versions.toml`)

- なし。

## 影響ファイル一覧

確認済み：

- `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/theme/FujuBankColors.kt`

未確認だが影響想定（実装時に grep で確定）：

- `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/**/*.kt` のうち `FujuBankColors.` を参照しているファイル全部。

> 実装前に `grep -R "FujuBankColors\." composeApp/` を 1 回流して全箇所を洗い出す。

## 実装ステップ

1. **Figma 値の抽出**: Figma MCP の `get_design_context` で銀行版 6 画面（インデックスの「デザイン参照」表）から共通カラーパレット・主要タイポを抽出する。
   - ホーム `504-5945`、取引履歴 `709-8658`、取引詳細 `697-7601`、アカウント `702-6440`、通知設定 (1) `697-8394`、通知設定 (2) `718-7332`。
   - fileKey: `bzm13wVWQmgaFFmlEbJZ3k`。
2. 抽出した銀行版の値で `FujuBankColors` の各プロパティを更新する：
   - `Background` / `Surface` / `OnSurface` 等の構造系キーは **値だけ更新**（キー名は維持）。
   - `BrandPink` 等のブランド系キーは銀行版のメインカラーへ。
   - `ActionPurple` / `ActionGreen` / `ActionBlue` など決済アプリ専用カテゴリ色は **削除**。
3. （任意）`FujuBankTypography.kt` を新設：
   ```kotlin
   data class FujuBankTypography(
       val headline: TextStyle,
       val body: TextStyle,
       val amount: TextStyle, // 残高 / 金額表示用
       // ...
   )
   ```
   Material3 の `Typography` ではなく軽量な data class で OK。
4. `grep -R "FujuBankColors\." composeApp/` で全参照を洗い出し、削除したキーを参照していた箇所に対応：
   - **auth / signup / welcome / splash**: 新カラーキーに **正式に繋ぎ替え** る（foundation の対象画面）。
   - **home / transactions / account**: 後続タスクで作り直すので、最低限ビルドが通るよう **暫定パッチ**（仮に `FujuBankColors.Surface` などに置換し、PR Description で「ここは後続タスクで再実装する」旨を明記）。
5. ビルド確認：`./gradlew :composeApp:assembleDebug`。
6. Android エミュレータで auth / signup / welcome / splash の見た目を目視確認。崩れていたら微調整するか、`AuthBackground` のような専用キーを追加する。

## 完了条件

- [ ] `FujuBankColors` の値が Figma 銀行版に揃っている（少なくとも構造系の `Background` / `Surface` / `OnSurface` / ブランドメインカラー）。
- [ ] 決済アプリ専用カテゴリ色（`ActionPurple` 等）が削除されている。
- [ ] （任意）`FujuBankTypography` が存在し、最低限のスタイル（見出し / 本文 / 金額）が定義されている。
- [ ] `./gradlew :composeApp:assembleDebug` が成功する。
- [ ] `./gradlew build` が通る。
- [ ] auth / signup / welcome / splash 画面の見た目が銀行ブランドのトーンになっている（エミュレータで目視確認）。
- [ ] home / transactions / account の暫定パッチ箇所が PR Description に列挙されている（後続タスクで作り直すリスト）。

## リスク / 注意点

- **認証系画面のリグレッション**: 値変更で auth / signup / welcome の見た目が崩れる可能性あり。エミュレータ確認を必ず行う。崩れた場合は色キーを使い分ける（`AuthBackground` 等を別途定義）か、許容範囲なら据え置き。
- **暫定パッチが残る**: home / transactions / account は後続の画面別タスクで作り直すため、本 PR では「ビルドが通る最低限」で構わない。**PR Description に「暫定パッチ箇所」リストを必ず記載** し、後続タスクで参照できるようにする。
- **Typography は任意**: 時間がなければ `FujuBankTypography` の新設はスキップして、後続タスクで必要に応じて追加して構わない。

## ブランチ / PR 運用（メモリ準拠）

- ブランチ: `feature/<TBD>-bank-rebranding-foundation-3-theme-tokens`
- コミット例:
  - `refactor: FujuBankColors の値を銀行版 Figma に合わせて再定義`
  - `chore: 決済アプリ専用カテゴリ色 (ActionPurple 等) を削除`
  - `feat: FujuBankTypography を新設（見出し/本文/金額）`
  - `fix: 削除カラー参照箇所の暫定パッチ`
- レビュー強制は不要（ソロ開発）。セルフレビュー後にマージ。
