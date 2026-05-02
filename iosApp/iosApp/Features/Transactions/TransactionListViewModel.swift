import Foundation
import Shared

/// 取引履歴画面の表示状態。
enum TransactionListUiState {
    case loading
    case loaded(transactions: [Shared.Transaction], refreshing: Bool)
    case error(message: String)
}

/// 取引履歴画面の状態とアクションをまとめる ViewModel。
///
/// shared 側 `TransactionsFlowIos.fetchMyTransactions` を呼んで取引一覧を取得する。
@MainActor
final class TransactionListViewModel: ObservableObject {
    @Published private(set) var state: TransactionListUiState = .loading

    private let userRepository: UserRepository
    private let sessionStore: SessionStore
    private var inFlight: Kotlinx_coroutines_coreJob?

    init() {
        self.userRepository = KoinIosKt.userRepository()
        self.sessionStore = KoinIosKt.sessionStore()
    }

    deinit {
        // deinit は @MainActor 隔離外で実行されるが、Kotlinx.coroutines の Job.cancel は
        // thread-safe なため、ここから直接 cancel しても安全。
        inFlight?.cancel(cause: nil)
    }

    func onAppear() {
        if case .loading = state {
            load(initial: true)
        }
    }

    func refresh() {
        load(initial: false, completion: nil)
    }

    /// `.refreshable` 用の async 版 refresh。fetch 完了まで待つことで、引きおろしジェスチャの
    /// インジケータがジョブ完了前に閉じないようにする。
    func refreshAwait() async {
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            load(initial: false) {
                continuation.resume()
            }
        }
    }

    private func load(initial: Bool, completion: (@MainActor () -> Void)? = nil) {
        inFlight?.cancel(cause: nil)
        if !initial, case let .loaded(items, _) = state {
            state = .loaded(transactions: items, refreshing: true)
        }
        inFlight = TransactionsFlowIosKt.fetchMyTransactions(
            userRepository: userRepository,
            sessionStore: sessionStore,
        ) { [weak self] outcome in
            Task { @MainActor in
                guard let self else {
                    completion?()
                    return
                }
                switch outcome {
                case let loaded as TransactionsLoadOutcome.Loaded:
                    self.state = .loaded(transactions: loaded.transactions, refreshing: false)
                case let failure as TransactionsLoadOutcome.Failure:
                    self.state = .error(message: failure.message)
                case let netFailure as TransactionsLoadOutcome.NetworkFailure:
                    self.state = .error(message: netFailure.message)
                case is TransactionsLoadOutcome.Unauthenticated:
                    self.state = .error(message: "セッションが切れました")
                default:
                    self.state = .error(message: "未知のエラーが発生しました")
                }
                completion?()
            }
        }
    }
}
