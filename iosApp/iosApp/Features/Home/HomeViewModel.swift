import Foundation
import Shared

/// ホーム画面の表示状態。
///
/// - `loading`: 初回 fetch 中。
/// - `loaded(profile, revealed, recentTransactions)`: プロフィール取得済み。
///   `revealed` は残高マスク解除フラグ。`recentTransactions` は最近の取引セクションの
///   内部状態（profile と独立に進む）。
/// - `error(message)`: 通信失敗 or サーバーエラー。
enum HomeUiState {
    case loading
    case loaded(profile: UserProfile, revealed: Bool, recentTransactions: RecentTransactionsState)
    case error(message: String)
}

/// ホーム画面「最近の取引」セクションの内部状態。
///
/// profile 取得とは独立に進行する。Recent 側の失敗は profile が成功していればホーム本体を
/// 落とさず、セクション内に「読み込めませんでした」+ 再試行ボタンを表示する。
enum RecentTransactionsState {
    case loading
    case ready(items: [RecentTransactionItem])
    case error(message: String)
}

/// ホーム画面の状態とアクションをまとめる ViewModel（SwiftUI の `ObservableObject`）。
///
/// shared 側 `ProfileFlowIos.fetchMyProfile` と `TransactionsFlowIos.fetchRecentTransactions`
/// を **同時に kick** し、それぞれ独立にコールバックで state を更新する（Android の
/// `coroutineScope { async / async }` と等価な並列実行）。
///
/// profile が解決される前に recent が完了したケースは `pendingRecent` に保持し、
/// profile 結果が来たタイミングでまとめて `.loaded` に合流させる。
///
/// TODO(A6): realtimeRepository.events を collect して残高ライブ更新する slot
@MainActor
final class HomeViewModel: ObservableObject {
    /// ホームの「最近の取引」セクションに表示する件数。Android 側 `HomeViewModel.RECENT_LIMIT` と揃える。
    private static let recentLimit: Int32 = 3

    @Published private(set) var state: HomeUiState = .loading

    private let profileRepository: ProfileRepository
    private let userRepository: UserRepository
    private let sessionStore: SessionStore
    // 進行中の fetch Job。新しい load() でキャンセルし、古い結果が後勝ちで state を上書きしないようにする。
    private var inFlightProfile: Kotlinx_coroutines_coreJob?
    private var inFlightRecent: Kotlinx_coroutines_coreJob?
    // profile が解決される前に recent が先着したときの一時保管。profile 結果到着時に
    // 読み出して `.loaded` の `recentTransactions` に流し込む。
    private var pendingRecent: RecentTransactionsState = .loading

    init() {
        self.profileRepository = KoinIosKt.profileRepository()
        self.userRepository = KoinIosKt.userRepository()
        self.sessionStore = KoinIosKt.sessionStore()
    }

    deinit {
        // deinit は @MainActor 隔離外で実行されるが、Kotlinx.coroutines の Job.cancel は
        // thread-safe なため、ここから直接 cancel しても安全。Swift 6 strict-concurrency
        // が有効になった場合は `nonisolated(unsafe)` か Task ラップに切替える。
        inFlightProfile?.cancel(cause: nil)
        inFlightRecent?.cancel(cause: nil)
    }

    func onAppear() {
        if case .loading = state {
            load()
        }
    }

    func toggleReveal() {
        guard case let .loaded(profile, revealed, recent) = state else { return }
        state = .loaded(profile: profile, revealed: !revealed, recentTransactions: recent)
    }

    func refresh() {
        load()
    }

    /// 最近の取引セクションだけ再取得する。エラー時の再試行ボタンから呼ばれる。
    func refreshRecent() {
        // profile 側はそのまま、Recent のみ Loading に戻して再取得する。
        if case let .loaded(profile, revealed, _) = state {
            state = .loaded(profile: profile, revealed: revealed, recentTransactions: .loading)
        }
        pendingRecent = .loading
        kickRecent()
    }

    private func load() {
        inFlightProfile?.cancel(cause: nil)
        inFlightRecent?.cancel(cause: nil)
        pendingRecent = .loading
        // refresh 中も Recent セクションは loading 表示にして、
        // Android 側 (HomeViewModel.kt:87-94) と挙動を揃える。
        // profile は前回値を保持しつつ refreshing 相当の見せ方にする。
        if case let .loaded(profile, revealed, _) = state {
            state = .loaded(profile: profile, revealed: revealed, recentTransactions: .loading)
        }
        kickProfile()
        kickRecent()
    }

    private func kickProfile() {
        inFlightProfile = ProfileFlowIosKt.fetchMyProfile(profileRepository: profileRepository) { [weak self] outcome in
            Task { @MainActor in
                guard let self else { return }
                switch outcome {
                case let loaded as ProfileLoadOutcome.Loaded:
                    // 既に loaded であれば revealed / recent を引き継ぐ。loading の場合は
                    // pendingRecent から recent を引き出して合流させる。
                    let revealed: Bool
                    let recent: RecentTransactionsState
                    if case let .loaded(_, currentRevealed, currentRecent) = self.state {
                        revealed = currentRevealed
                        recent = currentRecent
                    } else {
                        revealed = false
                        recent = self.pendingRecent
                    }
                    self.state = .loaded(profile: loaded.profile, revealed: revealed, recentTransactions: recent)
                case let failure as ProfileLoadOutcome.Failure:
                    self.state = .error(message: failure.message)
                case let netFailure as ProfileLoadOutcome.NetworkFailure:
                    self.state = .error(message: netFailure.message)
                default:
                    self.state = .error(message: "未知のエラーが発生しました")
                }
            }
        }
    }

    private func kickRecent() {
        inFlightRecent = TransactionsFlowIosKt.fetchRecentTransactions(
            userRepository: userRepository,
            sessionStore: sessionStore,
            limit: Self.recentLimit,
        ) { [weak self] outcome in
            Task { @MainActor in
                guard let self else { return }
                let nextRecent: RecentTransactionsState
                switch outcome {
                case let loaded as TransactionsLoadOutcome.Loaded:
                    let items = loaded.transactions.map { RecentTransactionItem.fromShared($0) }
                    nextRecent = .ready(items: items)
                case let failure as TransactionsLoadOutcome.Failure:
                    nextRecent = .error(message: failure.message)
                case let netFailure as TransactionsLoadOutcome.NetworkFailure:
                    nextRecent = .error(message: netFailure.message)
                case is TransactionsLoadOutcome.Unauthenticated:
                    // ホーム本体を落とさず、Recent セクションだけエラー表示にする。
                    nextRecent = .error(message: "最近の取引を取得できませんでした")
                default:
                    // TransactionsLoadOutcome は shared sealed class。Swift には網羅性が
                    // 効かないため将来 case 追加時の保険。debug ビルドで気づけるよう assertionFailure。
                    assertionFailure("Unknown TransactionsLoadOutcome subtype: \(outcome)")
                    nextRecent = .error(message: "最近の取引を取得できませんでした")
                }
                self.pendingRecent = nextRecent
                switch self.state {
                case let .loaded(profile, revealed, _):
                    // profile 確定済みなら recent だけ差し替え。
                    self.state = .loaded(profile: profile, revealed: revealed, recentTransactions: nextRecent)
                case .loading:
                    // profile がまだ未確定なので、ここでは state を書き換えず pendingRecent に
                    // 保持しておく。profile 解決時に kickProfile 側が pendingRecent を読み出して合流させる。
                    break
                case .error:
                    // profile 失敗で全画面エラーが優先される。recent 結果は無視する。
                    break
                }
            }
        }
    }
}
