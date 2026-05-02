import SwiftUI
import Shared

/// ホーム画面 — Figma `89:12356` 準拠。
///
/// - ヘッダー（fujupay ロゴ + 通知ベル）
/// - バーコード / QR / 残高カード（マスク表示トグル付き）
/// - 取引メニュー見出し + 4 アクション
///
/// ボトムタブ + 中央 FAB は親の `RootTabView` が描画する。
struct HomeView: View {
    @StateObject private var viewModel = HomeViewModel()
    var onTransactionHistory: () -> Void = {}
    var onSendReceive: () -> Void = {}
    var onShowToast: (String) -> Void = { _ in }

    var body: some View {
        ZStack {
            FujupayPalette.background.ignoresSafeArea()
            content
        }
        .onAppear { viewModel.onAppear() }
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.state {
        case .loading:
            ProgressView()
                .progressViewStyle(.circular)
                .tint(FujupayPalette.brandPink)
        case let .error(message):
            VStack(spacing: 16) {
                Text(message)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(FujupayPalette.textPrimary)
                    .multilineTextAlignment(.center)
                Button(action: { viewModel.refresh() }) {
                    Text("再試行")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 24)
                        .padding(.vertical, 10)
                        .background(FujupayPalette.brandPink)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
            }
            .padding(24)
        case let .loaded(profile, revealed):
            loadedContent(profile: profile, revealed: revealed)
        }
    }

    private func loadedContent(profile: UserProfile, revealed: Bool) -> some View {
        VStack(spacing: 16) {
            FujupayHeaderView(onNotificationTap: {
                onShowToast("通知機能は実装中です")
            })
            .padding(.top, 8)

            BalanceCardView(
                publicId: profile.publicId,
                balanceFuju: profile.balanceFuju,
                revealed: revealed,
                onToggleReveal: { viewModel.toggleReveal() },
            )

            HStack {
                Text("取引メニュー")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(FujupayPalette.textSecondary)
                Spacer()
            }
            .padding(.top, 8)
            .padding(.leading, 8)

            ActionTilesView(
                onTransactionHistory: onTransactionHistory,
                onSendReceive: onSendReceive,
                onScan: { onShowToast("スキャン機能は実装中です") },
                onCharge: { onShowToast("チャージ機能は実装中です") },
            )

            Spacer()
        }
        .padding(.horizontal, 16)
    }
}
