# 銀行アプリクライアント：ホーム画面 (Figma 709-8658) の Figma デザイン適用 — Android 先行

## 概要

`composeApp/src/androidMain/.../features/home/HomeScreen.kt` を Figma `709-8658` の新デザインに合わせて改修する。**Android のみ** を対象とし、commonMain 移植や iOS 対応は本タスクに含めない（後続の Task 3 で扱う）。最短で「Figma デザインの可視差分」を得ることを目的とする。

## 背景・目的

### なぜ Android 先行なのか

前提タスク `client-ios-1-compose-multiplatform-foundation`（[計画書](./client-ios-1-compose-multiplatform-foundation.md)）で `composeApp` に iOS ターゲットを追加し、Compose UI を iOS から起動する基盤を作ろうとしたが、`Shared.framework` と `ComposeApp.framework` を両方 `isStatic = true` で 1 アプリにリンクすると Kotlin/Native ランタイムシンボルが重複し、iOS 起動不能（Splash で永久停止）になる構造的問題が発生して revert した。詳細は Task 1 計画書末尾の「失敗経緯と方針変更」セクション参照。

このため方針を「KMP は iOS/Android 両対応必須」を維持しつつ、

1. まず本タスク (Task 2) で **Android 側だけ** Figma デザインを適用して見た目を確定させる
2. 次のタスク (Task 3) で framework 統合戦略を再設計し、commonMain 移植と iOS 対応を行う

という段階的アプローチに変更した。本タスクは段階 1 にあたる。

### 目的

- Figma `709-8658` の新ホーム画面デザインを Android 実機で体感できる状態にする
- 設計検討（Task 3 の framework 戦略決定）に時間を使うあいだも、デザイン適用の成果は止めない
- iOS 対応時（Task 3）に commonMain 移植する際の「移植元」として、Android 側の Compose 実装を Figma 準拠で確定させておく

## スコープ

- `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/home/HomeScreen.kt` および関連 Composable の Figma 反映
  - レイアウト・余白・色・タイポグラフィの更新
  - 新規アセット（アイコン / 画像）の `composeApp/src/androidMain/res/` への取り込み
  - `theme/FujuBankColors.kt` / `theme/FujuBankTypography.kt` の値更新（必要なら）
- 関連 Composable (`features/home/components/` 配下の `BalanceCard` / `ActionTiles` / `FujuBankHeader` 等) の Figma 準拠改修
- Android 実機 / エミュレータでの動作確認

### アウトオブスコープ

- **commonMain への移植は一切やらない**（Task 3 のスコープ）
- **iOS 対応は一切やらない**（Task 3 のスコープ）
- `composeApp` の iOS ターゲット追加（`iosArm64()` / `iosSimulatorArm64()`）は行わない（Task 3 で framework 戦略決定後に着手）
- Figma `697-7601` / `702-6440` の関連画面適用 → 個別タスク
- Login / Signup / Splash / MFA など他画面のデザイン更新
- ホーム画面のロジック追加（残高取得 API 変更、新しいアクション追加など）。デザイン適用に伴う必要最小限の state 追加のみ許容
- Navigation ライブラリ導入

## 影響範囲

- モジュール: `composeApp`
- ソースセット: `composeApp/src/androidMain/` のみ
  - Kotlin: `features/home/HomeScreen.kt`, `features/home/components/*.kt`, `theme/*.kt`（必要なら）
  - リソース: `composeApp/src/androidMain/res/drawable/` に新規 SVG/Vector Drawable を追加
- 破壊的変更: なし。`HomeScreen` の API シグネチャ（`viewModel`, `onTransactionHistory`, `onSendReceive`, `onShowToast`, `modifier`）は維持する
- 追加依存: 基本的に新規追加なし。Figma SVG をそのまま Vector Drawable に変換して取り込む方針なので追加ライブラリ不要

## 技術アプローチ

### Figma デザインの取得と反映

- `mcp plugin:figma:figma` の `get_design_context` を使い、`fileKey=bzm13wVWQmgaFFmlEbJZ3k`, `nodeId=709:8658` で実装参考スニペット + screenshot + design tokens を取得する
  - URL: https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=709-8658&m=dev
  - URL の `node-id=709-8658` は API 呼び出し時に `nodeId=709:8658` に変換する（`-` → `:`）
- `get_screenshot` で実際の見た目を確認、`get_metadata` でレイヤー構造を取得
- 出力された React+Tailwind は **参考のみ**。色・余白・フォントサイズを既存 `FujuBankColors` / `FujuBankTypography` に合わせて Compose で書き直す
- 新規アセット (SVG/PNG) はメモリ `reference_figma_assets_dir.md` に従い、まず `docs/figma-assets/` に書き出してから必要なものを `composeApp/src/androidMain/res/drawable/` に配置（Vector Drawable 化または PNG として配置）

### Compose 実装の修正

- 既存 `HomeScreen.kt` の構造（`Loading` / `Error` / `Loaded` の 3 状態 + `LoadedContent` 内で `FujuBankHeader` / `BalanceCard` / 「取引メニュー」見出し / `ActionTiles` を縦に並べる）は基本維持
- Figma との差分のみを反映:
  - 色トークン (`FujuBankColors.BrandPink` 等) の値を Figma に合わせる
  - 余白 (`padding` / `Arrangement.spacedBy`) を Figma の数値に合わせる
  - 新しいアイコン / 装飾要素を Figma SVG から追加
  - タイポグラフィ (`fontSize` / `fontWeight` / `lineHeight`) を Figma に合わせる
