import SwiftUI
import Shared

/// ホーム画面で表示する「最近の取引履歴」1 件分の表示モデル。
///
/// `direction` から sign（`+`/`-`）と金額色（ピンク/黒）を派生させる。Android 側
/// `RecentTransactionItem` と意味論を揃えてある。名前解決 / アーティファクト名取得は
/// 後続タスクで対応するため、`title` は呼び出し側で短縮 ID を組み込んで作成する。
struct RecentTransactionItem: Identifiable {
    let id: String
    let title: String
    let amount: Int64
    let direction: TransactionDirection
    let timestamp: String
}

extension RecentTransactionItem {
    /// shared `Transaction` を表示用 `RecentTransactionItem` に変換する。
    /// タイトル組み立ては `TransactionRow.swift` の `TransactionRowVariant` と同じロジック。
    static func fromShared(_ transaction: Shared.Transaction) -> RecentTransactionItem {
        let direction = transaction.direction
        let title: String
        if direction == TransactionDirection.mint {
            let suffix = transaction.artifactId.map { String($0.suffix(TransactionDisplay.shortIdLength)) }
            title = suffix.map { "アーティファクト \($0)" } ?? "発行"
        } else if direction == TransactionDirection.incoming {
            title = transaction.counterpartyPublicId
                .map { "@\($0) からもらいました" } ?? "入金"
        } else {
            title = transaction.counterpartyPublicId
                .map { "@\($0) に送りました" } ?? "送金"
        }
        let timestamp = TransactionDateFormatterIosKt.formatTransactionDateTimeSlashForIos(instant: transaction.occurredAt)
        return RecentTransactionItem(
            id: transaction.id,
            title: title,
            amount: transaction.amount,
            direction: direction,
            timestamp: timestamp,
        )
    }
}

/// ホーム画面の「最近の取引履歴」セクション — Figma `709:8658` 準拠。
///
/// セクションヘッダー（タイトル + もっとみる）と、白背景・角丸 20 の取引カード 3 枚を
/// 縦に並べる。
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
        // sign と金額色は direction から派生させる。Outgoing は黒/`-`、Mint/Incoming はピンク/`+`。
        let isOutgoing = item.direction == TransactionDirection.outgoing
        let sign = isOutgoing ? "-" : "+"
        let amountColor: Color = isOutgoing ? FujuBankPalette.textPrimary : FujuBankPalette.brandPink
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .top) {
                Text(item.title)
                    .font(FujuBankTypography.title)
                    .foregroundStyle(FujuBankPalette.textPrimary)
                Spacer()
                Text("\(sign)\(CurrencyFormatter.shared.formatAmount(amount: item.amount)) \(CurrencyFormatter.shared.UNIT)")
                    .font(FujuBankTypography.rowAmount)
                    .foregroundStyle(amountColor)
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
