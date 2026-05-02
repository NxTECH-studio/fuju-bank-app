import SwiftUI
import Shared

/// バーコード / QR / 残高 / 表示トグルをまとめた白いカード。Figma `89:12356` 準拠。
struct BalanceCardView: View {
    let publicId: String
    let balanceFuju: Int64
    let revealed: Bool
    let onToggleReveal: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Code128BarcodeImage(content: publicId)
                .frame(height: 63)
                .frame(maxWidth: .infinity)
            HStack(spacing: 12) {
                QRCodeImage(content: publicId)
                    .frame(width: 66, height: 66)
                VStack(alignment: .leading, spacing: 12) {
                    Text("現在の残高")
                        .font(.system(size: 12, weight: .regular))
                        .foregroundStyle(FujupayPalette.textSecondary)
                    // SF Pro は数字+カンマ+ハイフンが幅広で、20pt のままだと 1 行に
                    // 収まらず改行されることがある。lineLimit(1) で改行を防ぎ、
                    // minimumScaleFactor で必要なら自動縮小する。
                    HStack(alignment: .lastTextBaseline, spacing: 5) {
                        Text(displayValue)
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundStyle(Color.black)
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                        Text("ふじゅ〜")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundStyle(Color.black)
                            .lineLimit(1)
                            .fixedSize()
                    }
                }
                Spacer()
                Button(action: onToggleReveal) {
                    Text(revealed ? "隠す" : "表示")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(FujupayPalette.brandPink)
                        .padding(.horizontal, 11)
                        .padding(.vertical, 7)
                        .background(FujupayPalette.brandPink.opacity(0.1))
                        .clipShape(RoundedRectangle(cornerRadius: 35))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(30)
        .frame(maxWidth: .infinity)
        .background(FujupayPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 32))
        .shadow(color: Color(red: 30/255, green: 34/255, blue: 42/255).opacity(0.02), radius: 6, x: 0, y: 4)
    }

    private var displayValue: String {
        if revealed {
            return BalanceFormatterKt.formatBalanceFuju(value: balanceFuju)
        }
        return BalanceFormatterKt.maskedBalance()
    }
}
