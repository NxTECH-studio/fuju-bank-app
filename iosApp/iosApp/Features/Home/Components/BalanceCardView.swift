import SwiftUI
import Shared

/// バーコード / QR / 残高 / 表示トグルをまとめた白いカード。Figma `89:12356` 準拠。
struct BalanceCardView: View {
    let publicId: String
    let balanceFuju: Int64
    let revealed: Bool
    let onToggleReveal: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            Code128BarcodeImage(content: publicId)
                .frame(height: 63)
                .frame(maxWidth: .infinity)
            HStack(spacing: 16) {
                QRCodeImage(content: publicId)
                    .frame(width: 66, height: 66)
                VStack(alignment: .leading, spacing: 2) {
                    Text("現在の残高")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(FujupayPalette.textSecondary)
                    HStack(alignment: .lastTextBaseline, spacing: 4) {
                        Text(displayValue)
                            .font(.system(size: 22, weight: .bold))
                            .foregroundStyle(FujupayPalette.textPrimary)
                        Text("円")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(FujupayPalette.textPrimary)
                    }
                }
                Spacer()
                Button(action: onToggleReveal) {
                    Text(revealed ? "隠す" : "表示")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(FujupayPalette.textSecondary)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(FujupayPalette.background)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 24)
        .frame(maxWidth: .infinity)
        .background(FujupayPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 32))
        .shadow(color: Color.black.opacity(0.06), radius: 12, x: 0, y: 4)
    }

    private var displayValue: String {
        if revealed {
            return BalanceFormatterKt.formatBalanceFuju(value: balanceFuju)
        }
        return BalanceFormatterKt.maskedBalance()
    }
}
