import SwiftUI
import Shared

/// 取引詳細画面 — Figma `702:6440` 準拠（銀行版）。
///
/// 構成:
/// - ヘッダー: 戻る `<` / 「取引詳細」/ 通知ベル
/// - 大金額カード: 「+342,535 ふじゅ〜」をピンクで大きく表示（h=110、角丸 32）
/// - 取引行カード: アーティファクトアバター + タイトル/サブタイトル + 左上 X バッジ + 日時
/// - 感情データカード: 「感情データ (metadata)」見出し + 滞留時間 / 視線強度 の 2 行（暫定モック値）
///
/// 感情データの値（`18 秒` / `0.94`）は Figma 上のモックそのまま。バックエンド統合は後続タスク。
struct TransactionDetailView: View {
    @StateObject private var viewModel: TransactionDetailViewModel
    var onBack: () -> Void = {}
    var onNotificationTap: () -> Void = {}

    init(transaction: Shared.Transaction, onBack: @escaping () -> Void = {}, onNotificationTap: @escaping () -> Void = {}) {
        _viewModel = StateObject(wrappedValue: TransactionDetailViewModel(transaction: transaction))
        self.onBack = onBack
        self.onNotificationTap = onNotificationTap
    }

    var body: some View {
        VStack(spacing: 0) {
            header
            switch viewModel.state {
            case let .loaded(transaction):
                loadedContent(transaction: transaction)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(FujuBankPalette.background.ignoresSafeArea())
    }

    private var header: some View {
        ZStack {
            Text("取引詳細")
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

    private func loadedContent(transaction: Shared.Transaction) -> some View {
        VStack(spacing: 24) {
            AmountCard(transaction: transaction)
            VStack(spacing: 4) {
                DetailTransactionRow(transaction: transaction)
                EmotionMetadataCard()
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 10)
        .frame(maxWidth: .infinity)
    }
}

/// Figma `702:6440` 上部の大金額カード。Mint / Incoming はブランドピンク + `+`、Outgoing は
/// 黒 + `-`。記号は数値より小さい 40pt、数値は 48pt、単位は 20pt。
private struct AmountCard: View {
    let transaction: Shared.Transaction

    var body: some View {
        let appearance = AmountAppearance(direction: transaction.direction)
        HStack(spacing: 0) {
            Spacer(minLength: 0)
            HStack(alignment: .center, spacing: 8) {
                Text(appearance.sign)
                    .font(FujuBankTypography.amountSign)
                    .foregroundStyle(appearance.color)
                Text(CurrencyFormatter.shared.formatAmount(amount: transaction.amount))
                    .font(FujuBankTypography.amount)
                    .foregroundStyle(appearance.color)
                Text(CurrencyFormatter.shared.UNIT)
                    .font(FujuBankTypography.amountUnit)
                    .foregroundStyle(appearance.color)
            }
            .lineLimit(1)
            .minimumScaleFactor(0.5)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 36)
        .frame(maxWidth: .infinity)
        .frame(height: 110)
        .background(FujuBankPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 32))
        .shadow(color: FujuBankPalette.shadowTint.opacity(0.06), radius: 4, x: 0, y: 2)
    }
}

private struct AmountAppearance {
    let sign: String
    let color: Color

    init(direction: TransactionDirection) {
        if direction == TransactionDirection.outgoing {
            self.sign = "-"
            self.color = FujuBankPalette.textPrimary
        } else {
            self.sign = "+"
            self.color = FujuBankPalette.brandPink
        }
    }
}

/// Figma `702:6440` の中段カード。アーティファクトアバター + タイトル/サブタイトル + 日時、
/// 左上に SNS 出典バッジ（X）を絶対配置で重ねる。
private struct DetailTransactionRow: View {
    let transaction: Shared.Transaction

    var body: some View {
        let title = makeTitle(for: transaction)
        ZStack(alignment: .topLeading) {
            VStack(alignment: .trailing, spacing: 8) {
                HStack(alignment: .top, spacing: 12) {
                    RoundedRectangle(cornerRadius: 8)
                        .fill(FujuBankPalette.avatarArtifact)
                        .frame(width: 54, height: 54)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(title)
                            .font(FujuBankTypography.title)
                            .foregroundStyle(FujuBankPalette.textPrimary)
                        Text(TransactionDisplay.subtitlePlaceholder)
                            .font(FujuBankTypography.caption)
                            .foregroundStyle(FujuBankPalette.textSecondary)
                    }
                    .padding(.top, 4)
                    Spacer(minLength: 0)
                }
                Text(timestamp)
                    .font(FujuBankTypography.caption)
                    .foregroundStyle(FujuBankPalette.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(16)
            .frame(maxWidth: .infinity)
            .background(FujuBankPalette.surface)
            .shadow(color: FujuBankPalette.shadowTint.opacity(0.06), radius: 4, x: 0, y: 2)

            // SNS 出典バッジ (X)。Figma の `absolute left-8 top-8` を ZStack の TopLeading で再現。
            ZStack {
                RoundedRectangle(cornerRadius: 4.5)
                    .fill(FujuBankPalette.surface)
                RoundedRectangle(cornerRadius: 4.5)
                    .stroke(FujuBankPalette.hairline, lineWidth: 1)
                Image("XLogo")
                    .resizable()
                    .renderingMode(.original)
                    .scaledToFit()
                    .frame(width: 18, height: 18)
            }
            .frame(width: 24, height: 24)
            .padding(.leading, 8)
            .padding(.top, 8)
        }
    }

    private var timestamp: String {
        TransactionDateFormatterIosKt.formatTransactionDateTimeSlashForIos(instant: transaction.occurredAt)
    }

    private func makeTitle(for transaction: Shared.Transaction) -> String {
        let direction = transaction.direction
        if direction == TransactionDirection.mint {
            let suffix = transaction.artifactId.map { String($0.suffix(TransactionDisplay.shortIdLength)) }
            return suffix.map { "アーティファクト \($0)" } ?? "発行"
        } else if direction == TransactionDirection.incoming {
            let from = transaction.counterpartyUserId.map { String($0.suffix(TransactionDisplay.shortIdLength)) }
            return from.map { "\($0) からもらいました" } ?? "入金"
        } else {
            let to = transaction.counterpartyUserId.map { String($0.suffix(TransactionDisplay.shortIdLength)) }
            return to.map { "\($0) に送りました" } ?? "送金"
        }
    }
}

/// Figma `702:6440` 下段の感情データカード。バックエンド統合前は Figma 上のモック値
/// （滞留時間 18 秒 / 視線強度 0.94）をそのまま表示する。
private struct EmotionMetadataCard: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("感情データ (metadata)")
                .font(FujuBankTypography.title)
                .foregroundStyle(FujuBankPalette.textPrimary)
            metadataRow(label: "滞留時間", value: "18 秒")
            metadataRow(label: "視線強度", value: "0.94")
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(FujuBankPalette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 20))
    }

    private func metadataRow(label: String, value: String) -> some View {
        HStack {
            Text(label)
                .font(FujuBankTypography.caption)
                .foregroundStyle(FujuBankPalette.textTertiary)
            Spacer()
            Text(value)
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(FujuBankPalette.textPrimary)
        }
    }
}

