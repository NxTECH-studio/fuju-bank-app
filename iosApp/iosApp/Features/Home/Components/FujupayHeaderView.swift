import SwiftUI

/// ホームヘッダー：左 48×48 空 / 中央 fujupay ロゴ（fuju キャラクター + ワードマーク）/
/// 右 通知ベル（赤ドット）。Figma `89:12356` 準拠。
struct FujupayHeaderView: View {
    let onNotificationTap: () -> Void

    var body: some View {
        HStack {
            Color.clear.frame(width: 48, height: 48)
            Spacer()
            Image("FujupayFullLogo")
                .resizable()
                .scaledToFit()
                .frame(height: 29)
            Spacer()
            Button(action: onNotificationTap) {
                ZStack(alignment: .topTrailing) {
                    Image("BellOutline")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 24, height: 24)
                    // 赤ドット（白縁付き）。Figma の circle r=3.5 stroke 2 相当。
                    Circle()
                        .fill(FujupayPalette.background)
                        .frame(width: 9, height: 9)
                        .overlay(
                            Circle()
                                .fill(Color.red)
                                .frame(width: 6, height: 6)
                        )
                        .offset(x: 4, y: -2)
                }
                .frame(width: 48, height: 48)
            }
            .buttonStyle(.plain)
        }
    }
}
