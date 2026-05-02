import SwiftUI

/// ホームヘッダー：左 48×48 空 / 中央 fujupay ロゴ（fuju キャラクター + ワードマーク）/
/// 右 通知ベル（赤ドット）。Figma `89:12356` 準拠。
struct FujuBankHeaderView: View {
    let onNotificationTap: () -> Void

    var body: some View {
        HStack {
            Color.clear.frame(width: 48, height: 48)
            Spacer()
            Image("FujuBankFullLogo")
                .resizable()
                .scaledToFit()
                .frame(height: 29)
            Spacer()
            NotificationBellButton(onTap: onNotificationTap)
        }
    }
}
