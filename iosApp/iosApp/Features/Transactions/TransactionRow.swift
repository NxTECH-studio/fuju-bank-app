import SwiftUI
import Shared

/// 取引履歴の 1 行 — Figma `697:7601` 準拠（銀行版）。
///
/// - 白カード（角丸 0、影 drop-shadow）。一覧側は `Arrangement.spacedBy(2)` 相当の空きを開ける。
/// - 上段: 54pt アバター（白背景 + 中央 32pt X ロゴ）+ タイトル + サブタイトル
/// - 下段: 左に日時（yyyy/M/d HH:mm:ss）、右にピンクの金額（`+42 ふじゅ〜` / `-...`）
///
/// バックエンドからアーティファクト画像を取れる仕組みが整うまでは X ロゴで仮置きし、画像取得後に
/// 「画像 + 左上 X バッジ」バリアントを差し込めるようにこの 1 ファイルにまとめておく。
///
/// サブタイトル「18秒みつめられた」は Figma 上の固定文。視線データ統合は後続タスク。
struct TransactionRowView: View {
    let transaction: Shared.Transaction

    var body: some View {
        let variant = TransactionRowVariant(transaction: transaction)
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 12) {
                ArtifactAvatar()
                VStack(alignment: .leading, spacing: 2) {
                    Text(variant.title)
                        .font(FujuBankTypography.title)
                        .foregroundStyle(FujuBankPalette.textPrimary)
                    Text(TransactionDisplay.subtitlePlaceholder)
                        .font(FujuBankTypography.caption)
                        .foregroundStyle(FujuBankPalette.textSecondary)
                }
                .padding(.top, 4)
                Spacer(minLength: 0)
            }
            HStack(alignment: .bottom) {
                Text(formatTimestamp(transaction))
                    .font(FujuBankTypography.caption)
                    .foregroundStyle(FujuBankPalette.textSecondary)
                Spacer()
                Text("\(variant.sign)\(CurrencyFormatter.shared.formatAmount(amount: transaction.amount)) \(CurrencyFormatter.shared.UNIT)")
                    .font(FujuBankTypography.rowAmount)
                    .foregroundStyle(variant.amountColor)
            }
        }
    }

    private func formatTimestamp(_ transaction: Shared.Transaction) -> String {
        // Figma `697:7601` は `yyyy/M/d HH:mm:ss` 表記。共通実装は shared 側
        // `formatTransactionDateTimeSlash`。Swift から呼ぶ際は `TransactionDateFormatterIos.kt`
        // の引数無しラッパ `formatTransactionDateTimeSlashForIos` を経由して TimeZone デフォルトを
        // 使う（kotlinx.datetime の `TimeZone.Companion` シンボルを Swift 側で直接触らない）。
        TransactionDateFormatterIosKt.formatTransactionDateTimeSlashForIos(instant: transaction.occurredAt)
    }

}

/// 54pt の白角丸ボックスに 32pt の X ロゴを中央配置したアーティファクトプレースホルダ。
/// Figma `697:7601` の Credit 9/10/11 等の「画像が無いアーティファクト」表示にあたる。
private struct ArtifactAvatar: View {
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 8)
                .fill(FujuBankPalette.surface)
                .frame(width: 54, height: 54)
            Image("XLogo")
                .resizable()
                .renderingMode(.original)
                .scaledToFit()
                .frame(width: 32, height: 32)
        }
    }
}

private struct TransactionRowVariant {
    let title: String
    let sign: String
    let amountColor: Color

    init(transaction: Shared.Transaction) {
        // Kotlin/Native の enum は Swift の enum ではなくクラスプロパティとして公開されるため、
        // パターンマッチではなく等価比較で分岐する。
        let direction = transaction.direction
        if direction == TransactionDirection.mint {
            let suffix = transaction.artifactId
                .map { String($0.suffix(TransactionDisplay.shortIdLength)) }
            self.title = suffix.map { "アーティファクト \($0)" } ?? "発行"
            self.sign = "+"
            self.amountColor = FujuBankPalette.brandPink
        } else if direction == TransactionDirection.incoming {
            let from = transaction.counterpartyUserId
                .map { String($0.suffix(TransactionDisplay.shortIdLength)) }
            self.title = from.map { "\($0) からもらいました" } ?? "入金"
            self.sign = "+"
            self.amountColor = FujuBankPalette.brandPink
        } else {
            let to = transaction.counterpartyUserId
                .map { String($0.suffix(TransactionDisplay.shortIdLength)) }
            self.title = to.map { "\($0) に送りました" } ?? "送金"
            self.sign = "-"
            self.amountColor = FujuBankPalette.textPrimary
        }
    }
}

