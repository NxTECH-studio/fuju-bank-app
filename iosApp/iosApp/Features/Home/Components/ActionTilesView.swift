import SwiftUI

/// ホーム画面の 4 アクション（取引履歴 / 送る・もらう / スキャン / チャージ）。Figma `89:12356` 準拠。
///
/// 各タイルは白い丸角矩形 (rounded 20)、内側に 28pt のブランドカラーアイコン + 10pt Bold ラベル。
/// アイコンの SVG はブランドカラーが焼き込み済みのため `Image(...).renderingMode(.original)` で描画。
struct ActionTilesView: View {
    let onTransactionHistory: () -> Void
    let onSendReceive: () -> Void
    let onScan: () -> Void
    let onCharge: () -> Void

    var body: some View {
        HStack(spacing: 4) {
            tile(image: "ActionHistory", labelColor: FujupayPalette.actionPurple, label: "取引履歴", action: onTransactionHistory)
            tile(image: "ActionSend", labelColor: FujupayPalette.actionGreen, label: "送る・もらう", action: onSendReceive)
            tile(image: "ActionScan", labelColor: FujupayPalette.brandPink, label: "スキャン", action: onScan)
            tile(image: "ActionCharge", labelColor: FujupayPalette.actionBlue, label: "チャージ", action: onCharge)
        }
        .frame(maxWidth: .infinity)
    }

    private func tile(image: String, labelColor: Color, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 4) {
                // Figma CSS 仕様: width: 28pt / height: 28pt / aspect-ratio: 1/1
                // SVG は viewBox 32x32 + group transform で正方化済みなので 28pt 枠で
                // 4 つ均一に描画される。
                Image(image)
                    .resizable()
                    .renderingMode(.original)
                    .scaledToFit()
                    .frame(width: 28, height: 28)
                Text(label)
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(labelColor)
            }
            .frame(maxWidth: .infinity)
            .padding(.top, 6)
            .padding(.bottom, 14)
            .background(FujupayPalette.surface)
            .clipShape(RoundedRectangle(cornerRadius: 20))
        }
        .buttonStyle(.plain)
    }
}
