# `<TBD>` fuju 銀行リブランディング (foundation インデックス)

> Notion タスク ID: `<TBD>` （Notion 起票時に追記する。foundation は **5 PR に分割** されているため、Notion 上では 5 サブタスクとして起票するか、1 親タスク + 5 子タスクの構造を推奨）

## このドキュメントの位置付け

PayPay 風キャッシュレス決済アプリ「fujupay」として作っていた既存実装を、**「fuju 銀行 (fuju Bank)」** ブランドへリネーム / 再装飾する一連のリブランディング作業の **foundation（土台）インデックス**。

foundation はレビュー負荷を下げるため **5 つの独立した PR に分割** された。本ドキュメントは：

1. foundation 全体のゴール / スコープ / コンテキストを記述する
2. 5 サブ計画書へのリンクと依存関係を示す
3. foundation 後に続く後続タスク（画面別 + KMP 移行）の概要を残す

各 PR の **詳細な実装ステップ・完了条件** はサブ計画書を参照すること。

## 背景・目的

- プロダクトの方向性が「決済アプリ」から「銀行口座アプリ」へ転換した。Figma に銀行版の 6 画面リデザインが提示済み（後述「デザイン参照」）。
- 既存コードは `fujupay` 前提のクラス名・カラートークン名 (`FujupayColors`)・通貨表記 (`円`) で組まれており、後続の各画面差し替え作業の前に **下地** を整備しておかないと、画面実装の差分とリブランディングの差分が混ざって PR レビューが破綻する。
- foundation を先に切ることで、後続の画面別タスクは **「新しいテーマトークンを使って画面を組み直すだけ」** に集中できる状態を作る。

## foundation のゴール

foundation 5 PR がすべてマージされた状態で：

- [x] `FujupayColors` → `FujuBankColors` リネーム完了。`grep -R "Fujupay" .`（コード側）が 0 件。（PR #54 / foundation-1 にて達成）
- [x] 銀行ブランドのカラーパレット・タイポトークンが Figma の銀行版に沿って再定義済み。（PR #56 / foundation-3 にて達成）
- [x] 通貨単位「ふじゅ〜」共通フォーマッタ (`CurrencyFormatter`) が `shared/commonMain` に存在し、`./gradlew :shared:allTests` が通る。（PR #55 / foundation-2 にて達成）
- [x] スプラッシュ画面のロゴが新ブランド版に差し替わっている（Android / iOS 両方）。（PR #57 / foundation-4 にて達成）
- [x] アプリ表示名は判断完了。今回ヒアリング結果が「『ふじゅ〜』据え置きで OK」のため `app_name` / `CFBundleDisplayName` は変更せず継続。`applicationId` も据え置き。
- [x] Android で動作確認: `./gradlew :composeApp:assembleDebug` 成功（foundation-5 で確認）。エミュレータでの目視確認は PR レビュー時に実施。
- [x] iOS で動作確認: `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` 成功（foundation-5 で確認）。iOS Simulator での目視確認は PR レビュー時に実施。
- [x] 認証系（auth / signup / welcome）画面は **見た目の機能変更なし**。カラートークン import / 値変更のみ反映済み。
- [x] `./gradlew build` 全体が通る。（foundation-5 で確認）

## foundation の 5 PR 構成

実装順 = 依存順 = ファイル名の番号順。**1 → 2 → 3 → 4 → 5 の順にマージする** こと（並走させない）。

| # | サブ計画書 | 内容 | 依存 | 状態 |
|---|-----------|------|------|------|
| 1 | [`TBD-bank-rebranding-foundation-1-rename.md`](./TBD-bank-rebranding-foundation-1-rename.md) | `Fujupay*` → `FujuBank*` 機械的リネーム（値変更なし） | なし | ✅ PR #54 マージ済 |
| 2 | [`TBD-bank-rebranding-foundation-2-currency-formatter.md`](./TBD-bank-rebranding-foundation-2-currency-formatter.md) | `CurrencyFormatter` を `shared/commonMain` に新設 + commonTest + 「円」表記置換 | 1 | ✅ PR #55 マージ済 |
| 3 | [`TBD-bank-rebranding-foundation-3-theme-tokens.md`](./TBD-bank-rebranding-foundation-3-theme-tokens.md) | カラー / タイポトークンの値再定義（Figma 銀行版反映 + 不要キー削除） | 1（推奨: 2） | ✅ PR #56 マージ済 |
| 4 | [`TBD-bank-rebranding-foundation-4-splash.md`](./TBD-bank-rebranding-foundation-4-splash.md) | スプラッシュロゴ差し替え（Android + iOS） | 1（推奨: 3） | ✅ PR #57 マージ済 |
| 5 | [`TBD-bank-rebranding-foundation-5-app-name-and-polish.md`](./TBD-bank-rebranding-foundation-5-app-name-and-polish.md) | アプリ表示名 + 仕上げ + 全体ビルド検証 | 1, 2, 3, 4 | 🟡 本 PR（クロージング） |

