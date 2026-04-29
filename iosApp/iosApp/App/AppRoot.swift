import SwiftUI
import Shared

/// アプリ全体のルートビュー。SessionState を観測してログイン / MFA / ホームを切替える。
///
/// - Unauthenticated: LoginView を表示。
/// - MfaPending: MfaVerifyView を表示（pre_token 経由で AuthRepository.verifyMfa を叩く）。
/// - Authenticated: ホーム本体は A3 で実装するためプレースホルダを表示。
/// サインアップフローのローカルナビゲーション位置。
/// SessionState は変えずに UI 層だけでサインアップ 3 画面を切替える。
private enum SignupRoute {
    case none, create, otp, success
}

struct AppRoot: View {
    @StateObject private var session = SessionViewModel()
    @StateObject private var welcomeGate = WelcomeGateViewModel()
    @StateObject private var signupFlow = SignUpFlowState()
    // debug ビルド専用の認証スキップフラグ。SessionStore は触らず View 層だけで強制的に
    // AuthenticatedPlaceholderView を出す。プロセス kill で消える設計（永続化しない）。
    @State private var bypassAuth = false
    @State private var signupRoute: SignupRoute = .none

    var body: some View {
        Group {
            if bypassAuth {
                AuthenticatedPlaceholderView(userId: "debug-bypass")
            } else {
                switch session.state {
                case let mfa as SessionState.MfaPending:
                    MfaVerifyView(viewModel: MfaVerifyViewModel(preToken: mfa.preToken))
                        .id(mfa.preToken)
                case let auth as SessionState.Authenticated:
                    // サインアップ画面発の Authenticated 遷移 (pending) かつ未表示の場合のみ Welcome を挟む。
                    // bootstrap 復元による Authenticated は pending = false なので素通り。
                    if welcomeGate.shouldShowWelcome {
                        WelcomeView(onFinish: { welcomeGate.markShown() })
                    } else {
                        AuthenticatedPlaceholderView(userId: auth.userId)
                    }
                default:
                    unauthenticatedRouter
                }
            }
        }
        // bootstrap の起動は SplashGate に移管したのでここでは行わない。
        // SplashGate が bootstrapped == true を確認してから AppRoot を出すため、
        // 表示時点で SessionStore.state は復元済みになっている。
    }

    @ViewBuilder
    private var unauthenticatedRouter: some View {
        switch signupRoute {
        case .none:
            loginView
        case .create:
            SignUpCreateView(
                onNext: { signupRoute = .otp },
                onBack: { signupRoute = .none },
                onLoginRedirect: { signupRoute = .none },
            )
            .environmentObject(signupFlow)
        case .otp:
            SignUpOtpView(
                onConfirm: { signupRoute = .success },
                onBack: { signupRoute = .create },
            )
            .environmentObject(signupFlow)
        case .success:
            SignUpSuccessView(onFinish: {
                signupFlow.reset()
                signupRoute = .none
            })
        }
    }

    private var loginView: some View {
        // release ビルドでは onDebugSkip パラメータを渡さず、`#if DEBUG` ブロック内のシンボルが
        // 一切残らない設計にする。LoginView 側の onDebugSkip もデフォルト nil なので、
        // release では debug 用 CTA は完全に消える。
        let signupTap: () -> Void = {
            signupFlow.reset()
            signupRoute = .create
        }
        #if DEBUG
        return LoginView(
            viewModel: LoginViewModel(),
            onSignupTap: signupTap,
            onDebugSkip: { bypassAuth = true },
        )
        #else
        return LoginView(viewModel: LoginViewModel(), onSignupTap: signupTap)
        #endif
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
