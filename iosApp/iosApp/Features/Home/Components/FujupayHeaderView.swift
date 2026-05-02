import SwiftUI

/// ホームヘッダー：左 48×48 空 / 中央 fujupay ロゴ / 右 通知ベル（赤ドット付き）。
///
/// fujupay ロゴはサインアップ画面と共通の `FujuLogo` を再利用。
///
/// TODO(asset): Figma `89:12356` の fujupay ロゴ（fuju キャラクター + テキスト）に差し替え。
struct FujupayHeaderView: View {
    let onNotificationTap: () -> Void

    var body: some View {
        HStack {
            Color.clear.frame(width: 48, height: 48)
            Spacer()
            Image("FujuLogo")
                .resizable()
                .scaledToFit()
                .frame(height: 28)
            Spacer()
            Button(action: onNotificationTap) {
                ZStack(alignment: .topTrailing) {
                    Image(systemName: "bell")
                        .font(.system(size: 22, weight: .regular))
                        .foregroundStyle(FujupayPalette.textPrimary)
                    Circle()
                        .fill(FujupayPalette.brandPink)
                        .frame(width: 8, height: 8)
                        .offset(x: 2, y: -2)
                }
                .frame(width: 48, height: 48)
            }
            .buttonStyle(.plain)
        }
    }
}