### 分割方針

- **PR 1（rename）** と **PR 2（currency-formatter）** はそれぞれ独立性が高いので最初に。レビュー負荷が極小。
- **PR 3（theme-tokens）** は値変更が中心で diff が中規模。リネーム後に値だけ動かすことで diff を見やすくする。
- **PR 4（splash）** は画像 / レイアウト変更で UI レビュー軸が違うので独立 PR に分離。
- **PR 5（polish）** は foundation のクロージング。Android/iOS 両方の動作確認 + スクリーンショット添付に集中する。

### 推奨実装順 / 最初に取り組むべき PR

**最初に着手するのは `1-rename`**。値変更のない純粋なリネームで、後続 4 PR すべての前提となる。

`/start-with-plan TBD-bank-rebranding-foundation-1-rename.md` で着手する。

## foundation 後の後続タスク（別タスクとして起票）

foundation 5 PR がすべてマージされた後、以下の独立タスクを順次着手する：

- `<TBD>-bank-rebranding-home` — ホーム画面リデザイン。QR / バーコード表示・クイックアクションタイル群を撤去し、銀行口座向けレイアウト（残高・直近入出金・口座カード）に組み直す。Figma node `504-5945`。
- `<TBD>-bank-rebranding-transactions` — 取引履歴 + 取引詳細。SNS シェア / 視線データなど銀行アプリ独自メタデータの表示。Figma node `709-8658` / `697-7601`。
- `<TBD>-bank-rebranding-account` — 「アカウント」画面の本実装化（現在はプレースホルダ）。Figma node `702-6440`。
- `<TBD>-bank-rebranding-notification-settings` — 通知設定画面の新規追加。Figma node `697-8394` / `718-7332`。
- `<TBD>-kmp-common-migration` — 既存 `composeApp/androidMain` 配下の Compose UI / ViewModel を **段階的に commonMain 移行** する独立タスク（後述「commonMain への切り分け方針」参照）。

> 後続タスクは Notion 起票時にこのリストを参照し、`<TBD>` を実 ID に置換する。

## commonMain への切り分け方針（foundation での判断）

ヒアリング B 「OS 別最適化 × 共通ロジックの共通化」を踏まえた判断：

### 現状の構成（重要）

- `shared` モジュール: KMP 対応済み（`androidTarget` + `iosArm64` + `iosSimulatorArm64`）。Compose 依存なし。**純 Kotlin の共通ロジック置き場**として既に機能している。
- `composeApp` モジュール: **`androidTarget` のみ**。Compose Multiplatform プラグインは入っているが、iOS ターゲットは未設定。
- `iosApp`: SwiftUI ベース。Compose Multiplatform を取り込んでおらず、`Shared.framework` のみ参照。**iOS の UI は SwiftUI で個別実装** されている構成。

つまり現状は「ロジックは KMP（shared）、UI は OS ネイティブ（Compose ＋ SwiftUI）」のスタイル。Compose Multiplatform UI の commonMain 共有はまだ行っていない。

### foundation タスクでの切り分け

