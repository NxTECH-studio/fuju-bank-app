import Foundation
import Shared

/// ホーム画面の表示状態。
///
/// - `loading`: 初回 fetch 中。
/// - `loaded(profile, revealed)`: プロフィール取得済み。`revealed` は残高マスク解除フラグ。
/// - `error(message)`: 通信失敗 or サーバーエラー。
enum HomeUiState {
    case loading
    case loaded(profile: UserProfile, revealed: Bool)
    case error(message: String)
}

/// ホーム画面の状態とアクションをまとめる ViewModel（SwiftUI の `ObservableObject`）。
///
/// shared 側 `ProfileFlowIos.fetchMyProfile` を呼んで `UserProfile` を取得し、
/// マスク解除トグル / リフレッシュを提供する。
///
/// TODO(A6): realtimeRepository.events を collect して残高ライブ更新する slot
@MainActor
final class HomeViewModel: ObservableObject {
    @Published private(set) var state: HomeUiState = .loading

    private let profileRepository: ProfileRepository
    // 進行中の fetch Job。新しい load() でキャンセルし、古い結果が後勝ちで state を上書きしないようにする。
    private var inFlight: Kotlinx_coroutines_coreJob?

    init() {
        self.profileRepository = KoinIosKt.profileRepository()
    }

    deinit {
        // deinit は @MainActor 隔離外で実行されるが、Kotlinx.coroutines の Job.cancel は
        // thread-safe なため、ここから直接 cancel しても安全。Swift 6 strict-concurrency
        // が有効になった場合は `nonisolated(unsafe)` か Task ラップに切替える。
        inFlight?.cancel(cause: nil)
    }

    func onAppear() {
        if case .loading = state {
            load()
        }
    }

    func toggleReveal() {
        guard case let .loaded(profile, revealed) = state else { return }
        state = .loaded(profile: profile, revealed: !revealed)
    }

    func refresh() {
        load()
    }

    private func load() {
        inFlight?.cancel(cause: nil)
        inFlight = ProfileFlowIosKt.fetchMyProfile(profileRepository: profileRepository) { [weak self] outcome in
            Task { @MainActor in
                guard let self else { return }
                switch outcome {
                case let loaded as ProfileLoadOutcome.Loaded:
                    let revealed: Bool
                    if case let .loaded(_, current) = self.state { revealed = current } else { revealed = false }
                    self.state = .loaded(profile: loaded.profile, revealed: revealed)
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
}
