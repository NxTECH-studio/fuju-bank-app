import SwiftUI

/// サインアップ完了直後に 1 度だけ表示する Welcome シーケンス（Figma node 383-16889 → 383-17075）。
///
/// 「ようこそ」テキスト → fuju pay ロゴへのクロスフェード自動遷移。
/// 完了時に `onFinish` を呼び、呼び出し側で `signupWelcomePreferences.markCompleted()` /
/// `signupCompletionSignal.consume()` を行ってホームへ進める。
///
/// 表示時間は Compose 側 `WelcomeScreen` と揃える。
struct WelcomeView: View {
    let onFinish: () -> Void

    @State private var phase: Phase = .text

    var body: some View {
        ZStack {
            Color("FujuSplashBackground")
                .ignoresSafeArea()
            Text("ようこそ")
                .font(.system(size: 40, weight: .bold))
                .foregroundColor(Color(red: 0x11 / 255, green: 0x11 / 255, blue: 0x11 / 255))
                .multilineTextAlignment(.center)
                .opacity(phase == .text ? 1 : 0)
                .animation(.easeInOut(duration: Self.crossfadeSeconds), value: phase)
            Image("FujuLogo")
                .resizable()
                .scaledToFit()
                .frame(width: 195)
                .opacity(phase == .logo ? 1 : 0)
                .animation(.easeInOut(duration: Self.crossfadeSeconds), value: phase)
        }
        .task {
            try? await Task.sleep(nanoseconds: UInt64(Self.textVisibleSeconds * 1_000_000_000))
            phase = .logo
            try? await Task.sleep(
                nanoseconds: UInt64((Self.crossfadeSeconds + Self.logoVisibleSeconds) * 1_000_000_000),
            )
            onFinish()
        }
    }

    private enum Phase { case text, logo }

    private static let textVisibleSeconds: Double = 1.2
    private static let crossfadeSeconds: Double = 0.6
    private static let logoVisibleSeconds: Double = 1.5
}

#Preview {
    WelcomeView(onFinish: {})
}
