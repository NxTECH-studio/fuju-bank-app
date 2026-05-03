import SwiftUI
import Shared

/// 取引履歴画面 — Figma `697:7601` 準拠（銀行版）。
///
/// 構成:
/// - ヘッダー: 戻る `<` (左 48pt) / タイトル「取引履歴」(中央 17pt Bold) / 通知ベル (右 48pt)
/// - 本文: List + .refreshable。各カードは [TransactionRowView]、間隔 2pt（Figma `709:8658` の
///   ホーム最近取引と揃える）。
///
/// 親 `RootTabView` のボトムナビは表示したまま、戻るで Home に復帰する。
struct TransactionListView: View {
    @StateObject private var viewModel = TransactionListViewModel()
    var onBack: () -> Void = {}
    var onNotificationTap: () -> Void = {}
    var onTransactionTap: (Shared.Transaction) -> Void = { _ in }

    var body: some View {
        VStack(spacing: 0) {
            header
            content
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background(FujuBankPalette.background.ignoresSafeArea())
        .onAppear { viewModel.onAppear() }
    }

    private var header: some View {
        // Figma `697:7601` contents wrapper の p-10 に合わせて水平・垂直 10pt。
        // タイトルは中央寄せ、左右に 48pt の戻るボタン / 通知ベルを配置する。
        ZStack {
            Text("取引履歴")
                .font(FujuBankTypography.headline)
                .foregroundStyle(FujuBankPalette.textPrimary)

            HStack {
                Button(action: onBack) {
                    Image("ChevronLeft")
                        .renderingMode(.template)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 24, height: 24)
                        .foregroundStyle(FujuBankPalette.textPrimary)
                        .frame(width: 48, height: 48)
                }
                .buttonStyle(.plain)
                Spacer()
                NotificationBellButton(onTap: onNotificationTap)
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 10)
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.state {
        case .loading:
            ProgressView()
                .progressViewStyle(.circular)
                .tint(FujuBankPalette.brandPink)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        case let .error(message):
            errorContent(message: message)
        case let .loaded(items, _):
            if items.isEmpty {
                emptyContent
            } else {
                loadedList(items: items)
            }
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
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var emptyContent: some View {
        VStack {
            Spacer()
            Text("まだ取引がありません")
                .font(FujuBankTypography.body)
                .foregroundStyle(FujuBankPalette.textSecondary)
            Spacer()
        }
        .frame(maxWidth: .infinity)
        .refreshable { await refreshAsync() }
    }

    private func loadedList(items: [Shared.Transaction]) -> some View {
        // Figma `697:7601` の縦間隔 2pt は `Arrangement.spacedBy(2.dp)` の Android と揃える。
        // List の listRowInsets で行間 2pt を実現するために、行外側の空 listRowSeparator を
        // 隠して、Insets で top/bottom 1pt ずつ空ける（合計 2pt）。
        List {
            ForEach(items, id: \.id) { transaction in
                Button(action: { onTransactionTap(transaction) }) {
                    TransactionRowView(transaction: transaction)
                        .padding(16)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(FujuBankPalette.surface)
                        .shadow(color: FujuBankPalette.shadowTint.opacity(0.06), radius: 4, x: 0, y: 2)
                }
                .buttonStyle(.plain)
                .listRowSeparator(.hidden)
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets(top: 1, leading: 0, bottom: 1, trailing: 0))
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .background(FujuBankPalette.background)
        .refreshable { await refreshAsync() }
    }

    /// SwiftUI `.refreshable` は `async` 完了を待つので、ViewModel 側で
    /// `withCheckedContinuation` を使って fetch 完了までサスペンドする。
    @MainActor
    private func refreshAsync() async {
        await viewModel.refreshAwait()
    }
}
