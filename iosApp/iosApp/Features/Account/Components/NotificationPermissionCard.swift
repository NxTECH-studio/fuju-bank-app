import SwiftUI

/// 「プッシュ通知」マスタートグルカード。
///
/// 共通仕様 D に従い、OS のプッシュ通知許可をマスター、着金 / 転送をサブとする
/// 階層構造の最上段。既存 `NotificationCard` のサブトグル行と同じ
/// 白角丸（`RoundedRectangle(cornerRadius: 20, style: .continuous)`）+ shadow の
/// 見た目に揃える。
///
/// `Toggle` は受動コンポーネントとして扱い、`Binding` の setter からは
/// 値を更新せず、現在の OS 状態に応じて `requestPermission` / `openSystemSettings`
/// を呼び分ける（共通仕様 B / C）:
///
/// - `.notDetermined` → `requestPermission`
/// - `.denied` / `.granted` / `.systemSettingsOnly` → `openSystemSettings`
///   （iOS では一度許可した状態をアプリから revoke できないため `.granted` でも
///    OS 設定アプリ導線に振る）
///
/// 要求中は `isRequesting` が立ち、Toggle を `.disabled(true)` にして
/// 二重タップを防ぐ（共通仕様 F）。
struct NotificationPermissionCard: View {
    let state: NotificationPermissionState
    let isRequesting: Bool
    let onRequestPermission: () -> Void
    let onOpenSystemSettings: () -> Void

    var body: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                Text("プッシュ通知")
                    .font(FujuBankTypography.title)
                    .foregroundStyle(FujuBankPalette.textPrimary)
                Text(subDescription)
                    .font(FujuBankTypography.caption)
                    .foregroundStyle(FujuBankPalette.textTertiary)
            }
            Spacer(minLength: 12)
            Toggle(
                "",
                isOn: Binding(
                    get: { state.isGranted },
                    set: { _ in handleTap() }
                )
            )
            .labelsHidden()
            .tint(FujuBankPalette.brandPink)
            .disabled(isRequesting)
            .accessibilityLabel("プッシュ通知")
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(FujuBankPalette.surface)
        )
        .shadow(color: FujuBankPalette.shadowTint.opacity(0.08), radius: 4, x: 0, y: 2)
    }

    private var subDescription: String {
        switch state {
        case .notDetermined:
            return "プッシュ通知を受け取るには許可が必要です"
        case .granted:
            return "許可済み"
        case .denied:
            return "OS 設定から有効化できます"
        case .systemSettingsOnly:
            return "OS 設定から変更できます"
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
