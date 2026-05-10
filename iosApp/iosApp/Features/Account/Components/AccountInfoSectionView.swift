import SwiftUI

/// アカウントハブ画面（Figma `697:8394`）の「アカウント情報」セクション。
///
/// Android `AccountInfoSection.kt` と 1:1。白角丸カード内に「公開ID」と「メールアドレス」の
/// 2 行を配置し、間に 1pt の hairline divider を挟む。各行右端に編集鉛筆アイコン
/// (`EditPencil`) を配置し、タップで対応するフィールド単独の編集シートを開く。
struct AccountInfoSectionView: View {
    let publicId: String
    let email: String
    let onEditPublicId: () -> Void
    let onEditEmail: () -> Void
    /// MVP では AuthCore に email/publicId 更新 API が無いため編集 UI を無効化する
    /// （`editable=false` で鉛筆アイコンごと非表示）。Android `AccountInfoSection` と対称。
    var editable: Bool = true

    var body: some View {
        VStack(spacing: 0) {
            row(
                label: "公開ID",
                value: publicId,
                onEditTap: onEditPublicId,
                editAccessibilityLabel: "公開IDを編集",
                editable: editable,
            )
            Divider()
                .frame(height: 1)
                .overlay(FujuBankPalette.hairline)
                .padding(.horizontal, 16)
            row(
                label: "メールアドレス",
                value: email,
                onEditTap: onEditEmail,
                editAccessibilityLabel: "メールアドレスを編集",
                editable: editable,
            )
        }
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(FujuBankPalette.surface)
        )
        .shadow(color: FujuBankPalette.shadowTint.opacity(0.08), radius: 4, x: 0, y: 2)
    }

    private func row(
        label: String,
        value: String,
        onEditTap: @escaping () -> Void,
        editAccessibilityLabel: String,
        editable: Bool,
    ) -> some View {
        HStack(alignment: .center, spacing: 8) {
            VStack(alignment: .leading, spacing: 2) {
                Text(label)
                    .font(FujuBankTypography.caption)
                    .foregroundStyle(FujuBankPalette.textTertiary)
                Text(value)
                    .font(FujuBankTypography.body)
                    .foregroundStyle(FujuBankPalette.textPrimary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            // MVP では編集 UI を無効化。鉛筆ボタンは将来の復活前提でコードを残し、
            // `editable=false` のときだけ非表示にする。
            if editable {
                Button(action: onEditTap) {
                    Image("EditPencil")
                        .renderingMode(.template)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 18, height: 18)
                        .foregroundStyle(FujuBankPalette.textTertiary)
                        .frame(width: 36, height: 36)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(editAccessibilityLabel)
            }
        }
        .padding(.leading, 16)
        .padding(.trailing, 8)
        .padding(.vertical, 8)
    }
}