- ロジック (`HomeViewModel` / `HomeUiState`) は変更しない。デザイン適用に必要最小限の state 追加（例: 新しいトグル）が出た場合のみ ViewModel を更新

### Vector Drawable 警告への配慮

- 過去コミット `65222c5 chore(android): VectorPath lint 警告を tools:ignore で抑制` と同等の lint 警告が新規 Vector Drawable で出る場合、同じパターン（`tools:ignore="VectorPath"`）で抑制する

## 実装手順

1. Figma `709-8658` から `get_design_context` (fileKey: `bzm13wVWQmgaFFmlEbJZ3k`, nodeId: `709:8658`) で参考実装と screenshot、`get_metadata` でレイヤー構造を取得する
2. 必要な SVG/PNG アセットを Figma から書き出して `docs/figma-assets/709-8658/` に配置する
3. アセットを `composeApp/src/androidMain/res/drawable/` に Vector Drawable または PNG として取り込む
4. `theme/FujuBankColors.kt` / `theme/FujuBankTypography.kt` の値を Figma に合わせて更新（必要なら）
5. `features/home/components/FujuBankHeader.kt` を Figma 準拠に改修
6. `features/home/components/BalanceCard.kt` を Figma 準拠に改修（残高表示・マスクトグル・publicId 表示の見た目）
7. `features/home/components/ActionTiles.kt` を Figma 準拠に改修（4 アクションのアイコン・配置・色）
8. `HomeScreen.kt` の `LoadedContent` の余白・縦間隔を Figma に合わせて調整
9. Android Studio Preview で Loading / Error / Loaded 各状態の見た目を確認
10. `./gradlew :composeApp:assembleDebug` でビルド確認
11. Android 実機 / エミュレータで起動し、Figma screenshot と並べて差分を最終確認
12. `useDummyProfile=true` (`local.properties`) でバックエンド未起動でもホーム画面が動くことを確認

## 完了条件

- [ ] `./gradlew build` が通る
- [ ] `./gradlew :composeApp:assembleDebug` が通り、Android 実機/エミュレータで Figma `709-8658` 通りのホーム画面が表示される
- [ ] Loading / Error / Loaded の 3 状態がそれぞれ Figma 準拠で表示される
- [ ] 残高マスク表示トグル / 取引履歴遷移コールバック / 各アクションタイル押下のハンドラが従来通り動作する
- [ ] `useDummyProfile=true` でバックエンド未起動でもホーム画面が起動する
- [ ] `RootScaffold` のボトムナビから HomeScreen へ到達する経路が壊れていない
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が引き続き通る（Android 専用変更なので iOS framework link への影響はないはずだが念のため確認）

**iOS 動作確認は本タスクのスコープ外。Task 3 で対応する。**

## 想定される懸念・リスク

- **Figma の余白・色値が既存 `FujuBankColors` 体系と合わない**: 既存トークンの値を更新するか、新規トークンを追加するかの判断が必要。可能な限り既存トークン名を維持して値だけ差し替える方針（呼び出し側の影響を最小化）
- **Vector Drawable の lint 警告**: 過去コミット `65222c5` と同じく `tools:ignore="VectorPath"` で抑制
- **`qrose` / `BarcodeCard` のデザイン変更**: Figma で QR / バーコード表示部分のデザインが大きく変わる場合、既存の `qrose` ライブラリの描画オプションで対応可能か確認。難しい場合は本タスクでは描画ロジックは触らず見た目のラッパー（背景・余白）のみ Figma 準拠に
- **iOS 側の SwiftUI 実装との見た目乖離**: 本タスクで Android のみ Figma に追従するため、iOS 側 SwiftUI ホーム画面とは一時的に見た目が乖離する。これは Task 3 で commonMain 移植する際に解消される前提なので許容する

## 後続タスクへの引継ぎ

本タスクで Figma 準拠に確定した Android `HomeScreen` を、Task 3 (`client-bank-3-ios-multiplatform-integration.md`) で commonMain に移植する。Task 3 では framework 統合戦略の決定（案A/B/C の比較と選定）を行ったうえで、Android 実装をそのまま commonMain へ移し iOS でも同じ見た目を実現する。

## 参考リンク

- Figma ホーム画面 (本タスク対象): https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=709-8658&m=dev
- Figma 関連画面 (本タスク対象外): 
  - https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=697-7601
  - https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=702-6440
- 既存 Android 実装: `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/home/HomeScreen.kt`
- 関連コンポーネント: `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/home/components/`
- テーマ: `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/theme/`
- 前提タスク (revert 済み): [`client-ios-1-compose-multiplatform-foundation.md`](./client-ios-1-compose-multiplatform-foundation.md)
- 後続タスク: [`client-bank-3-ios-multiplatform-integration.md`](./client-bank-3-ios-multiplatform-integration.md)
- Figma アセット保存先ルール: メモリ `reference_figma_assets_dir.md` 参照（`docs/figma-assets/` 配下）
