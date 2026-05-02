import SwiftUI
import CoreImage.CIFilterBuiltins

/// `content` を CoreImage の `CIQRCodeGenerator` で QR にレンダリングして表示する。
///
/// L レベル / margin: 0 を Android (qrose) と揃える方針だが、CIFilter は内部で
/// 4 モジュール分の quiet zone を持つ。完全な見た目一致は難しいため、まずは
/// `correctionLevel = "L"` のみ揃える。
struct QRCodeImage: View {
    let content: String
    var foregroundColor: Color = FujupayPalette.textPrimary
    var backgroundColor: Color = .white

    private let context = CIContext()
    private let generator = CIFilter.qrCodeGenerator()

    var body: some View {
        if let image = generateImage() {
            Image(uiImage: image)
                .resizable()
                .interpolation(.none)
                .scaledToFit()
        } else {
            // 失敗時は空のグレー矩形でフォールバック。
            Rectangle()
                .fill(Color.gray.opacity(0.1))
        }
    }

    private func generateImage() -> UIImage? {
        guard let data = content.data(using: .utf8) else { return nil }
        generator.message = data
        generator.correctionLevel = "L"
        guard let outputImage = generator.outputImage else { return nil }

        // ぼやけ防止のため拡大してから cgImage 化する。
        let scaled = outputImage.transformed(by: CGAffineTransform(scaleX: 8, y: 8))
        guard let cgImage = context.createCGImage(scaled, from: scaled.extent) else { return nil }
        return UIImage(cgImage: cgImage)
    }
}
