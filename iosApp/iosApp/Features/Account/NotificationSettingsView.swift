import SwiftUI

/// 通知設定画面 — Figma `718:7332` 準拠（Android `NotificationSettingsScreen` と 1:1）。
///
/// - ヘッダー: 戻る `<` (左 48pt) / タイトル「通知設定」(中央 17pt Bold) / 通知ベル (右 48pt)
/// - 本文: 上段マスター「プッシュ通知」カード + サブ「着金通知 / 転送通知」カード
///   （client-bank-15 共通仕様 D の階層構造）。
///
/// マスター ON 状態（OS 許可が `.granted` / `.systemSettingsOnly`）でのみサブトグル
/// が操作可能。マスター OFF → ON 遷移時はサブトグル両方を自動 ON に上書きする
/// （権限ダイアログ経由・`scenePhase` 復帰経由のいずれでも）。
///
/// `NavigationStack` 配下で表示されるためヘッダーの戻るは `dismiss` を呼ぶ。`navigationBarHidden`
/// は SwiftUI 側で標準ナビバーを隠したうえで、Figma 準拠の自前ヘッダーを描く（`TransactionListView`
/// と同じスタイル）。
struct NotificationSettingsView: View {
    @StateObject private var viewModel = ObservableNotificationSettingsViewModel()
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    @State private var permissionState: NotificationPermissionState = .notDetermined
    @State private var isRequestingPermission: Bool = false
    var onNotificationTap: () -> Void = {}

    var body: some View {
        VStack(spacing: 0) {
            header
            VStack(spacing: 16) {
                NotificationPermissionCard(
                    state: permissionState,
                    isRequesting: isRequestingPermission,
                    onRequestPermission: requestPermission,
                    onOpenSystemSettings: openAppNotificationSettings
                )
                NotificationCard(
                    depositEnabled: viewModel.depositEnabled,
                    onDepositChange: viewModel.setDepositEnabled,
                    transferEnabled: viewModel.transferEnabled,
                    onTransferChange: viewModel.setTransferEnabled,
                    enabled: permissionState.isGranted,
                )
            }
            .padding(.horizontal, 16)
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(FujuBankPalette.background.ignoresSafeArea())
        .navigationBarHidden(true)
        .task {
            permissionState = await currentNotificationPermissionState()
        }
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase == .active {
                Task {
                    await refreshPermissionState()
                }
            }
        }
    }

    private func requestPermission() {
        guard !isRequestingPermission else { return }
        isRequestingPermission = true
        Task {
            let next = await requestNotificationPermission()
            await applyPermissionTransition(next: next)
            isRequestingPermission = false
        }
    }

    private func refreshPermissionState() async {
        let next = await currentNotificationPermissionState()
        await applyPermissionTransition(next: next)
    }

    /// 共通仕様 D: マスター OFF → ON 遷移時にサブトグル両方を自動 ON へ上書き。
    /// 権限ダイアログ経由・`scenePhase` 復帰経由のいずれでも同じ動作になる。
    /// 既に ON だった場合は上書きしない（`previous` が OFF だったときのみ反映）。
    @MainActor
    private func applyPermissionTransition(next: NotificationPermissionState) async {
        let previousOn = permissionState.isGranted
        permissionState = next
        if !previousOn && next.isGranted {
            viewModel.setDepositEnabled(true)
            viewModel.setTransferEnabled(true)
        }
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
///
/// 共通仕様 D に従い、マスタートグル OFF（`enabled = false`）時は両サブトグルを
/// `.disabled(true)` にして操作を受け付けず、視覚的にも `.opacity(0.4)` で抑制する。
private struct NotificationCard: View {
    let depositEnabled: Bool
    let onDepositChange: (Bool) -> Void
    let transferEnabled: Bool
    let onTransferChange: (Bool) -> Void
    let enabled: Bool

    var body: some View {
        VStack(spacing: 0) {
            ToggleRow(
                title: "着金通知",
                description: "ふじゅ〜が届いたとき",
                isOn: Binding(get: { depositEnabled }, set: onDepositChange),
                accessibilityLabel: "着金通知",
                enabled: enabled,
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
                enabled: enabled,
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
    let enabled: Bool

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
            .opacity(enabled ? 1.0 : 0.4)
            Spacer(minLength: 12)
            Toggle("", isOn: $isOn)
                .labelsHidden()
                .tint(FujuBankPalette.brandPink)
                .disabled(!enabled)
                .accessibilityLabel(accessibilityLabel)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }
}
