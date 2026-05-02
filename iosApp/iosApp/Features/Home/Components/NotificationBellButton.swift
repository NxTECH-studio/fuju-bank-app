import SwiftUI

/// 通知ベル（48pt タップ領域 + 24pt アイコン + 赤ドット）。
///
/// ホーム画面・取引履歴画面どちらからも参照される共通コンポーネント。
/// Figma では `89:12356` (home) と `410:20343` (transaction history) の右上に配置される。
struct NotificationBellButton: View {
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
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
