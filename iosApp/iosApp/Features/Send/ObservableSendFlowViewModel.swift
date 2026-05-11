import Foundation
import Shared

/// 送金フロー（Step 1: 送金先選択 → Step 2: 金額入力 → 実行）の状態を束ねる ViewModel。
///
/// Android `SendFlowViewModel.kt` と対称な責務:
/// - 検索クエリの 300ms デバウンス + 2 文字未満ガード
/// - 候補タップ → bottom sheet 確認 → Step 2 進行
/// - 金額入力（カスタム数字パッドからの 1 桁追加 / 削除）
/// - 確認 alert → `LedgerRepository.transfer` 実行
/// - MFA 必要時の retryKey 保持（送金時 MFA 検証 API 整備までは fallback エラーで停止）
@MainActor
final class ObservableSendFlowViewModel: ObservableObject {
    enum Step { case recipient, amount }

    enum SearchState: Equatable {
        case idle
        case needsMoreChars
        /// 英数字以外を含む / 32 文字超など、サーバ側 public_id 仕様 (`/\A[a-zA-Z0-9]+\z/` 2..32) を
        /// 満たさないクエリ。検索 API は発火させず、UI 側でヒントを出して入力修正を促す。
        case invalidChars
        case loading
        case ready(results: [UserSearchResult])
        case error(message: String)

        // [UserSearchResult] は Kotlin 側 data class。Equatable 適合のため id ベースで比較する。
        static func == (lhs: SearchState, rhs: SearchState) -> Bool {
            switch (lhs, rhs) {
            case (.idle, .idle), (.needsMoreChars, .needsMoreChars),
                 (.invalidChars, .invalidChars), (.loading, .loading): return true
            case let (.ready(a), .ready(b)): return a.map { $0.id } == b.map { $0.id }
            case let (.error(a), .error(b)): return a == b
            default: return false
            }
        }
    }

    enum Submission: Equatable {
        case idle
        case submitting
        case mfaRequired(retryKey: String)
        case success(transactionId: String, newBalance: Int64)
    }

    @Published private(set) var step: Step = .recipient
    @Published var query: String = "" {
        didSet {
            if query != oldValue { scheduleSearch() }
        }
    }
    @Published private(set) var searchState: SearchState = .idle
    @Published var confirmCandidate: UserSearchResult?
    @Published private(set) var recipient: UserSearchResult?
    @Published private(set) var balance: Int64 = 0
    @Published var amount: Int64 = 0
    @Published var showAmountConfirm: Bool = false
    @Published private(set) var submission: Submission = .idle
    @Published private(set) var error: String?

    private let userRepository: UserRepository
    private let ledgerRepository: LedgerRepository
    private let profileRepository: ProfileRepository
    private let sessionStore: SessionStore

    private var searchToken: Kotlinx_coroutines_coreJob?
    private var transferToken: Kotlinx_coroutines_coreJob?
    /// 検索デバウンス用 Task。query が変わる度にキャンセル → 新規発火。
    private var searchTask: Task<Void, Never>?

    private static let searchDebounceMs: UInt64 = 300
    private static let minSearchLength: Int = 2
    /// サーバ側 public_id 上限 (`bank-backend` の `public_id` バリデーション `2..32`) に合わせる。
    /// これを超えた入力は API を叩く前に弾く。
    private static let maxSearchLength: Int = 32
    /// サーバ側 public_id 許容文字集合 (`/\A[a-zA-Z0-9]+\z/`) と一致。
    /// これを満たさない入力は API を叩く前に弾き、ユーザーにヒントを表示する。
    private static let searchRegex = try! NSRegularExpression(pattern: "^[a-zA-Z0-9]+$")

    init() {
        self.userRepository = KoinIosKt.userRepository()
        self.ledgerRepository = KoinIosKt.ledgerRepository()
        self.profileRepository = KoinIosKt.profileRepository()
        self.sessionStore = KoinIosKt.sessionStore()
        loadBalance()
    }

    deinit {
        searchToken?.cancel(cause: nil)
        transferToken?.cancel(cause: nil)
        searchTask?.cancel()
    }

    // MARK: - Balance

    private func loadBalance() {
        // ホームと同じ ProfileRepository から取り、CTA の残高超過判定に使う。
        // 失敗時は 0 のままで、サーバ側の二重検証に委ねる。
        ProfileFlowIosKt.fetchMyProfile(profileRepository: profileRepository) { [weak self] outcome in
            Task { @MainActor in
                guard let self else { return }
                if let loaded = outcome as? ProfileLoadOutcome.Loaded {
                    self.balance = loaded.profile.balanceFuju
                }
            }
        }
    }

    // MARK: - Step 1: search & candidate

