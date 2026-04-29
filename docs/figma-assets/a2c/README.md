# A2c 起動スプラッシュ Figma 素材

Figma node 175-2457 (`https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=175-2457`) から
export した SVG を Git に保持し、再変換時の真実源とする。

## ファイル

| ファイル | 用途 | viewBox |
| :-- | :-- | :-- |
| `logo-icon.svg` | アイコン（ピンクの角丸正方形 + 白の "f" グリフ） | 0 0 51.3536 51.3536 |
| `logo-wordmark.svg` | ワードマーク（"fuju pay" 文字、`#111111`） | 0 0 133.176 36.5373 |
| `decoration.svg` | 背景の薄いグレー装飾（"8" 形）。launch screen には未使用、SwiftUI Splash で再導入する場合の参考 | 0 0 252.099 351.986 |

## 派生先

OS splash (Material splash screen / Info.plist UILaunchScreen) は中央正方形アイコン 1 枚しか
描画できないため、Figma 通りの合成は OS splash ではなく in-app の Compose / SwiftUI Splash
画面で行う。OS splash は背景色のみのフラッシュとして使う。

### iOS

- `iosApp/iosApp/Assets.xcassets/FujuLogo.imageset/FujuLogo.svg`
  - `logo-icon.svg` + `logo-wordmark.svg` を viewBox `0 0 195 51.354` に合成
  - `preserves-vector-representation: true` で Asset Catalog 経由に登録
  - `Info.plist UILaunchScreen` と SwiftUI `SplashView` の両方から参照
- `iosApp/iosApp/Assets.xcassets/FujuSplashDecoration.imageset/FujuSplashDecoration.svg`
  - `decoration.svg` を Asset Catalog 用にクリーンアップ（`var(--fill-0, #EEEFF1)` を直接 `#EEEFF1` に置換、`preserveAspectRatio="none"` 等の不要属性を除去）
  - SwiftUI `SplashView` でロゴ背後に重ねて表示

### Android

- `composeApp/src/androidMain/res/drawable/fuju_logo.xml`
  - `logo-icon.svg` + `logo-wordmark.svg` を viewBox `0 0 195 51.354` に合成した vector drawable
  - radial gradient (`#FF1E9E` → `#FB0F95`) は `<aapt:attr>` で再現
  - inner shadow filter は Vector Drawable 非対応のため省略
  - Compose `SplashScreen` で参照（OS splash icon は無効化済み）
- `composeApp/src/androidMain/res/drawable/fuju_splash_decoration.xml`
  - `decoration.svg` を 252.099x351.986 viewport の vector drawable に変換
  - `fillType="evenOdd"` でリングを表現、内側を `#F6F7F9` で塗りつぶし重なり時の不透明度を担保
  - Compose `SplashScreen` で参照
- `composeApp/src/androidMain/res/values/themes.xml` の `Theme.App.Starting`
  - `windowSplashScreenAnimatedIcon` を `@android:color/transparent` に設定し、OS splash の中央アイコンを抑止
  - 背景色 `windowSplashScreenBackground` のみで OS splash を表示

## 注意

- Vector Drawable は SVG の `<filter>` (inner shadow) と CSS `var()` を扱えないため、Android 側はこれらを除いた版を採用。
- iOS Asset Catalog の SVG レンダラも CSS `var()` を解釈しないため、Figma export の `var(--fill-0, ...)` は直接リテラル色に置換すること。
- 背景色は Figma の `#F6F7F9`。両プラットフォームとも `FujuSplashBackground.colorset` / `colors.xml fuju_splash_bg` で参照。
- Figma が更新された場合は `mcp__figma-desktop__get_design_context` で再 export し、本ディレクトリと派生ファイル（4 種：iOS 2 + Android 2）を同時に更新する。
