import SwiftUI

/// 取引履歴 / 送る・もらう など、A3 ではまだ未実装の画面の汎用プレースホルダ。
/// 戻るボタンで元のタブに復帰する。
struct ComingSoonView: View {
    let title: String
    let onBack: () -> Void

    var body: some View {
        ZStack {
            FujupayPalette.background.ignoresSafeArea()
            VStack(spacing: 8) {
                Text(title)
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(FujupayPalette.textPrimary)
                Text("この画面は別タスクで実装します")
                    .font(.system(size: 14))
                    .foregroundStyle(FujupayPalette.textSecondary)
                Button(action: onBack) {
                    Text("戻る")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(FujupayPalette.brandPink)
                        .padding(.top, 16)
                }
                .buttonStyle(.plain)
            }
            .padding(24)
        }
    }
}
