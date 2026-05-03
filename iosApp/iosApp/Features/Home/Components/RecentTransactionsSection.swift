import SwiftUI
import Shared

/// ホーム画面で表示する「最近の取引履歴」1 件分の表示モデル。
///
/// バックエンド統合前のため、文字列とサインを呼び出し側で組み立ててそのまま流し込む形に留める。
/// Android 側の `RecentTransactionItem` (data class) と意味論を揃えてある。
struct RecentTransactionItem: Identifiable {
    let id: String
    let title: String
    let amount: Int64
    let sign: String
    let timestamp: String
}

/// ホーム画面の「最近の取引履歴」セクション — Figma `709:8658` 準拠。
///
/// セクションヘッダー（タイトル + もっとみる）と、白背景・角丸 20 の取引カード 3 枚を
/// 縦に並べる。バックエンド連携は本タスクのスコープ外のため、表示するアイテムは呼び出し側
/// からモックを渡す。
struct RecentTransactionsSection: View {
    let items: [RecentTransactionItem]
    let onMore: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            sectionHeader
            VStack(spacing: 2) {
                ForEach(items) { item in
                    RecentTransactionCard(item: item)
                }
            }
            .frame(maxWidth: .infinity)
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity)
    }

    private var sectionHeader: some View {
        HStack {
            Text("最近の取引履歴")
                .font(FujuBankTypography.sectionLabel)
                .foregroundStyle(FujuBankPalette.textPrimary)
            Spacer()
            Button(action: onMore) {
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
        .padding(.horizontal, 8)
    }
}

private struct RecentTransactionCard: View {
    let item: RecentTransactionItem

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .top) {
                Text(item.title)
                    .font(FujuBankTypography.title)
                    .foregroundStyle(FujuBankPalette.textPrimary)
                Spacer()
                Text("\(item.sign)\(CurrencyFormatter.shared.formatAmount(amount: item.amount)) \(CurrencyFormatter.shared.UNIT)")
                    .font(FujuBankTypography.rowAmount)
                    .foregroundStyle(FujuBankPalette.brandPink)
            }
            Text(item.timestamp)
                .font(FujuBankTypography.caption)
                .foregroundStyle(FujuBankPalette.textSecondary)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(FujuBankPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 20))
        .shadow(color: FujuBankPalette.shadowTint.opacity(0.06), radius: 4, x: 0, y: 2)
    }
}
