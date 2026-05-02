import SwiftUI

/// ホーム画面の 4 アクション（取引履歴 / 送る・もらう / スキャン / チャージ）。
///
/// 各タイルは白い丸ボタン + 下にラベル。アイコンは SF Symbols を使い、色だけがカテゴリで変わる。
struct ActionTilesView: View {
    let onTransactionHistory: () -> Void
    let onSendReceive: () -> Void
    let onScan: () -> Void
    let onCharge: () -> Void

    var body: some View {
        HStack {
            tile(symbol: "clock.arrow.circlepath", tint: FujupayPalette.actionPurple, label: "取引履歴", action: onTransactionHistory)
            Spacer()
            tile(symbol: "paperplane", tint: FujupayPalette.actionGreen, label: "送る・もらう", action: onSendReceive)
            Spacer()
            tile(symbol: "qrcode.viewfinder", tint: FujupayPalette.brandPink, label: "スキャン", action: onScan)
            Spacer()
            tile(symbol: "plus.circle", tint: FujupayPalette.actionBlue, label: "チャージ", action: onCharge)
        }
        .frame(maxWidth: .infinity)
    }

    private func tile(symbol: String, tint: Color, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 8) {
                ZStack {
                    Circle()
                        .fill(FujupayPalette.surface)
                        .frame(width: 56, height: 56)
                    Image(systemName: symbol)
                        .font(.system(size: 24, weight: .regular))
                        .foregroundStyle(tint)
                }
                Text(label)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(FujupayPalette.textSecondary)
            }
        }
        .buttonStyle(.plain)
    }
}
