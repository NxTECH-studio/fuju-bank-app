import SwiftUI
import Shared

/// 残高カード — Figma `709:8658` 準拠のシンプル表示版。
///
/// - 高さ 154pt、白背景、角丸 32、わずかな drop-shadow。
/// - 左寄せに「現在の残高」ラベル(14pt medium) と、48pt Bold の数値 + 20pt Bold の単位
///   「ふじゅ〜」をベースライン揃えで横並び。
/// - 旧 fujupay デザインにあった QR / バーコード / マスクトグル / publicId 表示は新銀行版で撤去。
struct BalanceCardView: View {
    let balanceFuju: Int64

    var body: some View {
        HStack(alignment: .center, spacing: 0) {
            VStack(alignment: .leading, spacing: 12) {
                Text("現在の残高")
                    .font(FujuBankTypography.body)
                    .foregroundStyle(FujuBankPalette.textPrimary)

                HStack(alignment: .lastTextBaseline, spacing: 8) {
                    Text(CurrencyFormatter.shared.formatAmount(amount: balanceFuju))
                        .font(FujuBankTypography.amount)
                        .foregroundStyle(FujuBankPalette.textPrimary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.5)
                    Text(CurrencyFormatter.shared.UNIT)
                        .font(FujuBankTypography.amountUnit)
                        .foregroundStyle(FujuBankPalette.textPrimary)
                        .padding(.bottom, 6)
                        .fixedSize()
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 36)
        .padding(.bottom, 6)
        .frame(maxWidth: .infinity)
        .frame(height: 154)
        .background(FujuBankPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 32))
        .shadow(color: FujuBankPalette.shadowTint.opacity(0.06), radius: 4, x: 0, y: 2)
    }
}
