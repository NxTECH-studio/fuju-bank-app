# アプリアイコン設定とアプリ名「ふじゅ〜」化

## 概要

Figma で確定したアプリアイコン (node-id=216-2572) を Android / iOS 両プラットフォームに設定し、ホーム画面の表示名を「ふじゅ〜」に統一する。Bundle ID / Application ID は変更しない。

## 背景・目的

- 現状アプリアイコンは KMP テンプレ標準（Android は緑グリッドの adaptive icon、iOS は `AppIcon.appiconset` のスロットだけ用意され画像未配置）で、プロダクトのブランドが反映されていない。
- アプリ名も Android = `Fujubankapp`、iOS = `PRODUCT_NAME=Fujubankapp` と仮置きのままで、ホーム画面でブランド名が見えない。
- アイコン名「ふじゅ〜」は最終決定に近く、これを反映することでビルドして実機/シミュレータに置いた状態のブランド体験を確認できるようにする。

## 影響範囲

- モジュール: `composeApp`（Android）, `iosApp`（iOS）
- ソースセット: Android リソース (`composeApp/src/androidMain/res/`)、iOS リソース (`iosApp/iosApp/Assets.xcassets/`, `iosApp/iosApp/Info.plist`, `iosApp/Configuration/Config.xcconfig`)
- 共通コード (`commonMain` / `shared`) への変更なし
- 破壊的変更:
  - 公開 API / Shared framework ABI 変更なし
  - Application ID / Bundle ID 変更なし（`studio.nxtech.fujubank` / `studio.nxtech.fujubank.Fujubankapp$(TEAM_ID)` を維持）
  - 表示名のみ「ふじゅ〜」に変わる（OS インストール済みの旧名アプリは上書きインストール時にアイコン・名前が差し替わる挙動）
- 追加依存: なし（`gradle/libs.versions.toml` は変更しない）

## 前提と確定済みの方針

- 表示名は全角チルダで「ふじゅ〜」（U+301C ではなく macOS で打鍵される全角チルダを使用、Figma 表記準拠）
- 内部 Bundle 名 / 識別子に使うアルファベット表記は `fuju`（小文字）。ただし今回は識別子変更を行わないため、「内部に `Fujubankapp` 文字列が残ることは許容」する
- Android アイコン: **Adaptive Icon**（`mipmap-anydpi-v26/ic_launcher.xml` の `<background>` / `<foreground>` レイヤー分離）方式を採用
  - Mono アイコン（テーマ付き）/ レガシー Pre-O 個別 PNG は今回スコープ外。`mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher.png` および `ic_launcher_round.png` はテンプレ生成のラスタを Figma のアイコン PNG で置換するのみとする
- iOS アイコン: **Single Size 方式**（1024×1024 マスター 1 枚を `AppIcon.appiconset` に配置）
  - 既存 `Contents.json` は Light / Dark / Tinted の 3 スロット構成。Light スロットのみ画像を入れ、Dark / Tinted のスロットは Light と同じ画像を共用する（ファイル分けはしない、Contents.json を 3 ファイル参照に揃える）
- 検証は **ビルド成功 + Android エミュレータ / iOS シミュレータでアイコンとアプリ名「ふじゅ〜」が表示されること** を目視確認

## 実装ステップ

### 1. Figma からアセット取得

1. Figma MCP (`mcp__figma-desktop__get_design_context` / `get_screenshot`) で node-id=216-2572 の構成を確認
2. 必要な画像を書き出す:
   - **Android Adaptive Icon foreground** (透過 PNG, 432×432px 相当を 108dp の vector または `mipmap-xxxhdpi` の 432×432 PNG として)
   - **Android Adaptive Icon background** （単色なら `drawable/ic_launcher_background.xml` を `<color>` 1 つの vector に置換、画像背景なら同サイズ PNG）
   - **Android legacy ラウンド/スクエア用ラスタ** (`mipmap-{m=48,h=72,xh=96,xxh=144,xxxh=192}dpi/ic_launcher.png` および `ic_launcher_round.png`)
   - **iOS マスター画像** (`AppIcon-1024.png`, 1024×1024px, アルファ無しの sRGB PNG)
