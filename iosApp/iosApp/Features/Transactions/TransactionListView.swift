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
        .background(FujupayPalette.background.ignoresSafeArea())
        .onAppear { viewModel.onAppear() }
    }

    private var header: some View {
        // 横余白はホーム画面と揃えるため 16pt（HomeView 側の `padding(.horizontal, 16)` と同値）。
        // タイトルは中央寄せ、左右に 48pt の戻るボタン / 通知ベルを配置する。
        ZStack {
            Text("取引履歴")
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(FujupayPalette.textPrimary)

            HStack {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(FujupayPalette.textPrimary)
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
                .tint(FujupayPalette.brandPink)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
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
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        case let .loaded(items, _):
            if items.isEmpty {
                VStack {
                    Spacer()
                    Text("まだ取引がありません")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(FujupayPalette.textSecondary)
                    Spacer()
                }
                .frame(maxWidth: .infinity)
                .refreshable { await refreshAsync() }
            } else {
                List {
                    ForEach(Array(items.enumerated()), id: \.element.id) { index, transaction in
                        VStack(spacing: 12) {
                            TransactionRowView(transaction: transaction)
                            if index < items.count - 1 {
                                Rectangle()
                                    .fill(FujupayPalette.transactionDivider)
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
                .background(FujupayPalette.background)
                .refreshable { await refreshAsync() }
            }
        }
    }

    /// SwiftUI `.refreshable` は `async` を期待する。Kotlin 側のコールバック型 API を
    /// `withCheckedContinuation` で待ち合わせず、状態の遷移をポーリングするのは複雑なため、
    /// シンプルに `refresh()` をキックして即座に return する。引きおろしジェスチャの
    /// インジケータは表示されるが、新しい結果が反映されたタイミングで自動的に閉じる。
    @MainActor
    private func refreshAsync() async {
        viewModel.refresh()
    }
}
