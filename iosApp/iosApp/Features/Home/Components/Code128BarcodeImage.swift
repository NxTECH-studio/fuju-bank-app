import SwiftUI
import CoreImage.CIFilterBuiltins

/// `content` を CoreImage の `CICode128BarcodeGenerator` で Code128 にレンダリングして表示する。
///
/// Android 側はライブラリ未導入のため疑似縞模様で代替している。差し替え時はこちらの
/// 出力 (CIFilter の生バー幅) を基準に揃える想定。
struct Code128BarcodeImage: View {
    let content: String

    // CIContext は Metal pipeline 構築が重いので static 共有。レンダ結果は @State で
    // content キーにキャッシュして再描画毎の createCGImage を避ける。
    private static let context = CIContext()
    @State private var cachedImage: UIImage?

    var body: some View {
        Group {
            if let image = cachedImage {
                Image(uiImage: image)
                    .resizable()
                    .interpolation(.none)
                    .aspectRatio(contentMode: .fill)
            } else {
                Rectangle()
                    .fill(Color.gray.opacity(0.1))
            }
        }
        .task(id: content) {
            cachedImage = Self.generateImage(for: content)
        }
    }

    private static func generateImage(for content: String) -> UIImage? {
        guard let data = content.data(using: .ascii) else {
            // Code128 は ASCII 範囲のみ。fail 時は何も描かない。
            return nil
        }
        let generator = CIFilter.code128BarcodeGenerator()
        generator.message = data
        generator.quietSpace = 0
        guard let outputImage = generator.outputImage else { return nil }
        let scaled = outputImage.transformed(by: CGAffineTransform(scaleX: 4, y: 4))
        guard let cgImage = context.createCGImage(scaled, from: scaled.extent) else { return nil }
        return UIImage(cgImage: cgImage)
    }
}
