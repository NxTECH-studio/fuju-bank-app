import SwiftUI
import Shared

/// 送金フロー Step 2 — 金額入力 + プレビュー画面（iOS）。
///
/// 大金額表示 + 残高プレビューカード + ピンク CTA + カスタム数字パッド + 確認 alert。
/// 完了時は [onComplete] を呼び、親で Snackbar 相当の Toast 表示 + ホーム自動遷移を行う。
struct SendAmountView: View {
    @ObservedObject var viewModel: ObservableSendFlowViewModel
    var onBack: () -> Void
    var onComplete: (_ transactionId: String, _ newBalance: Int64) -> Void

    var body: some View {
        VStack(spacing: 0) {
            header
            if let recipient = viewModel.recipient {
                content(recipient: recipient)
            } else {
                Color.clear
                    .onAppear { onBack() }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(FujuBankPalette.background.ignoresSafeArea())
        .alert(
            "確認",
            isPresented: Binding(
                get: { viewModel.showAmountConfirm },
                set: { newValue in if !newValue { viewModel.dismissAmountConfirm() } },
            ),
            actions: {
                Button("送金する", role: .none) { viewModel.submit() }
                Button("キャンセル", role: .cancel) { viewModel.dismissAmountConfirm() }
            },
            message: {
                if let recipient = viewModel.recipient {
                    Text("\(recipient.name)さんに \(formatAmount(viewModel.amount))\(currencyUnit) 送りますか？")
                }
            },
        )
        .onChange(of: submissionSuccessFingerprint) { _, _ in
            if case let .success(transactionId, newBalance) = viewModel.submission {
                onComplete(transactionId, newBalance)
            }
        }
    }

    /// `.onChange` で監視するため、Submission を String に縮退させる。
    private var submissionSuccessFingerprint: String {
        switch viewModel.submission {
        case .idle: return "idle"
        case .submitting: return "submitting"
        case let .mfaRequired(retryKey): return "mfa:\(retryKey)"
        case let .success(tx, balance): return "success:\(tx):\(balance)"
        }
    }

    private var header: some View {
        ZStack {
            Text("送金")
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
                .disabled(viewModel.submission == .submitting)
                Spacer()
                Color.clear.frame(width: 48, height: 48)
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 10)
    }

    private func content(recipient: UserSearchResult) -> some View {
        VStack(spacing: 16) {
            recipientChip(recipient: recipient)
            amountDisplay
            if viewModel.isOverBalance {
                Text("残高が不足しています")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Color.red)
            }
            balancePreviewCard
            if let err = viewModel.error {
                Text(err)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Color.red)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
            }
            Spacer(minLength: 0)
            sendCta
            numericKeypad
            Spacer().frame(height: 8)
        }
        .padding(.horizontal, 16)
    }

    private func recipientChip(recipient: UserSearchResult) -> some View {
        HStack(spacing: 8) {
            ZStack {
                Circle().fill(FujuBankPalette.avatarPerson)
                Image("AvatarTomato")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 28, height: 28)
                    .clipShape(Circle())
            }
            .frame(width: 28, height: 28)
            VStack(alignment: .leading, spacing: 2) {
                Text(recipient.name)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(FujuBankPalette.textPrimary)
                Text("#" + String(recipient.publicId.suffix(4)))
                    .font(.system(size: 11))
                    .foregroundStyle(FujuBankPalette.textTertiary)
            }
            Spacer()
        }
        .padding(.top, 4)
    }

    private var amountDisplay: some View {
        HStack(alignment: .lastTextBaseline, spacing: 6) {
            Text(formatAmount(viewModel.amount))
                .font(.system(size: 40, weight: .bold))
                .foregroundStyle(FujuBankPalette.textPrimary)
            Text(currencyUnit)
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(FujuBankPalette.textSecondary)
        }
        .padding(.top, 24)
        .padding(.bottom, 8)
    }

    private var balancePreviewCard: some View {
        let balanceAfter = max(0, viewModel.balance - viewModel.amount)
        return HStack {
            Text("送金後の残高")
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(FujuBankPalette.textSecondary)
            Spacer()
            Text("\(formatAmount(balanceAfter)) \(currencyUnit)")
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(FujuBankPalette.textPrimary)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(Color(red: 0xFF / 255, green: 0xEA / 255, blue: 0xF6 / 255))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private var sendCta: some View {
        Button(action: { viewModel.presentAmountConfirm() }) {
            HStack(spacing: 8) {
                if viewModel.submission == .submitting {
                    ProgressView()
                        .progressViewStyle(.circular)
                        .tint(.white)
                    Text("送金中...")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(.white)
                } else {
                    Text("送金する")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(.white)
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 48)
            .background(viewModel.canShowConfirm ? FujuBankPalette.brandPink : Color(red: 0xE6 / 255, green: 0xE6 / 255, blue: 0xE6 / 255))
            .clipShape(RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
        .disabled(!viewModel.canShowConfirm)
    }

    private var numericKeypad: some View {
        VStack(spacing: 6) {
            ForEach(keypadRows, id: \.self) { row in
                HStack(spacing: 6) {
                    ForEach(row, id: \.self) { key in
                        keyView(key: key)
                    }
                }
            }
        }
        .padding(.bottom, 8)
    }

    private var keypadRows: [[KeypadKey]] {
        [
            [.digit(1), .digit(2), .digit(3)],
            [.digit(4), .digit(5), .digit(6)],
            [.digit(7), .digit(8), .digit(9)],
            [.empty, .digit(0), .delete],
        ]
    }

    private func keyView(key: KeypadKey) -> some View {
        let enabled = viewModel.submission != .submitting
        return Button(action: {
            switch key {
            case let .digit(value): viewModel.appendDigit(value)
            case .delete: viewModel.deleteDigit()
            case .empty: break
            }
        }) {
            ZStack {
                if case .empty = key {
                    Color.clear
                } else {
                    FujuBankPalette.surface
                }
                switch key {
                case let .digit(value):
                    Text("\(value)")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundStyle(FujuBankPalette.textPrimary)
                case .delete:
                    Text("⌫")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundStyle(FujuBankPalette.textPrimary)
                case .empty:
                    EmptyView()
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 48)
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
        .disabled(!enabled || key == .empty)
    }
}

private enum KeypadKey: Hashable {
    case digit(Int)
    case delete
    case empty
}

/// 「ふじゅ〜」単位文字列。Android 側 `CurrencyFormatter.UNIT` と一致。
private let currencyUnit = "ふじゅ〜"

/// 桁区切り `12,345` 形式に整形する。
/// Android 側 `CurrencyFormatter.formatAmount` と挙動を合わせる（負数は先頭に `-`）。
private func formatAmount(_ amount: Int64) -> String {
    let formatter = NumberFormatter()
    formatter.numberStyle = .decimal
    formatter.groupingSeparator = ","
    formatter.usesGroupingSeparator = true
    return formatter.string(from: NSNumber(value: amount)) ?? "\(amount)"
}
