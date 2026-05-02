import SwiftUI
import Shared

/// 取引履歴の 1 行 — Figma `410:20343` 準拠。
///
/// - 50pt 円形アイコン（暫定プレースホルダ）
/// - タイトル（取引相手 or 「発行」）と日時を 1 段目に並べる
/// - 下段に金額を 32pt で大きく表示し、`+` / `-` 記号と「ふじゅ〜」16pt サフィックス
///
/// 金額の色:
/// - Mint / Incoming: 緑（#0CD80C）
/// - Outgoing: 黒（#111111）
struct TransactionRowView: View {
    let transaction: Shared.Transaction

    var body: some View {
        let variant = TransactionRowVariant(transaction: transaction)
        VStack(alignment: .trailing, spacing: 0) {
            HStack(alignment: .top, spacing: 14) {
                Circle()
                    .fill(variant.avatarColor)
                    .frame(width: 50, height: 50)
                VStack(alignment: .leading, spacing: 4) {
                    HStack(alignment: .top, spacing: 16) {
                        Text(variant.title)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(FujupayPalette.textPrimary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Text(TransactionDateFormatterIosKt.formatTransactionDateForIos(instant: transaction.occurredAt))
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(FujupayPalette.transactionMeta)
                    }
                    if let subtitle = variant.subtitle {
                        Text(subtitle)
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(FujupayPalette.transactionMeta)
                    }
                }
            }
            amountText(variant: variant)
        }
    }

    private func amountText(variant: TransactionRowVariant) -> some View {
        let formatted = BalanceFormatterKt.formatBalanceFuju(value: transaction.amount)
        return (
            Text("\(variant.sign)\(formatted)")
                .font(.system(size: 32, weight: .bold))
            + Text("ふじゅ〜")
                .font(.system(size: 16, weight: .bold))
        )
        .foregroundStyle(variant.amountColor)
    }
}

private struct TransactionRowVariant {
    let title: String
    let subtitle: String?
    let sign: String
    let amountColor: Color
    let avatarColor: Color

    init(transaction: Shared.Transaction) {
        // Kotlin/Native の enum は Swift の enum ではなくクラスプロパティとして公開されるため、
        // パターンマッチではなく等価比較で分岐する。
        let direction = transaction.direction
        if direction == TransactionDirection.mint {
            self.title = "発行"
            self.subtitle = transaction.artifactId.flatMap { id in
                let short = String(id.suffix(SHORT_ID_LEN))
                return "アーティファクト \(short)"
            }
            self.sign = "+"
            self.amountColor = FujupayPalette.actionGreen
            self.avatarColor = FujupayPalette.avatarArtifact
        } else if direction == TransactionDirection.incoming {
            let from = transaction.counterpartyUserId.map { String($0.suffix(SHORT_ID_LEN)) } ?? "相手"
            self.title = "\(from)からもらいました"
            self.subtitle = nil
            self.sign = "+"
            self.amountColor = FujupayPalette.actionGreen
            self.avatarColor = FujupayPalette.avatarPerson
        } else {
            let to = transaction.counterpartyUserId.map { String($0.suffix(SHORT_ID_LEN)) } ?? "相手"
            self.title = "\(to)に送りました"
            self.subtitle = nil
            self.sign = "-"
            self.amountColor = FujupayPalette.textPrimary
            self.avatarColor = FujupayPalette.avatarPerson
        }
    }
}

// 表示優先度の暫定対処: 取引相手やアーティファクトの名前解決 API が無いため、
// 末尾 6 文字に縮めて表示する。本番では UserApi / ArtifactRepository で解決する想定。
private let SHORT_ID_LEN = 6

