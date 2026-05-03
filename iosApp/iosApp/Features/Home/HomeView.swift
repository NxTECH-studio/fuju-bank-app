import SwiftUI
import Shared

/// ホーム画面 — Figma `709:8658` 準拠（銀行版）。
///
/// 構成:
/// - ヘッダー（左 48pt 空 / 中央 fuju 銀行 ロゴ + chevron / 右 通知ベル）
/// - 残高カード（48pt の数値 + 「ふじゅ〜」単位、QR / バーコード / マスクトグルは旧 fujupay デザインから撤去）
/// - 「最近の取引履歴」セクション（モック 3 件 + もっとみる）
///
/// ボトムナビは親 `RootTabView` が描画する。
///
/// 注: 取引履歴のモック表示は Figma `709:8658` の見た目を再現するための暫定。
///     バックエンドからの最近の取引取得 API は本タスクのスコープ外（後続タスクで対応）。
struct HomeView: View {
    @StateObject private var viewModel = HomeViewModel()
    var onTransactionHistory: () -> Void = {}
    /// 旧 fujupay の「送る・もらう」アクション。新銀行版では発火させないが、`RootTabView` の
    /// 既存シグネチャを変えないため引数として残す。
    var onSendReceive: () -> Void = {}
    var onShowToast: (String) -> Void = { _ in }

    var body: some View {
        ZStack {
            FujuBankPalette.background.ignoresSafeArea()
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
                .tint(FujuBankPalette.brandPink)
        case let .error(message):
            errorContent(message: message)
        case let .loaded(profile, _):
            loadedContent(profile: profile)
        }
    }

    private func errorContent(message: String) -> some View {
        VStack(spacing: 16) {
            Text(message)
                .font(FujuBankTypography.body)
                .foregroundStyle(FujuBankPalette.textPrimary)
                .multilineTextAlignment(.center)
            Button(action: { viewModel.refresh() }) {
                Text("再試行")
                    .font(FujuBankTypography.title)
                    .foregroundColor(.white)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 10)
                    .background(FujuBankPalette.brandPink)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            }
            .buttonStyle(.plain)
        }
        .padding(24)
    }

    private func loadedContent(profile: UserProfile) -> some View {
        VStack(spacing: 4) {
            FujuBankHeaderView(onNotificationTap: {
                onShowToast("通知機能は実装中です")
            })
            BalanceCardView(balanceFuju: profile.balanceFuju)
            RecentTransactionsSection(
                items: HomeView.mockRecentTransactions,
                onMore: onTransactionHistory,
            )
            Spacer(minLength: 0)
        }
        .padding(10)
    }

    /// Figma `709:8658` の見本値そのまま。バックエンド統合は後続タスクで実施。
    private static let mockRecentTransactions: [RecentTransactionItem] = (0..<3).map { index in
        RecentTransactionItem(
            id: "mock-\(index)",
            title: "トマトのイラスト",
            amount: 42,
            sign: "+",
            timestamp: "2025/3/4 12:03:03",
        )
    }
}
