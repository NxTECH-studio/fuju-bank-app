import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        KoinIosKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
            // 環境変数 `FUJUBANK_USE_COMPOSE=1` で Compose Multiplatform 経路に切替える。
            // Task 1 の動作確認用フラグであり、Task 2 完了後にデフォルト切替する予定。
            // 未設定 or `0` の場合は従来通り SplashGate -> AppRoot (SwiftUI) を表示する。
            if ProcessInfo.processInfo.environment["FUJUBANK_USE_COMPOSE"] == "1" {
                ComposeRootView()
            } else {
                // SplashGate が bootstrap 完了 + min-duration を待ってから AppRoot に切替える。
                SplashGate()
            }
        }
    }
}
