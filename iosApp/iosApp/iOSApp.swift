import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        KoinIosKt.doInitKoin()
        // Authenticated → Unauthenticated 遷移時に Provider singleton キャッシュを破棄する観測を
        // プロセス起動時に 1 回だけ起動する。Coordinator 内で二重起動防止フラグがあるため、
        // ホットリロード等で `init` が複数回走っても安全。
        KoinIosKt.sessionResetCoordinator().start()
    }

    var body: some Scene {
        WindowGroup {
            // SplashGate が bootstrap 完了 + min-duration を待ってから AppRoot に切替える。
            SplashGate()
        }
    }
}
