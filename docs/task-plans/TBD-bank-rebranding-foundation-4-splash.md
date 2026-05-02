# `<TBD>` fuju 銀行リブランディング foundation (4/5) — スプラッシュ差し替え

> Notion タスク ID: `<TBD>` （Notion 起票時に追記する。ブランチ名は `feature/<TBD>-bank-rebranding-foundation-4-splash`）
>
> **全体像** は `docs/task-plans/TBD-bank-rebranding-foundation.md`（インデックス）を参照。
> 本 PR は foundation を 5 分割した **4/5 番目**。スプラッシュ画面のロゴ / 装飾を銀行版に差し替え。

## 概要

スプラッシュ画面のロゴを「fuju pay」ワードマークから「fuju Bank」相当の新ロゴに差し替える。Android (`res/drawable/`) と iOS (`Assets.xcassets/`) の両方を更新し、両プラットフォームでスプラッシュが新ロゴで表示されることを確認する。

## 背景・目的

- アプリ起動時の第一印象がリブランディングの肝。テーマトークンや画面実装より先に **見た目の変更ポイントとして気付かれるのがスプラッシュ**。
- ロゴ画像は OS 別に管理しているため（`composeApp/androidMain/res/drawable/` と `iosApp/Assets.xcassets/`）、両方に同期して反映する必要がある。

## スコープ（この PR でやること）

| # | 項目 | 内容 |
|---|------|------|
| 追加 | スプラッシュロゴ画像差し替え（Android） | `res/drawable/fuju_logo.*` / `fuju_splash_decoration.*` を新版に置換 |
| 追加 | スプラッシュロゴ画像差し替え（iOS） | `Assets.xcassets/` 配下のロゴ asset を新版に置換 |
| 追加 | レイアウト調整 | `SplashScreen.kt` のサイズ指定 / SwiftUI 側 `SplashView` のレイアウトを新ロゴに合わせて微調整 |
| 追加 | 背景色（必要時のみ） | `colors.xml` の `fuju_splash_bg` / `FujuSplashBackground.colorset` を Figma 値に合わせて更新（変わっていなければ据え置き） |

## スコープ外（後続 PR で対応）

- アプリ表示名 / 仕上げ（PR 5/5）

## 前提となる先行 PR

- `1-rename`
- `3-theme-tokens`（必須ではないが、テーマ値が確定してからの方が背景色の整合が取りやすい）

## 後続 PR

- `5-app-name-and-polish`

## 影響範囲

### モジュール / ソースセット

- `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/splash/SplashScreen.kt` — レイアウト調整。
- `composeApp/src/androidMain/res/drawable/` — 新ロゴ / 新装飾画像の追加または差し替え。
- `composeApp/src/androidMain/res/values/colors.xml` — `fuju_splash_bg`（必要時のみ）。
- `iosApp/iosApp/Assets.xcassets/` — 新ロゴ asset、`FujuSplashBackground.colorset`（必要時のみ）。
- `iosApp/iosApp/` 配下の SwiftUI スプラッシュ実装ファイル（`SplashView.swift` 相当、実装時に Xcode で確認）。
- `docs/figma-assets/` — Figma から書き出した生 PNG/SVG の保管先（メモリ「Figma アセット保存先」遵守）。

### 破壊的変更

- なし（画像差し替えのみ）。

### 追加依存 (`gradle/libs.versions.toml`)

- なし。

## 影響ファイル一覧

確認済み：

- `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/splash/SplashScreen.kt` — `width(195.dp)` 等のサイズ指定あり。
- `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/MainActivity.kt` — `installSplashScreen()` 呼び出し。背景色リソースの参照名はそのまま据え置く。
- `composeApp/src/androidMain/res/drawable/fuju_logo.*` / `fuju_splash_decoration.*` — 差し替え対象。
- `composeApp/src/androidMain/res/values/colors.xml` — `fuju_splash_bg` の値（Figma 確認の上、必要なら更新）。
- `iosApp/iosApp/iOSApp.swift` — `SplashGate()` 経由でスプラッシュ呼び出し。
- `iosApp/iosApp/Assets.xcassets/FujuSplashBackground.colorset/Contents.json` — 背景色（必要時のみ）。

未確認（実装時に Xcode で確認）：

- iOS 側の SwiftUI スプラッシュ実装ファイル（`SplashView.swift` 相当）。

