import SwiftUI
import CoreImage.CIFilterBuiltins

/// `content` を CoreImage の `CIQRCodeGenerator` で QR にレンダリングして表示する。
///
/// L レベル / margin: 0 を Android (qrose) と揃える方針だが、CIFilter は内部で
/// 4 モジュール分の quiet zone を持つ。完全な見た目一致は難しいため、まずは
/// `correctionLevel = "L"` のみ揃える。
struct QRCodeImage: View {
    let content: String
    var foregroundColor: Color = FujuBankPalette.textPrimary
    var backgroundColor: Color = .white

    // SwiftUI の View は値型で再生成が頻繁に走るため、Metal pipeline 構築コストの大きい
    // CIContext は static で共有する。レンダ結果は @State で content キーにキャッシュし、
    // 再描画毎の createCGImage を回避する。
    private static let context = CIContext()
    @State private var cachedImage: UIImage?

    var body: some View {
        Group {
            if let image = cachedImage {
                Image(uiImage: image)
                    .resizable()
                    .interpolation(.none)
                    .scaledToFit()
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
        guard let data = content.data(using: .utf8) else { return nil }
        let generator = CIFilter.qrCodeGenerator()
        generator.message = data
        generator.correctionLevel = "L"
        guard let outputImage = generator.outputImage else { return nil }

        // ぼやけ防止のため拡大してから cgImage 化する。
        let scaled = outputImage.transformed(by: CGAffineTransform(scaleX: 8, y: 8))
        guard let cgImage = context.createCGImage(scaled, from: scaled.extent) else { return nil }
        return UIImage(cgImage: cgImage)
    }
}
