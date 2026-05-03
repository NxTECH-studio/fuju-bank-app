import SwiftUI

/// ホーム画面ヘッダー — Figma `709:8658` 準拠。
///
/// 構成: 左 48pt 空 / 中央「fuju 銀行」ロゴ + 下向き chevron / 右 通知ベル。
/// Figma の chevron は chevron-right を 90 度回転して下向きにしているため、Android と同じく
/// `ChevronRight` を 90 度回転して再利用する。
struct FujuBankHeaderView: View {
    let onNotificationTap: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            // 左 48pt のダミー領域: 中央ロゴを画面中央に押し出すため右側の通知ベルと
            // 同サイズの空を確保する。
            Color.clear.frame(width: 48, height: 48)
            Spacer()

            HStack(spacing: 2) {
                Image("LogoFujuBank")
                    .resizable()
                    .renderingMode(.original)
                    .scaledToFit()
                    .frame(height: 28)
                Image("ChevronRight")
                    .renderingMode(.template)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 14, height: 14)
                    .rotationEffect(.degrees(90))
                    .foregroundStyle(FujuBankPalette.textPrimary)
            }

            Spacer()
            NotificationBellButton(onTap: onNotificationTap)
        }
        .frame(height: 48)
    }
}