## 実装ステップ

1. **Figma からロゴアセット書き出し**: Figma MCP の `get_screenshot` または Figma 上での export 機能で、銀行版スプラッシュ用ロゴ / 装飾を書き出し、`docs/figma-assets/bank-redesign-splash-logo.png`（および装飾用画像）として保管する。
   - メモリ「Figma アセット保存先」: 書き出し生ファイルは `docs/figma-assets/` 配下、`/tmp` 等プロジェクト外には置かない。
   - SVG が取れるなら SVG 推奨（Android Vector Drawable 化しやすい）。
2. **背景色の確認**: Figma の銀行版スプラッシュ背景色を確認し、`#F6F7F9` から変わっているなら `colors.xml` の `fuju_splash_bg` と iOS の `FujuSplashBackground.colorset` の双方を更新。同じなら据え置き。
3. **Android 差し替え**:
   - 書き出した画像を `composeApp/src/androidMain/res/drawable/fuju_logo.xml`（Vector Drawable）または `fuju_logo.png`（PNG の場合は密度別ディレクトリ `drawable-xxhdpi/` 等への配置を検討）として配置。
   - `fuju_splash_decoration.*` も同様に差し替え。
   - `SplashScreen.kt` のサイズ指定（`width(195.dp)` 等）を新ロゴのアスペクト比に合わせて調整。
4. **iOS 差し替え**:
   - `iosApp/iosApp/Assets.xcassets/` 配下のロゴ asset を新版に置換（@1x / @2x / @3x）。Xcode で Asset Catalog を開いて差し替えるのが安全。
   - SwiftUI `SplashView` のレイアウト（サイズ・位置）を新ロゴに合わせて調整。
5. **動作確認（Android）**:
   - `./gradlew :composeApp:assembleDebug`。
   - Android エミュレータでアプリ起動 → スプラッシュが新ロゴで表示。
6. **動作確認（iOS）**:
   - `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`。
   - `open iosApp/iosApp.xcodeproj` → Xcode で Run（iPhone 15 Simulator）→ スプラッシュが新ロゴで表示。

## 完了条件

- [ ] Android: 新ロゴ画像が `composeApp/src/androidMain/res/drawable/` に配置されている。
- [ ] iOS: 新ロゴ asset が `iosApp/iosApp/Assets.xcassets/` に配置されている。
- [ ] `SplashScreen.kt` および iOS 側 `SplashView` のレイアウトが新ロゴで崩れていない。
- [ ] `./gradlew :composeApp:assembleDebug` が成功する。
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が成功する。
- [ ] `./gradlew build` が通る。
- [ ] Android エミュレータでスプラッシュが新ロゴで表示される（PR Description にスクリーンショット添付）。
- [ ] iOS Simulator でスプラッシュが新ロゴで表示される（PR Description にスクリーンショット添付）。
- [ ] `docs/figma-assets/` に書き出し生ファイルが保管されている。

## リスク / 注意点

- **iOS 側の SwiftUI スプラッシュ実装ファイル名が未確認**: `iosApp/iosApp/iOSApp.swift` から `SplashGate()` を呼んでいるが、実体ファイルの場所は実装時に Xcode で開いて確認する。
- **画像形式の判断**: Vector Drawable (XML) で済ませられるシンプルなロゴなら XML、複雑なら PNG 密度別。iOS は基本 PNG @1x/@2x/@3x または PDF (Single Scale)。
- **Android 12+ の Splash Screen API**: `installSplashScreen()` を使っているため、`windowSplashScreenAnimatedIcon` の theme 設定にも依存する場合がある。実装時に `themes.xml` を確認。
- **Figma ロゴ書き出し**: ロゴ専用の node ID が「デザイン参照」6 画面の中に組み込まれているか、別 node に存在するかは実装時に Figma MCP で探す。

## ブランチ / PR 運用（メモリ準拠）

- ブランチ: `feature/<TBD>-bank-rebranding-foundation-4-splash`
- コミット例:
  - `chore: スプラッシュロゴを銀行版に差し替え（Android）`
  - `chore: スプラッシュロゴを銀行版に差し替え（iOS）`
  - `style: SplashScreen のサイズ指定を新ロゴに合わせて調整`
- レビュー強制は不要（ソロ開発）。セルフレビュー後にマージ。
