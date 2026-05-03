import SwiftUI

/// 通知設定画面 — Figma `718:7332` 準拠（Android `NotificationSettingsScreen` と 1:1）。
///
/// - ヘッダー: 戻る `<` (左 48pt) / タイトル「通知設定」(中央 17pt Bold) / 通知ベル (右 48pt)
/// - 本文: 白角丸カード内に「着金通知 / ふじゅ〜が届いたとき」「転送通知 / 送金が完了したとき」
///   の 2 行 + 各行右にトグル
///
/// `NavigationStack` 配下で表示されるためヘッダーの戻るは `dismiss` を呼ぶ。`navigationBarHidden`
/// は SwiftUI 側で標準ナビバーを隠したうえで、Figma 準拠の自前ヘッダーを描く（`TransactionListView`
/// と同じスタイル）。
struct NotificationSettingsView: View {
    @StateObject private var viewModel = ObservableNotificationSettingsViewModel()
    @Environment(\.dismiss) private var dismiss
    var onNotificationTap: () -> Void = {}

    var body: some View {
        VStack(spacing: 0) {
            header
            VStack(spacing: 16) {
                NotificationCard(
                    depositEnabled: viewModel.depositEnabled,
                    onDepositChange: viewModel.setDepositEnabled,
                    transferEnabled: viewModel.transferEnabled,
                    onTransferChange: viewModel.setTransferEnabled,
                )
            }
            .padding(.horizontal, 16)
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(FujuBankPalette.background.ignoresSafeArea())
        .navigationBarHidden(true)
    }

    private var header: some View {
        ZStack {
            Text("通知設定")
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
                NotificationBellButton(onTap: onNotificationTap)
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 10)
    }
}

/// 通知設定の白角丸カード（着金 / 転送の 2 行）。
private struct NotificationCard: View {
    let depositEnabled: Bool
    let onDepositChange: (Bool) -> Void
    let transferEnabled: Bool
    let onTransferChange: (Bool) -> Void

    var body: some View {
        VStack(spacing: 0) {
            ToggleRow(
                title: "着金通知",
                description: "ふじゅ〜が届いたとき",
                isOn: Binding(get: { depositEnabled }, set: onDepositChange),
                accessibilityLabel: "着金通知",
            )
            Divider()
                .frame(height: 1)
                .overlay(FujuBankPalette.hairline)
                .padding(.horizontal, 16)
            ToggleRow(
                title: "転送通知",
                description: "送金が完了したとき",
                isOn: Binding(get: { transferEnabled }, set: onTransferChange),
                accessibilityLabel: "転送通知",
            )
        }
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(FujuBankPalette.surface)
        )
        .shadow(color: FujuBankPalette.shadowTint.opacity(0.08), radius: 4, x: 0, y: 2)
    }
}

/// カード内 1 行（タイトル + 説明 + トグル）。
private struct ToggleRow: View {
    let title: String
    let description: String
    @Binding var isOn: Bool
    let accessibilityLabel: String

    var body: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(FujuBankTypography.title)
                    .foregroundStyle(FujuBankPalette.textPrimary)
                Text(description)
                    .font(FujuBankTypography.caption)
                    .foregroundStyle(FujuBankPalette.textTertiary)
            }
            Spacer(minLength: 12)
            Toggle("", isOn: $isOn)
                .labelsHidden()
                .tint(FujuBankPalette.brandPink)
                .accessibilityLabel(accessibilityLabel)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }
}
