import Foundation
import Shared

/// `SessionStore.bootstrapped` を Swift の `@Published` に転写する観測役。
///
/// `SessionViewModel` が `SessionState` を観測しているのと同じ構造。Skie を入れていないため、
/// shared 側の `SessionStoreIosKt.observeBootstrapped(_:onChange:)` を経由して
/// Kotlin の StateFlow を Swift クロージャで受け取る。
@MainActor
final class SplashViewModel: ObservableObject {
    @Published private(set) var bootstrapped: Bool = false

    private var token: FlowToken?

    init() {
        let store = KoinIosKt.sessionStore()
        // observeBootstrapped は subscribe 直後に現在値を 1 回 emit する。
        // 初期化時点で既に true（再起動などで bootstrap 済み）なら直後に true になる。
        self.token = SessionStoreIosKt.observeBootstrapped(store: store) { [weak self] value in
            // Kotlin の Boolean は Swift から KotlinBoolean として渡ってくる。
            let done = value.boolValue
            Task { @MainActor in
                self?.bootstrapped = done
            }
        }
    }

    deinit {
        token?.close()
    }
}
