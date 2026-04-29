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

- iOS: `iosApp/iosApp/Assets.xcassets/FujuLogo.imageset/FujuLogo.svg`
  - `logo-icon.svg` + `logo-wordmark.svg` を viewBox `0 0 195 51.354` に合成（Figma の合成サイズに合わせる）
  - `preserves-vector-representation: true` で Asset Catalog 経由に登録
- Android: `composeApp/src/androidMain/res/drawable/fuju_logo.xml`
  - `logo-icon.svg` のみを Material splash icon 用 vector drawable (108dp viewport, 中央 64dp) に変換
  - ワードマークは `windowSplashScreenAnimatedIcon` の単一アイコン制約で省略

## 注意

- Vector Drawable は SVG の `<filter>` (inner shadow) と CSS `var()` を扱えないため、Android 側ではこれらを除いた簡略版を採用。
- 背景色は Figma の `#F6F7F9`。両プラットフォームとも `FujuSplashBackground.colorset` / `colors.xml fuju_splash_bg` で参照。
- Figma が更新された場合は `mcp__figma-desktop__get_design_context` で再 export し、本ディレクトリと派生ファイルを同時に更新する。