3. 一時保存先: `composeApp/src/androidMain/res/...` / `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/` に直接配置

### 2. Android アイコン差し替え

1. `composeApp/src/androidMain/res/drawable/ic_launcher_background.xml` を Figma のブランド背景色に置換
   - 単色なら `<vector>` 内に `<path android:fillColor="#XXXXXX" android:pathData="M0,0h108v108h-108z"/>` のみ残す形でテンプレのグリッド線を全削除
2. `composeApp/src/androidMain/res/drawable/ic_launcher_foreground.xml`（または `mipmap-anydpi-v26/ic_launcher.xml` が参照する foreground）を Figma の前景に置換
   - vector 化が困難なら `mipmap-xxxhdpi/ic_launcher_foreground.png` (432×432) を配置し `<foreground android:drawable="@mipmap/ic_launcher_foreground"/>` に書き換え
3. `mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher.png` と `ic_launcher_round.png` を Figma 由来の PNG で置換
4. `mipmap-anydpi-v26/ic_launcher.xml` / `ic_launcher_round.xml` の参照は既存のままで OK（drawable 中身を入れ替えれば反映される）

### 3. Android アプリ名差し替え

1. `composeApp/src/androidMain/res/values/strings.xml` の `app_name` を `Fujubankapp` から `ふじゅ〜` に変更
2. `AndroidManifest.xml` の `android:label="@string/app_name"` は変更不要（既に参照済み）
3. （任意）`MainActivity` の `android:label` を明示していないことを確認 → 確認済み、対応不要

### 4. iOS アイコン差し替え

1. `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/` に `AppIcon-1024.png` を配置
2. `Contents.json` を以下に書き換え（Light / Dark / Tinted の 3 スロットすべて同じファイルを参照）:
   ```json
   {
     "images" : [
       { "idiom" : "universal", "platform" : "ios", "size" : "1024x1024", "filename" : "AppIcon-1024.png" },
       { "appearances" : [ { "appearance" : "luminosity", "value" : "dark" } ], "idiom" : "universal", "platform" : "ios", "size" : "1024x1024", "filename" : "AppIcon-1024.png" },
       { "appearances" : [ { "appearance" : "luminosity", "value" : "tinted" } ], "idiom" : "universal", "platform" : "ios", "size" : "1024x1024", "filename" : "AppIcon-1024.png" }
     ],
     "info" : { "author" : "xcode", "version" : 1 }
   }
   ```
   - Dark / Tinted を専用画像に差し替えたくなったら後続タスクで対応（今回はスコープ外）

### 5. iOS アプリ名差し替え（Bundle ID は維持）

1. `iosApp/Configuration/Config.xcconfig` を以下に修正
   - `PRODUCT_NAME` を維持しつつ Bundle ID を切り離す:
     ```
     TEAM_ID=

     PRODUCT_NAME=Fujubankapp
     PRODUCT_BUNDLE_IDENTIFIER=studio.nxtech.fujubank.Fujubankapp$(TEAM_ID)

     CURRENT_PROJECT_VERSION=1
     MARKETING_VERSION=1.0
     ```
   - Bundle ID 変更スコープ外のため `PRODUCT_NAME` はそのまま `Fujubankapp` を維持（`.app` バンドル名と Bundle ID 末尾を変えない）
2. `iosApp/iosApp/Info.plist` に `CFBundleDisplayName` を追加してホーム画面表示名のみ差し替える:
   ```xml
   <key>CFBundleDisplayName</key>
   <string>ふじゅ〜</string>
   ```
   - `CFBundleName` は明示しなくても `PRODUCT_NAME` から `$(PRODUCT_NAME)` で埋まる。既存挙動を変えないため追加しない
