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
            case let auth as SessionState.Authenticated:
                AuthenticatedPlaceholderView(userId: auth.userId)
            default:
                LoginView(viewModel: LoginViewModel())
            }
        }
        .onAppear {
            // 起動 1 回だけセッション復元を試みる。SwiftUI の onAppear は再表示でも呼ばれるため
            // 実害は無いが冗長なので bootstrapped フラグを 1 つ持たせる手も後で検討する。
            session.bootstrap()
        }
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
