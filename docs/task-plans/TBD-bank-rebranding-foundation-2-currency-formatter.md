# `<TBD>` fuju 銀行リブランディング foundation (2/5) — `CurrencyFormatter` を shared/commonMain に新設

> Notion タスク ID: `<TBD>` （Notion 起票時に追記する。ブランチ名は `feature/<TBD>-bank-rebranding-foundation-2-currency-formatter`）
>
> **全体像** は `docs/task-plans/TBD-bank-rebranding-foundation.md`（インデックス）を参照。
> 本 PR は foundation を 5 分割した **2/5 番目**。通貨単位「円」→「ふじゅ〜」の共通化。

## 概要

通貨表記を **「円」→「ふじゅ〜」** に変える共通ユーティリティ `CurrencyFormatter` を `shared/commonMain` に新設し、`commonTest` でテストを書く。さらに既存の「円」表記呼び出し箇所をすべて新フォーマッタへ差し替える。

## 背景・目的

- 銀行アプリの通貨単位を「ふじゅ〜」へ変更する方針が確定。各画面で個別に文字列連結するとフォーマット揺れ（カンマ区切り / マイナス記号 / 単位の前後）が発生する。
- KMP の `shared/commonMain` に置けば、Android (Compose) からも iOS (SwiftUI) からも同じロジックで呼び出せる。
- 後続の `kmp-common-migration` タスクで commonMain に置くべきロジックの **先行例** にもなる。

## スコープ（この PR でやること）

| # | 項目 | 内容 |
|---|------|------|
| (h) | `CurrencyFormatter` 新設 | `shared/src/commonMain/kotlin/studio/nxtech/fujubank/format/CurrencyFormatter.kt` |
| (h) | `CurrencyFormatterTest` | `shared/src/commonTest/kotlin/.../CurrencyFormatterTest.kt`（kotlin.test） |
| (h) | 既存「円」表記の置換 | grep `"円"` で該当箇所を洗い出して `CurrencyFormatter.formatFujus(...)` に差し替え |

## スコープ外（後続 PR で対応）

- カラー / タイポトークンの値再定義（PR 3/5）
- スプラッシュ差し替え（PR 4/5）
- アプリ表示名 / 仕上げ（PR 5/5）

## 前提となる先行 PR

- `1-rename`（`FujuBankColors` などのリネームが完了していること）

## 後続 PR

- `3-theme-tokens`

## 影響範囲

### モジュール / ソースセット

- `shared/src/commonMain/` — 新規ファイル追加。
- `shared/src/commonTest/` — 新規テスト追加。
- `composeApp/src/androidMain/` — 既存「円」表記の参照箇所を差し替え（数箇所程度の想定）。
- `iosApp/iosApp/` — SwiftUI 側で「円」表記を直書きしているなら `Shared.framework` 経由で `CurrencyFormatter().formatFujus(...)` を呼ぶ形に変更（該当箇所があれば）。

### 破壊的変更

- **Shared framework ABI**: 新クラス `CurrencyFormatter` を **追加するのみ**。既存 API の削除・変更なし。iOS 側の追従コストは低い。

### 追加依存 (`gradle/libs.versions.toml`)

- **なし**。`kotlinx.datetime` も使わない純粋な数値整形（`kotlin.text` のみ）。

## 影響ファイル一覧

新規作成：

- `shared/src/commonMain/kotlin/studio/nxtech/fujubank/format/CurrencyFormatter.kt`
- `shared/src/commonTest/kotlin/studio/nxtech/fujubank/format/CurrencyFormatterTest.kt`

更新（実装前に grep で確定）：

- `composeApp/src/androidMain/kotlin/.../**/*.kt` のうち `"円"` を含むファイル。
- `iosApp/iosApp/**/*.swift` のうち `"円"` を含むファイル。

## 実装ステップ

