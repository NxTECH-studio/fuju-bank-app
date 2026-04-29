import SwiftUI
import Shared

/// アプリのルートで `SplashView` と本体 (`AppRoot`) を切り替えるゲート。
///
/// 振る舞い:
/// 1. 起動直後に `SessionStore.bootstrap()` を発火する（冪等なので再呼び出しは即 return）。
/// 2. `SplashViewModel.bootstrapped` が true になり、かつ `SplashConfig.minDuration` を
///    満たした瞬間に `splashFinished` を立て、`AppRoot` に切替える。
///
/// 旧 `AppRoot.task` から bootstrap を呼ぶと SplashView 表示中にコルーチンが
/// 起動されず bootstrapped が永久に false になるため、ここで起動するのが正しい。
struct SplashGate: View {
    @StateObject private var splash = SplashViewModel()
    @State private var splashFinished = false

    var body: some View {
        ZStack {
            if splashFinished {
                AppRoot()
                    .transition(.opacity)
            } else {
                SplashView()
                    .transition(.opacity)
                    .task {
                        await runSplashFlow()
                    }
            }
        }
        .animation(.easeInOut(duration: 0.2), value: splashFinished)
    }

    /// bootstrap 起動 → bootstrapped 観測 → min-duration 担保 を 1 つの async 処理にまとめる。
    private func runSplashFlow() async {
        let start = Date()

        // bootstrap は SessionStore.scope.launch 経由で別コルーチンで走る。
        // ここで onComplete を待たずに先へ進み、bootstrapped の Flow を待つ。
        AuthFlowIosKt.bootstrapSession(
            sessionStore: KoinIosKt.sessionStore(),
            authRepository: KoinIosKt.authRepository(),
            userRepository: KoinIosKt.userRepository(),
            onComplete: {}
        )

        // bootstrapped == true まで待つ。`@Published` を `.values` で AsyncSequence 化し、
        // true が来たら break する。SplashViewModel が deinit する前に値が来る前提。
        for await done in splash.$bootstrapped.values where done {
            break
        }

        let elapsed = Date().timeIntervalSince(start)
        let remaining = SplashConfig.minDuration - elapsed
        if remaining > 0 {
            try? await Task.sleep(nanoseconds: UInt64(remaining * 1_000_000_000))
        }
        splashFinished = true
    }
}
