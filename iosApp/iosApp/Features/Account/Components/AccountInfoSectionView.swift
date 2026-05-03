import SwiftUI

/// アカウントハブ画面（Figma `697:8394`）の「アカウント情報」セクション。
///
/// Android `AccountInfoSection.kt` と 1:1。白角丸カード内に「表示名」と「メールアドレス」の
/// 2 行を配置し、間に 1pt の hairline divider を挟む。
struct AccountInfoSectionView: View {
    let displayName: String
    let email: String

    var body: some View {
        VStack(spacing: 0) {
            row(label: "表示名", value: displayName)
            Divider()
                .frame(height: 1)
                .overlay(FujuBankPalette.hairline)
                .padding(.horizontal, 16)
            row(label: "メールアドレス", value: email)
        }
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(FujuBankPalette.surface)
        )
        .shadow(color: FujuBankPalette.shadowTint.opacity(0.08), radius: 4, x: 0, y: 2)
    }

    private func row(label: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(FujuBankTypography.caption)
                .foregroundStyle(FujuBankPalette.textTertiary)
            Text(value)
                .font(FujuBankTypography.body)
                .foregroundStyle(FujuBankPalette.textPrimary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }
}
