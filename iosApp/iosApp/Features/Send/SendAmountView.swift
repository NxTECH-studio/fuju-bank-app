import SwiftUI
import Shared

/// 送金フロー Step 2 — 金額入力 + プレビュー画面（iOS）。
///
/// 金額入力欄（数字キーボード IME）+ 残高プレビューカード + メモ入力欄（標準 IME）+ ピンク CTA
/// + 確認 alert。完了時は [onComplete] を呼び、親で Snackbar 相当の Toast 表示 + ホーム自動遷移を行う。
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
                    Text("@\(recipient.publicId) さんに \(formatAmount(viewModel.amount))\(currencyUnit) 送りますか？")
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
            amountField
            if viewModel.isOverBalance {
                Text("残高が不足しています")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Color.red)
            }
            balancePreviewCard
            memoField
            if let err = viewModel.error {
                Text(err)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Color.red)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
            }
            Spacer(minLength: 0)
            sendCta
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
            Text("@" + recipient.publicId)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(FujuBankPalette.textPrimary)
            Spacer()
        }
        .padding(.top, 4)
    }

    /// 金額入力欄。OS 標準の数字キーボード IME を起動し、メモ欄 (`memoField`) と並列の
    /// 「2 つの入力欄」UI として機能する。旧実装の `amountDisplay` + `numericKeypad` は撤去し、
    /// tap 対象（金額 vs メモ）に応じて IME が出し分けられる構成へ変更した。
    ///
    /// - `.keyboardType(.numberPad)` で数字キーボード IME。
    /// - 数字以外を `set` 内で除去、先頭 0 を `drop(while:)` で正規化、`Int64(...)` で parse。
    ///   Int64 範囲外は nil になり 0 扱いで安全側へ。
    private var amountField: some View {
        let amountText = viewModel.amount == 0 ? "" : String(viewModel.amount)
        let binding = Binding<String>(
            get: { amountText },
            set: { newValue in
                let digitsOnly = newValue.filter { $0.isNumber }
                let normalized = String(digitsOnly.drop(while: { $0 == "0" }))
                let parsed = Int64(normalized) ?? 0
                viewModel.onAmountChange(parsed)
            },
        )
        return HStack(alignment: .firstTextBaseline, spacing: 8) {
            TextField("0", text: binding)
                .keyboardType(.numberPad)
                .multilineTextAlignment(.trailing)
                .font(.system(size: 28, weight: .bold))
                .foregroundStyle(FujuBankPalette.textPrimary)
                .disabled(viewModel.submission == .submitting)
                .frame(maxWidth: .infinity)
            Text(currencyUnit)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(FujuBankPalette.textSecondary)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(FujuBankPalette.surface)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(FujuBankPalette.hairline, lineWidth: 1),
        )
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .padding(.top, 8)
    }

    /// 任意メモ入力欄 + `n/80` カウンタ。Android 側 `MemoField` と対称。
    ///
    /// - 80 文字上限のクライアントガードは `submit()` 内で `prefix(80)` により安全側に丸める
    ///   （IME 干渉を避けるため入力時の即時切り詰めは行わない / 詳細は ViewModel `memo` 参照）。
    /// - `axis: .vertical` + `lineLimit(1...3)` でメモらしい複数行入力を許容する。
    /// - 残量が 10 文字以下になったらカウンタを警告色 (red) に切り替える。
    private var memoField: some View {
        let length = viewModel.memo.count
        let remaining = ObservableSendFlowViewModel.memoMaxLength - length
        let counterColor: Color = remaining <= memoRemainingWarnThreshold
            ? Color.red
            : FujuBankPalette.textSecondary
        return VStack(alignment: .trailing, spacing: 4) {
            TextField(
                "メモ（任意・80文字まで）",
                text: $viewModel.memo,
                axis: .vertical,
            )
            .lineLimit(1...3)
            .font(.system(size: 14, weight: .regular))
            .foregroundStyle(FujuBankPalette.textPrimary)
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(FujuBankPalette.surface)
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(FujuBankPalette.hairline, lineWidth: 1),
            )
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .disabled(viewModel.submission == .submitting)
            Text("\(length)/\(ObservableSendFlowViewModel.memoMaxLength)")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(counterColor)
        }
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
        .background(FujuBankPalette.lightPink)
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
            .background(viewModel.canShowConfirm ? FujuBankPalette.brandPink : FujuBankPalette.disabledButtonBg)
            .clipShape(RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
        .disabled(!viewModel.canShowConfirm)
    }

}

/// memo カウンタの警告色しきい値。残量がこの値以下になったら red に切り替える。
private let memoRemainingWarnThreshold: Int = 10

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
