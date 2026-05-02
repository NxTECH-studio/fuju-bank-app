import SwiftUI
import CoreImage.CIFilterBuiltins

/// `content` を CoreImage の `CICode128BarcodeGenerator` で Code128 にレンダリングして表示する。
///
/// Android 側はライブラリ未導入のため疑似縞模様で代替している。差し替え時はこちらの
/// 出力 (CIFilter の生バー幅) を基準に揃える想定。
struct Code128BarcodeImage: View {
    let content: String

    private let context = CIContext()
    private let generator = CIFilter.code128BarcodeGenerator()

    var body: some View {
        if let image = generateImage() {
            Image(uiImage: image)
                .resizable()
                .interpolation(.none)
                .aspectRatio(contentMode: .fill)
        } else {
            Rectangle()
                .fill(Color.gray.opacity(0.1))
        }
    }

    private func generateImage() -> UIImage? {
        guard let data = content.data(using: .ascii) else {
            // Code128 は ASCII 範囲のみ。fail 時は何も描かない。
            return nil
        }
        generator.message = data
        generator.quietSpace = 0
        guard let outputImage = generator.outputImage else { return nil }
        let scaled = outputImage.transformed(by: CGAffineTransform(scaleX: 4, y: 4))
        guard let cgImage = context.createCGImage(scaled, from: scaled.extent) else { return nil }
        return UIImage(cgImage: cgImage)
    }
}
