import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        KoinIosKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
            // SplashGate が bootstrap 完了 + min-duration を待ってから AppRoot に切替える。
            SplashGate()
        }
    }
}
