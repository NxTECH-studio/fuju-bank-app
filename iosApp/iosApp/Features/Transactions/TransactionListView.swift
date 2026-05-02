import SwiftUI
import Shared

/// 取引履歴画面 — Figma `410:20343` 準拠。
///
/// - ヘッダー: 戻る `<` / タイトル「取引履歴」/ 通知ベル
/// - 本文: `List` + `.refreshable`。空状態は中央寄せ文言。
struct TransactionListView: View {
    @StateObject private var viewModel = TransactionListViewModel()
    var onBack: () -> Void = {}
    var onNotificationTap: () -> Void = {}

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
        // 横余白はホーム画面と揃えるため 16pt（HomeView 側の `padding(.horizontal, 16)` と同値）。
        // タイトルは中央寄せ、左右に 48pt の戻るボタン / 通知ベルを配置する。
        ZStack {
            Text("取引履歴")
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(FujuBankPalette.textPrimary)

            HStack {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(FujuBankPalette.textPrimary)
                        .frame(width: 48, height: 48)
                }
                .buttonStyle(.plain)
                Spacer()
                NotificationBellButton(onTap: onNotificationTap)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
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
            VStack(spacing: 16) {
                Text(message)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(FujuBankPalette.textPrimary)
                    .multilineTextAlignment(.center)
                Button(action: { viewModel.refresh() }) {
                    Text("再試行")
                        .font(.system(size: 14, weight: .semibold))
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
        case let .loaded(items, _):
            if items.isEmpty {
                VStack {
                    Spacer()
                    Text("まだ取引がありません")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(FujuBankPalette.textSecondary)
                    Spacer()
                }
                .frame(maxWidth: .infinity)
                .refreshable { await refreshAsync() }
            } else {
                // 行間の区切り線は SwiftUI 標準の `Divider` 系で出すと色制御が難しいため、
                // 各行の bottom に薄い Rectangle を貼って表現する。最終行には付けない。
                let lastId = items.last?.id
                List {
                    ForEach(items, id: \.id) { transaction in
                        VStack(spacing: 12) {
                            TransactionRowView(transaction: transaction)
                            if transaction.id != lastId {
                                Rectangle()
                                    .fill(FujuBankPalette.transactionDivider)
                                    .frame(height: 2)
                            }
                        }
                        .listRowSeparator(.hidden)
                        .listRowBackground(Color.clear)
                        .listRowInsets(EdgeInsets(top: 6, leading: 18, bottom: 6, trailing: 18))
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
                .background(FujuBankPalette.background)
                .refreshable { await refreshAsync() }
            }
        }
    }

    /// SwiftUI `.refreshable` は `async` 完了を待つので、ViewModel 側で
    /// `withCheckedContinuation` を使って fetch 完了までサスペンドする。
    @MainActor
    private func refreshAsync() async {
        await viewModel.refreshAwait()
    }
}
