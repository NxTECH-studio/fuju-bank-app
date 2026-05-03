import SwiftUI

/// アカウントハブ画面（Figma `697:8394`）のプロフィールカード。
///
/// 構成は Android `ProfileCard.kt` と 1:1:
/// - 白背景・角丸 20pt・薄影
/// - 上段: 64pt の円形アバター（左寄せ）
/// - 中段: 表示名 + 編集鉛筆アイコン
/// - 下段: 「ID: xxxxxxxxxxxxx」のグレー小文字
struct ProfileCardView: View {
    let displayName: String
    let accountId: String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // 円形アバター（64pt 固定）
            ZStack {
                Circle().fill(FujuBankPalette.surface)
                Image("AvatarTomato")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 64, height: 64)
            }
            .frame(width: 64, height: 64)
            .accessibilityElement()
            .accessibilityLabel("プロフィールアバター")

            HStack(spacing: 8) {
                Text(displayName)
                    .font(.system(size: 22, weight: .bold))
                    .foregroundStyle(FujuBankPalette.textPrimary)
                Image("EditPencil")
                    .renderingMode(.template)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 18, height: 18)
                    .foregroundStyle(FujuBankPalette.textPrimary)
                    .accessibilityLabel("表示名を編集")
            }

            Text("ID: \(accountId)")
                .font(FujuBankTypography.caption)
                .foregroundStyle(FujuBankPalette.textTertiary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16)
        .padding(.vertical, 16)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(FujuBankPalette.surface)
        )
        .shadow(color: FujuBankPalette.shadowTint.opacity(0.08), radius: 4, x: 0, y: 2)
    }
}
