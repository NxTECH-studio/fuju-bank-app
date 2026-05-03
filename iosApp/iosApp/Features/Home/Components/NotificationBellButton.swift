import SwiftUI

/// 通知ベル（48pt タップ領域 + 24pt アイコン + 赤ドット）。Figma 銀行版 `709:8658` /
/// `697:7601` / `702:6440` 共通の右上ヘッダーアイコン。
///
/// アイコンは Figma `notification-bell.svg`（ベル本体は `#B0B0B0`、赤ドット円は内蔵）
/// をそのまま `NotificationBell` アセットとして使用する。SVG に赤ドットが含まれている
/// ため、Swift 側で別途オーバーレイは描かない。
///
/// 既存 `BellOutline` と異なり、銀行版は赤ドットの色 / 縁の太さが Figma 仕様で固定済み
/// なので、独自の重ね描きをしないほうが見た目が一致する。
struct NotificationBellButton: View {
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            Image("NotificationBell")
                .resizable()
                .renderingMode(.original)
                .scaledToFit()
                .frame(width: 24, height: 24)
                .frame(width: 48, height: 48)
        }
        .buttonStyle(.plain)
    }
}