| 対象 | 配置場所 | 理由 |
|------|---------|------|
| `FujuBankColors`（`androidx.compose.ui.graphics.Color` を使う） | `composeApp/androidMain` 据え置き | `composeApp` が androidTarget のみなので commonMain に置けない。Compose Multiplatform の `Color` を使うには `composeApp` の KMP ターゲット拡張＋ iosApp の Compose 統合が必要 = foundation の範囲外 |
| `CurrencyFormatter`（純 Kotlin） | **`shared/commonMain` に新規** | 純数値整形は Compose 不要。commonMain に置けば iOS の SwiftUI からも `CurrencyFormatter().formatFujus(...)` で呼べる |
| 新ロゴ画像 | Android: `composeApp/androidMain/res/drawable/`、iOS: `iosApp/Assets.xcassets/` に **個別配置** | UI が OS 別実装なので画像も OS 別管理が現状最適 |
| Splash 背景色 | Android: `colors.xml`、iOS: `FujuSplashBackground.colorset` に **個別配置**＋同期コメントを残す | 既存方針（`colors.xml` のコメント参照）を踏襲 |
| iOS 側の色定義（SwiftUI 用） | iOS Asset Catalog に `Color Set` で個別定義 | 上記理由 |

### 後続タスク（`<TBD>-kmp-common-migration`）で扱う範囲

foundation では **触らない**：

- `composeApp` を multiplatform ターゲット (iosArm64 / iosSimulatorArm64) 対応に拡張する。
- `App.kt` を commonMain に移植する（`android.os.SystemClock` を `kotlinx.datetime` または expect/actual に置換）。
- `BuildConfig.DEBUG` を `expect val isDebug: Boolean` で抽象化。
- `FujuBankColors` (`androidx.compose.ui.graphics.Color`) を Compose Multiplatform の `org.jetbrains.compose.ui.graphics.Color` に切り替えて commonMain へ移植。
- `iosApp` を SwiftUI から Compose Multiplatform UI に置き換える（または共存させる）。

これは **大きな構造変更** なのでリブランディング作業と並走させない。リブランディング 5 タスクが落ち着いてから別 PR で着手する想定。

### この判断の根拠

- 「リブランディング ＋ 全面 commonMain 移行」を同時にやると差分が爆発し、PR レビュー・動作検証ともに破綻する。
- foundation の目標は「**後続の画面別タスクが新テーマトークンで実装できる土台**」であって、KMP 構造自体の刷新ではない。
- `CurrencyFormatter` だけは shared に置いて先行例を作っておくと、後続の `kmp-common-migration` タスクでパターンを引っ張りやすい。

## デザイン参照

Figma fileKey: `bzm13wVWQmgaFFmlEbJZ3k`

| 画面 | node-id | Figma URL | ローカル画像 |
|------|---------|-----------|-------------|
| ホーム | `504-5945` | `https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/?node-id=504-5945` | `docs/figma-assets/bank-redesign-home.png` |
| 取引履歴 | `709-8658` | `https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/?node-id=709-8658` | `docs/figma-assets/bank-redesign-transactions.png` |
| 取引詳細 | `697-7601` | `https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/?node-id=697-7601` | `docs/figma-assets/bank-redesign-transaction-detail.png` |
| アカウント | `702-6440` | `https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/?node-id=702-6440` | `docs/figma-assets/bank-redesign-account.png` |
| 通知設定 (1) | `697-8394` | `https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/?node-id=697-8394` | `docs/figma-assets/bank-redesign-notification-settings-1.png` |
| 通知設定 (2) | `718-7332` | `https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/?node-id=718-7332` | `docs/figma-assets/bank-redesign-notification-settings-2.png` |

> ローカル画像のファイル名は実際の保存名に合わせて実装時に修正する。

PR 3（theme-tokens）実装中は `mcp__figma__get_design_context` で各 node から **共通カラーパレットとタイポ** を抽出し、`FujuBankColors` の値を確定させる。スプラッシュ用ロゴが別 node にある場合は PR 4（splash）で別途調査する。

## ブランチ / PR 運用（メモリ準拠）

- 各サブ PR のブランチ命名: `feature/<TBD>-bank-rebranding-foundation-{1-5}-{slug}`
- main 直コミット禁止（メモリ「`/start-with-plan` は feature ブランチ＋PR 必須」遵守）
- コミットメッセージは日本語（メモリ「コミットメッセージは日本語で書く」遵守）。Conventional Commits の prefix（`refactor:` / `feat:` / `chore:` 等）は英語のまま
- レビュー強制は不要（ソロ開発、メモリ「Solo developer project」遵守）。セルフレビュー後にマージ。

## 進行のはじめ方

```
/start-with-plan TBD-bank-rebranding-foundation-1-rename.md
```
