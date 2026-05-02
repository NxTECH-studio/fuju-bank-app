import Foundation
import Shared

/// 送金画面の進捗ステップ。
enum SendPhase {
    /// 入力中。フォーム編集とボタン押下が可能。
    case editing

    /// 確認ダイアログ表示中。
    case confirming

    /// API 通信中（二重 submit 防止のためボタンを disable）。
    case submitting
}

/// 送金成功時に View 側へ 1 度だけ伝えるイベント。
struct SendSuccessEvent: Equatable {
    let newBalance: Int64
    let transactionId: String
}

/// 送金画面の状態とアクションをまとめる ViewModel（SwiftUI の `ObservableObject`）。
///
/// shared 側 `SendFlowIos.submitTransfer` を呼んで送金を実行する。Idempotency-Key は
/// LedgerRepository が UUID を内部生成するため、ここでは MFA 用 retryKey の保持のみ行う。
@MainActor
final class SendViewModel: ObservableObject {
    @Published var recipientExternalId: String = ""
    @Published var amountFuju: String = ""
    @Published private(set) var phase: SendPhase = .editing
    @Published private(set) var errorMessage: String?
    @Published private(set) var successEvent: SendSuccessEvent?

    private let ledgerRepository: LedgerRepository
    private let sessionStore: SessionStore
    private var inFlight: Kotlinx_coroutines_coreJob?

    init() {
        self.ledgerRepository = KoinIosKt.ledgerRepository()
        self.sessionStore = KoinIosKt.sessionStore()
    }

    deinit {
        inFlight?.cancel(cause: nil)
    }

    /// 入力値から submit 可能かを判定する。受取人が空でなく、金額が正の整数で、Editing 状態のとき可。
    var canSubmit: Bool {
        guard phase == .editing else { return false }
        guard !recipientExternalId.trimmingCharacters(in: .whitespaces).isEmpty else { return false }
        guard parsedAmount != nil else { return false }
        return true
    }

    /// 数字のみ受理して Int64 に変換する。空文字 / 0 以下 / 桁あふれは nil。
    var parsedAmount: Int64? {
        let trimmed = amountFuju.trimmingCharacters(in: .whitespaces)
        guard let value = Int64(trimmed), value > 0 else { return nil }
        return value
    }

    func onRecipientChange(_ value: String) {
        // 改行は弾く。前後空白は submit 時に trim する。
        let sanitized = value.replacingOccurrences(of: "\n", with: "")
            .replacingOccurrences(of: "\r", with: "")
        recipientExternalId = sanitized
        errorMessage = nil
    }

    func onAmountChange(_ value: String) {
        // 数字のみ受理。
        let sanitized = value.filter { $0.isNumber }
        amountFuju = sanitized
        errorMessage = nil
    }

    func requestConfirm() {
        guard canSubmit else { return }
        phase = .confirming
    }

    func cancelConfirm() {
        guard phase == .confirming else { return }
        phase = .editing
    }

    func submit() {
        guard phase == .confirming, let amount = parsedAmount else { return }
        let recipient = recipientExternalId.trimmingCharacters(in: .whitespaces)
        guard !recipient.isEmpty else { return }

        inFlight?.cancel(cause: nil)
        phase = .submitting
        errorMessage = nil

        inFlight = SendFlowIosKt.submitTransfer(
            ledgerRepository: ledgerRepository,
            sessionStore: sessionStore,
            recipientExternalId: recipient,
            amount: amount,
            memo: nil,
            retryKey: nil,
        ) { [weak self] outcome in
            Task { @MainActor in
                guard let self else { return }
                self.handleOutcome(outcome)
            }
        }
    }

    /// Toast 表示が終わったあと View 側から呼ぶ。
    func consumeError() { errorMessage = nil }

    /// 成功イベントを navigate-back に消費したあと再発火を防ぐために呼ぶ。
    func consumeSuccess() { successEvent = nil }

    private func handleOutcome(_ outcome: SendOutcome) {
        switch outcome {
        case let success as SendOutcome.Success:
            phase = .editing
            successEvent = SendSuccessEvent(
                newBalance: success.newBalance,
                transactionId: success.transactionId,
            )
            // 連続送金時のミス防止のため入力欄をクリア。
            recipientExternalId = ""
            amountFuju = ""
        case is SendOutcome.InsufficientBalance:
            phase = .editing
            errorMessage = "残高が足りません"
        case is SendOutcome.RecipientNotFound:
            phase = .editing
            errorMessage = "送り先が見つかりません"
        case is SendOutcome.RateLimitExceeded:
            phase = .editing
            errorMessage = "リクエストが多すぎます。少し時間をおいてください"
        case is SendOutcome.MfaRequired:
            // MVP では MFA verify 画面が未実装のため、誘導メッセージのみ。
            phase = .editing
            errorMessage = "二段階認証が必要です（別タスクで実装予定）"
        case let failure as SendOutcome.Failure:
            phase = .editing
            errorMessage = failure.message
        case let netFailure as SendOutcome.NetworkFailure:
            phase = .editing
            errorMessage = netFailure.message
        case is SendOutcome.Unauthenticated:
            phase = .editing
            errorMessage = "セッションが切れました"
        default:
            phase = .editing
            errorMessage = "送金に失敗しました"
        }
    }
}