3. `Info.plist` の `UILaunchScreen` 既存ブロックには触れない

### 6. 検証

1. `./gradlew :composeApp:assembleDebug` が通る
2. Android エミュレータにインストールし、ホーム画面でブランドアイコン + 「ふじゅ〜」表示を目視確認
3. Xcode で `iosApp` を Run（iPhone 15 / iOS 17+ シミュレータ）し、ホーム画面でブランドアイコン + 「ふじゅ〜」表示を目視確認
4. `./gradlew build` が成功する
5. （shared 影響なし想定だが安全のため）`./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が通ることを確認

## 検証チェックリスト

- [ ] `./gradlew :composeApp:assembleDebug` が通る
- [ ] Android エミュレータでホーム画面アイコンが Figma デザイン通り表示される
- [ ] Android エミュレータでアプリ名が「ふじゅ〜」と表示される
- [ ] iOS シミュレータでホーム画面アイコンが Figma デザイン通り表示される
- [ ] iOS シミュレータでアプリ名が「ふじゅ〜」と表示される
- [ ] `./gradlew build` が通る
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が通る
- [ ] Application ID (`studio.nxtech.fujubank`) / iOS Bundle ID (`studio.nxtech.fujubank.Fujubankapp$(TEAM_ID)`) が変わっていない

## 技術的な補足

- **Bundle ID とアプリ表示名の分離（iOS）**: 既存の `PRODUCT_BUNDLE_IDENTIFIER` は `$(TEAM_ID)` 連結で個人開発時の衝突回避を行う構成。`PRODUCT_NAME` を変えると `.app` バンドル名と Bundle ID 末尾の両方が連動するため、表示名だけ変える今回は `PRODUCT_NAME` を据え置き、`Info.plist` の `CFBundleDisplayName` を新設して差し替える。これは「アプリ ID は不変、ホーム画面ラベルだけ多言語/別名」という iOS の標準パターン。
- **Adaptive Icon（Android）**: `mipmap-anydpi-v26/ic_launcher.xml` で foreground/background レイヤーを分離するのが Android 8.0 以降の正攻法。今回は既存 XML の参照先 drawable を置換するだけで OS 側のマスク（円・squircle 等）に追従できる。Mono（テーマ付き）アイコンは Android 13+ のオプション機能で、今回は対応しない。
- **「ふじゅ〜」の文字コード**: 全角チルダ。`strings.xml` / `Info.plist` ともに UTF-8 で素直に埋め込む。エスケープ不要。
- **Figma 由来アセットの置き場**: Android は素直に `composeApp/src/androidMain/res/mipmap-*` 配下に置く（`commonMain/composeResources/` ではない、ランチャアイコンは Android リソースシステムに入れる必要がある）。iOS は `Assets.xcassets/AppIcon.appiconset/` に置く。
- **アイコン素材取得時の注意**: Figma の export で角丸が含まれていると iOS 側で二重丸になる。**1024×1024 マスターは角丸なし・透過なし** で書き出すこと。Android Adaptive Icon foreground は中央 66dp 内に主要素を収めるセーフエリアに注意。

## ブランチ・コミット運用メモ

- ブランチ名提案: `feature/<task-id>-set-app-icon-and-display-name`
  - `<task-id>` は Notion タスク DB のクライアント側プレフィックス（記憶 `reference_notion_task_db.md` 参照）に従って採番。`/start-with-plan` 実行時にタスク ID を確定する
- PR は必須（main 直コミット禁止）。レビュー強制は不要（ソロ開発）
- コミットメッセージ例（日本語、Conventional Commits の prefix のみ英語）:
  - `feat(android): アプリアイコンを Figma デザインに差し替え`
  - `feat(android): アプリ表示名を「ふじゅ〜」に変更`
  - `feat(ios): AppIcon を Figma デザインに差し替え`
  - `feat(ios): CFBundleDisplayName を「ふじゅ〜」に設定`
