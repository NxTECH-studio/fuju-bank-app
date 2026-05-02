import SwiftUI

/// 取引履歴 / 送る・もらう など、A3 ではまだ未実装の画面の汎用プレースホルダ。
/// 戻るボタンで元のタブに復帰する。
struct ComingSoonView: View {
    let title: String
    let onBack: () -> Void

    var body: some View {
        ZStack {
            FujuBankPalette.background.ignoresSafeArea()
            VStack(spacing: 8) {
                Text(title)
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(FujuBankPalette.textPrimary)
                Text("この画面は別タスクで実装します")
                    .font(.system(size: 14))
                    .foregroundStyle(FujuBankPalette.textSecondary)
                Button(action: onBack) {
                    Text("戻る")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(FujuBankPalette.brandPink)
                        .padding(.top, 16)
                }
                .buttonStyle(.plain)
            }
            .padding(24)
        }
    }
}
