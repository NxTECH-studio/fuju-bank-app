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
    /// 送金時に付与する任意メモ。Android `SendFlowState.memo` と対称。
    ///
    /// 入力時の即時切り詰め（didSet で `prefix(80)`）は、SwiftUI Binding 経由で View 側に値が
    /// 逆流する際に IME の marked text（変換中の未確定文字）と衝突し、日本語/中国語等の IME
    /// 入力が成立しなくなる（変換確定できない / 入力した文字が消える）ため行わない。
    /// 80 文字超過は `submit()` 内で `prefix(80)` により安全側で丸め、表示上のカウンタが
    /// 80 を超えた時点で warning 色に切り替わる UI で過入力をユーザーに気付かせる。
    /// `String.count` は Grapheme Cluster ベースのため Kotlin `String.length` (UTF-16 code unit)
    /// と非対称だが、80 文字程度の短文かつどちらも目視で違和感のない範囲のため UX 上は許容する。
    @Published var memo: String = ""
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
    /// memo 上限。Android `SendFlowViewModel.MEMO_MAX_LENGTH` と同期させる。
    static let memoMaxLength: Int = 80

    init() {
        self.userRepository = KoinIosKt.userRepository()
        self.ledgerRepository = KoinIosKt.ledgerRepository()
        self.profileRepository = KoinIosKt.profileRepository()
        self.sessionStore = KoinIosKt.sessionStore()
        loadBalance()
    }

    deinit {
        // Kotlin Job.cancel() / Swift Task.cancel() はどちらも thread-safe 仕様で、
        // deinit が main thread 以外で実行されても安全。Swift 5 では `@MainActor` 隔離
        // プロパティへの nonisolated アクセスは警告止まりだが、Swift 6 への移行時には
        // `MainActor.assumeIsolated` で囲むか non-isolated holder にリファクタする想定。
        // 現状は cancel API の thread-safe 性に依存して残置する（実害なし）。
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
        // 既存の debounce Task に加えて、すでに発火済みの searchToken（Kotlin Job）も
        // 早期キャンセルする。これがないと debounce 中に新クエリが来ても、前回の API
        // レスポンスが後から `searchState` を上書きしてしまうレース条件が残る。
        searchTask?.cancel()
        searchToken?.cancel(cause: nil)
        // クエリ分類は shared (`SendSearchGuardKt`) に集約し、Android / iOS で同じ仕様に揃える。
        // Kotlin enum entries は SCREAMING_SNAKE_CASE で宣言してあるため Swift では camelCase
        // (.empty / .tooShort / .invalid / .valid) で参照できる。
        switch SendSearchGuardKt.classifySendSearchQuery(query: query) {
        case SendSearchQueryClassification.empty:
            searchState = .idle
            return
        case SendSearchQueryClassification.tooShort:
            searchState = .needsMoreChars
            return
        case SendSearchQueryClassification.invalid:
            searchState = .invalidChars
            return
        case SendSearchQueryClassification.valid:
            break
        default:
            // 将来 enum case が追加された場合の防御。新ケース追加時はここを更新する。
            searchState = .idle
            return
        }
        let snapshot = query.trimmingCharacters(in: .whitespacesAndNewlines)
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

    private func runSearch(query: String) {
        searchState = .loading
        searchToken?.cancel(cause: nil)
        searchToken = SendFlowIosKt.searchRecipients(
            userRepository: userRepository,
            query: query,
        ) { [weak self] outcome in
            Task { @MainActor in
                guard let self else { return }
                // 古い検索のレスポンスが新しい入力を上書きしないよう、現在のクエリと
                // 一致するときだけ state を更新する（debounce + searchToken キャンセルでも
                // すり抜けるレースを 1 段強化）。
                let currentTrimmed = self.query.trimmingCharacters(in: .whitespacesAndNewlines)
                guard currentTrimmed == query else { return }
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
        memo = ""
        error = nil
        submission = .idle
        step = .amount
    }

    // MARK: - Step 2: amount entry

    func resetToRecipient() {
        step = .recipient
        recipient = nil
        amount = 0
        memo = ""
        error = nil
        submission = .idle
    }

    /// 金額入力。OS 標準の数字キーボード IME 経由で `TextField` から呼ばれる。
    /// Int64 範囲外は UI 側で `Int64(...)` が nil になり 0 扱いになるため、ここまで届かない前提。
    func onAmountChange(_ value: Int64) {
        amount = value
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
        // 送金中 / 送金成功直後の二重 submit を防御。`.success` の経路は通常 UI 側でホーム遷移
        // してから ViewModel が破棄されるため到達しないが、再入のレース対策として明示ガードを
        // 残す（`.mfaRequired` は再試行可能なのでガード対象外）。
        switch submission {
        case .submitting, .success:
            return
        case .idle, .mfaRequired:
            break
        }
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
        // 80 文字超過は submit 時にここで安全側に丸める（IME 干渉を避けるため didSet で
        // 切り詰めない方針。詳細は memo プロパティの doc コメント参照）。
        // 空文字 / 空白のみの memo は nil に正規化してサーバへ送る（Android 側と同じ挙動）。
        let truncatedMemo = String(memo.prefix(Self.memoMaxLength))
        let trimmedMemo = truncatedMemo.trimmingCharacters(in: .whitespacesAndNewlines)
        let memoForRequest: String? = trimmedMemo.isEmpty ? nil : truncatedMemo
        showAmountConfirm = false
        submission = .submitting
        error = nil
        transferToken?.cancel(cause: nil)
        transferToken = SendFlowIosKt.executeTransfer(
            ledgerRepository: ledgerRepository,
            fromUserId: from,
            toUserId: recipient.id,
            amount: amount,
            memo: memoForRequest,
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
