import SwiftUI

/// 「プッシュ通知」マスターカード。
///
/// 共通仕様 D に従い、OS のプッシュ通知許可をマスター、着金 / 転送をサブとする
/// 階層構造の最上段。マスターはステータステキスト + 単一のアクションボタンで
/// 表現する（Toggle は使わない。Toggle だと「ON タップで OFF にならず設定へ飛ぶ」
/// など語彙と挙動の不整合が出るため）。
///
/// 状態に応じてボタンラベルと挙動を切り替える（共通仕様 B / C）:
///
/// - `.notDetermined` → 「許可する」、タップで OS 権限ダイアログ
/// - `.denied` / `.granted` / `.systemSettingsOnly` → 「OS 設定を開く」、タップで OS 設定アプリ
///
/// 要求中は `isRequesting` でボタンを `.disabled(true)` にして二重タップを防ぐ
/// （共通仕様 F）。
struct NotificationPermissionCard: View {
    let state: NotificationPermissionState
    let isRequesting: Bool
    let onRequestPermission: () -> Void
    let onOpenSystemSettings: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text("プッシュ通知")
                    .font(FujuBankTypography.title)
                    .foregroundStyle(FujuBankPalette.textPrimary)
                Text(subDescription)
                    .font(FujuBankTypography.caption)
                    .foregroundStyle(FujuBankPalette.textTertiary)
            }
            actionButton
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(FujuBankPalette.surface)
        )
        .shadow(color: FujuBankPalette.shadowTint.opacity(0.08), radius: 4, x: 0, y: 2)
    }

    private var actionButton: some View {
        Button(action: handleTap) {
            Text(buttonLabel)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(isRequesting ? FujuBankPalette.textTertiary : Color.white)
                .frame(maxWidth: .infinity)
                .frame(height: 40)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(isRequesting ? FujuBankPalette.hairline : FujuBankPalette.brandPink)
                )
        }
        .buttonStyle(.plain)
        .disabled(isRequesting)
        .accessibilityLabel(buttonLabel)
    }

    private var subDescription: String {
        switch state {
        case .notDetermined:
            return "プッシュ通知を受け取るには許可が必要です"
        case .granted:
            return "許可済み"
        case .denied:
            return "現在 OS で通知が無効になっています"
        case .systemSettingsOnly:
            return "OS 設定から変更できます"
        }
    }

    private var buttonLabel: String {
        switch state {
        case .notDetermined:
            return "許可する"
        case .denied, .granted, .systemSettingsOnly:
            return "OS 設定を開く"
        }
    }

    private func handleTap() {
        switch state {
        case .notDetermined:
            onRequestPermission()
        case .denied, .granted, .systemSettingsOnly:
            onOpenSystemSettings()
        }
    }
}

#Preview("NotificationPermissionCard 4 states") {
    VStack(spacing: 16) {
        NotificationPermissionCard(
            state: .notDetermined,
            isRequesting: false,
            onRequestPermission: {},
            onOpenSystemSettings: {}
        )
        NotificationPermissionCard(
            state: .granted,
            isRequesting: false,
            onRequestPermission: {},
            onOpenSystemSettings: {}
        )
        NotificationPermissionCard(
            state: .denied,
            isRequesting: false,
            onRequestPermission: {},
            onOpenSystemSettings: {}
        )
        NotificationPermissionCard(
            state: .systemSettingsOnly,
            isRequesting: false,
            onRequestPermission: {},
            onOpenSystemSettings: {}
        )
    }
    .padding(16)
    .background(FujuBankPalette.background)
}
