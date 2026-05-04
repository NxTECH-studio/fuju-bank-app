import SwiftUI

/// プライバシーポリシー / 利用規約など、shared `PrivacyContent` で凍結された法的文書本文を
/// アプリ内で表示する汎用画面。Figma `798:12559` の「法的情報」セクションのドリルダウン先。
/// Android `LegalDocumentScreen` と 1:1。
///
/// ヘッダーは `PrivacySettingsView` / `NotificationSettingsView` と同じパターン
/// （戻る `<` + 中央タイトル 17pt Bold）。本文は白カード内にスクロール表示する。
struct LegalDocumentView: View {
    let title: String
    let bodyText: String
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 0) {
            header
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Text(bodyText)
                        .font(.system(size: 13, weight: .regular))
                        .foregroundStyle(FujuBankPalette.textPrimary)
                        .lineSpacing(22 - 13)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 18)
                        .padding(.vertical, 20)
                }
                .background(
                    RoundedRectangle(cornerRadius: 20, style: .continuous)
                        .fill(FujuBankPalette.surface)
                )
                .shadow(color: FujuBankPalette.shadowTint.opacity(0.08), radius: 4, x: 0, y: 2)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(FujuBankPalette.background.ignoresSafeArea())
        .navigationBarHidden(true)
    }

    private var header: some View {
        ZStack {
            Text(title)
                .font(FujuBankTypography.headline)
                .foregroundStyle(FujuBankPalette.textPrimary)

            HStack {
                Button(action: { dismiss() }) {
                    Image("ChevronLeft")
                        .renderingMode(.template)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 24, height: 24)
                        .foregroundStyle(FujuBankPalette.textPrimary)
                        .frame(width: 48, height: 48)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("戻る")
                Spacer()
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 10)
    }
}
