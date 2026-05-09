import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        KoinIosKt.doInitKoin()
        // Authenticated → Unauthenticated 遷移時に Provider singleton キャッシュを破棄する観測を
        // プロセス起動時に 1 回だけ起動する。`@main App.init` は SwiftUI ライフサイクル上
        // プロセス毎 1 回しか呼ばれない想定だが、Coordinator 側にも二重起動防止フラグを持たせ
        // テスト/プレビュー等で再評価された場合でも安全に no-op となるようにしている。
        KoinIosKt.sessionResetCoordinator().start()
    }

    var body: some Scene {
        WindowGroup {
            // SplashGate が bootstrap 完了 + min-duration を待ってから AppRoot に切替える。
            SplashGate()
        }
    }
}