1. `grep -R '"円"' composeApp/ iosApp/ shared/` で既存の「円」表記呼び出し箇所を全件リストアップ。
2. `shared/src/commonMain/kotlin/studio/nxtech/fujubank/format/CurrencyFormatter.kt` を新規作成。
   - API 例:
     ```kotlin
     object CurrencyFormatter {
         fun formatFujus(amount: Long): String  // "1,234 ふじゅ〜"
     }
     ```
   - 内部実装は `kotlin.text` のみで完結（Locale 依存させない＝ KMP 安全）。
   - 3 桁区切りカンマは手書きで実装（`java.text.NumberFormat` は commonMain に存在しないため使えない）。
   - 負数は `-1,234 ふじゅ〜` の形にする（マイナス記号→数値→単位の順）。
   - 「円」表記の後方互換 API は **作らない**（呼び出し元で総入れ替えする）。
3. `shared/src/commonTest/kotlin/.../CurrencyFormatterTest.kt` を作成。最低限のケースを kotlin.test で：
   - `0` → `"0 ふじゅ〜"`
   - `100` → `"100 ふじゅ〜"`
   - `999` → `"999 ふじゅ〜"`（カンマ境界手前）
   - `1000` → `"1,000 ふじゅ〜"`（カンマ境界）
   - `1234567` → `"1,234,567 ふじゅ〜"`
   - `-1234` → `"-1,234 ふじゅ〜"`
4. `./gradlew :shared:allTests` でテストを通す。
5. ステップ 1 で洗い出した既存「円」表記箇所をすべて `CurrencyFormatter.formatFujus(...)` に差し替え。
   - Android (Compose): `studio.nxtech.fujubank.format.CurrencyFormatter` を import して直接呼び出し。
   - iOS (SwiftUI): `Shared.framework` 経由で `CurrencyFormatter.shared.formatFujus(amount: ...)` のように呼び出し（Kotlin の `object` は Swift では `.shared` でアクセスする）。
6. ビルド確認：
   - `./gradlew :shared:allTests`
   - `./gradlew :composeApp:assembleDebug`
   - `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`

## 完了条件

- [ ] `shared/src/commonMain/.../CurrencyFormatter.kt` が存在する。
- [ ] `shared/src/commonTest/.../CurrencyFormatterTest.kt` が存在し、上記 6 ケース以上が PASS する。
- [ ] `./gradlew :shared:allTests` が成功する。
- [ ] `grep -R '"円"' composeApp/ iosApp/` の結果が 0 件（コードコメント / 文字列リテラルともに）。
- [ ] `./gradlew :composeApp:assembleDebug` が成功する。
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が成功する。
- [ ] `./gradlew build` が通る。
- [ ] （Android で確認可能な金額表示画面があれば）エミュレータで「ふじゅ〜」表記になっていることを目視確認。

## 技術的な補足

- KMP の `commonMain` では `java.text.NumberFormat` / `java.util.Locale` は使えない。Locale 依存しない単純な 3 桁区切りカンマは手書きで実装する：
  ```kotlin
  private fun groupWithComma(absStr: String): String {
      val sb = StringBuilder()
      val len = absStr.length
      for (i in 0 until len) {
          if (i > 0 && (len - i) % 3 == 0) sb.append(',')
          sb.append(absStr[i])
      }
      return sb.toString()
  }
  ```
- 単位と数値の間にスペースを入れるかは Figma の実画面表記に合わせる（基本は `1,234 ふじゅ〜`、Figma が詰めていれば `1,234ふじゅ〜`）。本 PR の実装時に Figma MCP で確認する。
- iOS 側で Kotlin object を呼ぶときは `CurrencyFormatter.shared.formatFujus(amount: 1234)` の形になる点に注意（Swift interop の慣例）。

## ブランチ / PR 運用（メモリ準拠）

- ブランチ: `feature/<TBD>-bank-rebranding-foundation-2-currency-formatter`
- コミット例:
  - `feat: shared/commonMain に CurrencyFormatter (ふじゅ〜) を追加`
  - `test: CurrencyFormatter の commonTest を追加`
  - `refactor: 既存の「円」表記を CurrencyFormatter.formatFujus に差し替え`
- レビュー強制は不要（ソロ開発）。セルフレビュー後にマージ。