    private func scheduleSearch() {
        searchTask?.cancel()
        let snapshot = query.trimmingCharacters(in: .whitespacesAndNewlines)
        if snapshot.isEmpty {
            searchState = .idle
            return
        }
        if snapshot.count < Self.minSearchLength {
            searchState = .needsMoreChars
            return
        }
        // サーバ側 public_id 仕様 (`/\A[a-zA-Z0-9]+\z/` 2..32) を満たさないクエリは API を
        // 発火させない。IME composition と相性が悪いため query setter 経由 (`scheduleSearch` 起動時)
        // でだけ判定する（妥協案）。
        if snapshot.count > Self.maxSearchLength || !Self.matchesSearchRegex(snapshot) {
            searchState = .invalidChars
            return
        }
        searchTask = Task { [weak self] in
            // try? だと sleep がキャンセルされても続行してしまうため、
            // do-catch で確実に早期 return させる。
            do {
                try await Task.sleep(nanoseconds: Self.searchDebounceMs * 1_000_000)
            } catch {
                return
            }
            guard let self else { return }
            if Task.isCancelled { return }
            await MainActor.run { self.runSearch(query: snapshot) }
        }
    }

    private static func matchesSearchRegex(_ s: String) -> Bool {
        let range = NSRange(s.startIndex..<s.endIndex, in: s)
        return searchRegex.firstMatch(in: s, options: [], range: range) != nil
    }

    private func runSearch(query: String) {
        searchState = .loading
        searchToken?.cancel(cause: nil)
        searchToken = SendFlowIosKt.searchRecipients(
            userRepository: userRepository,
            query: query,
        ) { [weak self] outcome in
            Task { @MainActor in
                guard let self else { return }
                switch outcome {
                case let loaded as UserSearchOutcome.Loaded:
                    self.searchState = .ready(results: loaded.results)
                case let failure as UserSearchOutcome.Failure:
                    self.searchState = .error(message: failure.message)
                case let netFailure as UserSearchOutcome.NetworkFailure:
                    self.searchState = .error(message: netFailure.message)
                default:
                    self.searchState = .error(message: "検索に失敗しました")
                }
            }
        }
    }

    func tapCandidate(_ candidate: UserSearchResult) {
        confirmCandidate = candidate
    }

    func cancelCandidateConfirm() {
        confirmCandidate = nil
    }

    /// bottom sheet「決定」CTA で Step 2 に遷移する。
    func confirmCandidate(_ candidate: UserSearchResult) {
        confirmCandidate = nil
        recipient = candidate
        amount = 0
        error = nil
        submission = .idle
        step = .amount
    }

    // MARK: - Step 2: amount entry

    func resetToRecipient() {
        step = .recipient
        recipient = nil
        amount = 0
        error = nil
        submission = .idle
    }

    func appendDigit(_ digit: Int) {
        precondition((0...9).contains(digit), "digit must be 0..9")
        let next = amount &* 10 &+ Int64(digit)
        // overflow 検知: &* / &+ は wrap 演算なので除算で逆変換できなければ overflow。
        if amount != 0 && next / 10 != amount { return }
        amount = next
        error = nil
    }

    func deleteDigit() {
        amount = amount / 10
        error = nil
    }

    var isOverBalance: Bool { amount > balance }
    var canShowConfirm: Bool {
        recipient != nil && amount > 0 && !isOverBalance && submission != .submitting
    }

    func presentAmountConfirm() {
        guard canShowConfirm else { return }
        showAmountConfirm = true
    }

    func dismissAmountConfirm() {
        showAmountConfirm = false
    }

    /// alert の「送金する」CTA から呼ばれる。MFA 経路では retryKey を引き継ぐ。
    func submit() {
        guard let recipient else { return }
        guard let from = (sessionStore.current as? SessionState.Authenticated)?.userId else {
            showAmountConfirm = false
            error = "セッションが無効です。もう一度ログインしてください"
            return
        }
        let retryKey: String? = {
            if case let .mfaRequired(key) = submission { return key }
            return nil
        }()
        showAmountConfirm = false
        submission = .submitting
        error = nil
        transferToken?.cancel(cause: nil)
        transferToken = SendFlowIosKt.executeTransfer(
            ledgerRepository: ledgerRepository,
            fromUserId: from,
            toUserId: recipient.id,
            amount: amount,
            retryKey: retryKey,
        ) { [weak self] outcome in
            Task { @MainActor in
                guard let self else { return }
                self.applyTransferOutcome(outcome)
            }
        }
    }

    private func applyTransferOutcome(_ outcome: TransferOutcome) {
        switch outcome {
        case let success as TransferOutcome.Success:
            balance = success.newBalance
            submission = .success(transactionId: success.transactionId, newBalance: success.newBalance)
        case let mfa as TransferOutcome.MfaRequired:
            submission = .mfaRequired(retryKey: mfa.retryKey)
            error = "送金時の二段階認証は現在準備中です。後ほど再度お試しください"
        case let failure as TransferOutcome.Failure:
            if failure.reset {
                step = .recipient
                recipient = nil
                amount = 0
                submission = .idle
            } else {
                submission = .idle
            }
            error = failure.message
        case let netFailure as TransferOutcome.NetworkFailure:
            submission = .idle
            error = netFailure.message
        default:
            submission = .idle
            error = "送金に失敗しました"
        }
    }
}
