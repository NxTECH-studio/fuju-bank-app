import Foundation
import Shared

/// `SessionStore.state` を SwiftUI の `@Published` に転写する観測役。
///
/// shared 側の `SessionStoreIosKt.observeSession(_:onChange:)` が StateFlow から
/// `SessionState` を逐次 Swift クロージャに渡してくれるので、それを `@MainActor` に
/// 戻して `@Published` を更新する。Skie 等を入れていないため、StateFlow を Swift から
/// 直接触らない構成にしている。
@MainActor
final class SessionViewModel: ObservableObject {
    @Published private(set) var state: SessionState

    private var token: FlowToken?
    private let sessionStore: SessionStore

    init() {
        let store = KoinIosKt.sessionStore()
        self.sessionStore = store
        // SessionStore は MutableStateFlow を抱えているので state.value は必ず Unauthenticated を返す。
        // observeSession の collect は subscribe 直後に現在値を 1 回 emit するので state は即座に上書きされる。
        self.state = SessionState.Unauthenticated.shared
        self.token = SessionStoreIosKt.observeSession(store: store) { [weak self] newState in
            Task { @MainActor in
                self?.state = newState
            }
        }
    }

    deinit {
        token?.close()
    }

    /// アプリ起動時に呼んで、保存済み access / refresh cookie からセッションを復元する。
    func bootstrap() {
        SessionStoreIosKt.bootstrapSession(
            sessionStore: sessionStore,
            authRepository: KoinIosKt.authRepository(),
            userRepository: KoinIosKt.userRepository(),
            onComplete: {}
        )
    }
}
