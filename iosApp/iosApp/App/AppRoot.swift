import SwiftUI
import Shared

/// アプリ全体のルートビュー。SessionState を観測してログイン / MFA / ホームを切替える。
///
/// - Unauthenticated: LoginView を表示。
/// - MfaPending: MfaVerifyView を表示（pre_token 経由で AuthRepository.verifyMfa を叩く）。
/// - Authenticated: ホーム本体は A3 で実装するためプレースホルダを表示。
struct AppRoot: View {
    @StateObject private var session = SessionViewModel()

    var body: some View {
        Group {
            switch session.state {
            case let mfa as SessionState.MfaPending:
                MfaVerifyView(viewModel: MfaVerifyViewModel(preToken: mfa.preToken))
                    .id(mfa.preToken)
            case let auth as SessionState.Authenticated:
                AuthenticatedPlaceholderView(userId: auth.userId)
            default:
                LoginView(viewModel: LoginViewModel())
            }
        }
        // bootstrap の起動は SplashGate に移管したのでここでは行わない。
        // SplashGate が bootstrapped == true を確認してから AppRoot を出すため、
        // 表示時点で SessionStore.state は復元済みになっている。
    }
}

/// ログイン済み画面のプレースホルダ。A3 で HomeView に置き換える。
struct AuthenticatedPlaceholderView: View {
    let userId: String

    var body: some View {
        VStack(spacing: 12) {
            Text("ログイン済み")
                .font(.headline)
            Text(userId)
                .font(.caption)
                .foregroundColor(.secondary)
            Text("A3 でホーム画面を実装します")
                .font(.footnote)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding()
    }
}
