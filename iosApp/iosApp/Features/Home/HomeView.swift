import SwiftUI
import Shared

/// ホーム画面 — Figma `709:8658` 準拠（銀行版）。
///
/// 構成:
/// - ヘッダー（左 48pt 空 / 中央 fuju 銀行 ロゴ + chevron / 右 通知ベル）
/// - 残高カード（48pt の数値 + 「ふじゅ〜」単位、QR / バーコード / マスクトグルは旧 fujupay デザインから撤去）
/// - 「最近の取引履歴」セクション（API 取得済み 3 件 + もっとみる）
///
/// ボトムナビは親 `RootTabView` が描画する。
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
        case let .loaded(profile, _, recent):
            loadedContent(profile: profile, recent: recent)
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

    private func loadedContent(profile: UserProfile, recent: RecentTransactionsState) -> some View {
        VStack(spacing: 4) {
            FujuBankHeaderView(onNotificationTap: {
                onShowToast("通知機能は実装中です")
            })
            BalanceCardView(balanceFuju: profile.balanceFuju)
            recentSection(recent: recent)
            Spacer(minLength: 0)
        }
        .padding(10)
    }

    @ViewBuilder
    private func recentSection(recent: RecentTransactionsState) -> some View {
        switch recent {
        case .loading:
            recentLoadingPlaceholder
        case let .ready(items):
            if items.isEmpty {
                recentEmptyPlaceholder
            } else {
                RecentTransactionsSection(
                    items: items,
                    onMore: onTransactionHistory,
                )
            }
        case let .error(message):
            recentErrorPlaceholder(message: message)
        }
    }

    private var recentLoadingPlaceholder: some View {
        VStack(alignment: .leading, spacing: 8) {
            recentSectionHeader(showMore: false)
            ZStack {
                ProgressView()
                    .progressViewStyle(.circular)
                    .tint(FujuBankPalette.brandPink)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 96)
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity)
    }

    private var recentEmptyPlaceholder: some View {
        VStack(alignment: .leading, spacing: 8) {
            recentSectionHeader(showMore: true)
            Text("取引履歴はまだありません")
                .font(FujuBankTypography.body)
                .foregroundStyle(FujuBankPalette.textSecondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
                .padding(24)
                .background(FujuBankPalette.surface)
                .clipShape(RoundedRectangle(cornerRadius: 20))
                .shadow(color: FujuBankPalette.shadowTint.opacity(0.06), radius: 4, x: 0, y: 2)
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity)
    }

    private func recentErrorPlaceholder(message: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            recentSectionHeader(showMore: true)
            VStack(spacing: 12) {
                Text(message)
                    .font(FujuBankTypography.body)
                    .foregroundStyle(FujuBankPalette.textSecondary)
                    .multilineTextAlignment(.center)
                Button(action: { viewModel.refreshRecent() }) {
                    Text("再試行")
                        .font(FujuBankTypography.title)
                        .foregroundColor(.white)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 8)
                        .background(FujuBankPalette.brandPink)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
            }
            .frame(maxWidth: .infinity)
            .padding(24)
            .background(FujuBankPalette.surface)
            .clipShape(RoundedRectangle(cornerRadius: 20))
            .shadow(color: FujuBankPalette.shadowTint.opacity(0.06), radius: 4, x: 0, y: 2)
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity)
    }

    /// Loading / Empty / Error 共通の最近の取引セクションヘッダ。
    /// `RecentTransactionsSection` 内のヘッダと見た目を揃える。
    private func recentSectionHeader(showMore: Bool) -> some View {
        HStack {
            Text("最近の取引履歴")
                .font(FujuBankTypography.sectionLabel)
                .foregroundStyle(FujuBankPalette.textPrimary)
            Spacer()
            if showMore {
                Button(action: onTransactionHistory) {
                    HStack(spacing: 4) {
                        Text("もっとみる")
                            .font(FujuBankTypography.linkAction)
                            .foregroundStyle(FujuBankPalette.linkBlue)
                        Image("ChevronRight")
                            .renderingMode(.template)
                            .resizable()
                            .scaledToFit()
                            .frame(width: 14, height: 14)
                            .foregroundStyle(FujuBankPalette.linkBlue)
                    }
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 8)
    }
}
