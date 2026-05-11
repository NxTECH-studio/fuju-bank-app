import SwiftUI
import Shared

/// アプリ全体のルートビュー。SessionState を観測してログイン / MFA / ホームを切替える。
///
/// - Unauthenticated: LoginView を表示。サインアップ動線は signupRoute で分岐。
/// - MfaPending: MfaVerifyView を表示（pre_token 経由で AuthRepository.verifyMfa を叩く）。
/// - MfaSetupRequired: MFA 未セットアップユーザのための QR/コード/recovery codes 画面群。
/// - Authenticated: Welcome → ホーム。
/// サインアップフローのローカルナビゲーション位置（2 値）。
/// `Active` のときの実画面は SignUpFlowState.phase で決まる。
private enum SignupRoute {
    case none, active
}

struct AppRoot: View {
    @StateObject private var session = SessionViewModel()
    @StateObject private var welcomeGate = WelcomeGateViewModel()
    @StateObject private var signupFlow = SignUpFlowState()
    @State private var bypassAuth = false
    @State private var signupRoute: SignupRoute = .none
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        Group {
            if bypassAuth {
                RootTabView()
            } else {
                switch session.state {
                case let mfa as SessionState.MfaPending:
                    MfaVerifyView(viewModel: MfaVerifyViewModel(preToken: mfa.preToken))
                        .id(mfa.preToken)
                case is SessionState.MfaSetupRequired:
                    // 既存ユーザ resume: SignUpFlowState を再利用して MFA セットアップ画面群に誘導。
                    // 入口は MfaQr 固定。`SignUpFlowState` を新規生成して Phase は `.mfaQr` に強制する。
                    MfaSetupResumeView()
                case let authenticated as SessionState.Authenticated:
                    if welcomeGate.shouldShowWelcome {
                        WelcomeView(onFinish: { welcomeGate.markShown() })
                    } else {
                        // RootTabView は内部に `@StateObject` で SendFlow VM 等を保持し、
                        // HomeView も `@StateObject` で HomeViewModel を抱えるため、別ユーザに
                        // ログインし直したとき同一 View インスタンスが再利用されると前ユーザの
                        // _state が残ったまま表示される。`bankUserId` を View id に組み込み、
                        // ユーザが変わったら View tree ごと再生成して新ユーザ用に init し直す。
                        RootTabView()
                            .id(authenticated.bankUserId)
                    }
                default:
                    unauthenticatedRouter
                }
            }
        }
        .onChange(of: scenePhase) { _, newPhase in
            guard newPhase == .active else { return }
            Task {
                try? await KoinIosKt.tokenExpiryWatcher().checkNow()
            }
        }
        // Authenticated に遷移したタイミングでサインアップ動線をリセット。これをやらないと、
        // ログアウトで Unauthenticated に戻った瞬間に signupRoute = .active かつ
        // signupFlow.phase = .recoveryCodes が残っていて RecoveryCodesView が再表示される。
        .onChange(of: session.state) { _, newState in
            if newState is SessionState.Authenticated {
                if signupRoute != .none {
                    signupRoute = .none
                }
                // signupFlow は @StateObject なので AppRoot のライフタイム中保持される。
                // 次回サインアップを開始する時に LoginView の signupTap で改めて reset() されるが、
                // ここでも明示的に reset しておくことで Authenticated 中の再描画で古い phase を
                // 触らないことを保証する。
                signupFlow.reset()
            }
        }
    }

    @ViewBuilder
    private var unauthenticatedRouter: some View {
        switch signupRoute {
        case .none:
            loginView
        case .active:
            switch signupFlow.phase {
            case .accountInput:
                SignUpAccountView(
                    onBack: { signupRoute = .none },
                    onLoginRedirect: { signupRoute = .none },
                )
                .environmentObject(signupFlow)
            case .mfaQr:
                MfaQrView()
                    .environmentObject(signupFlow)
            case .mfaCodeInput:
                MfaCodeView()
                    .environmentObject(signupFlow)
            case .recoveryCodes:
                RecoveryCodesView(onComplete: {
                    // confirmRecoveryCodes が SessionStore.setAuthenticated を呼んでくれる。
                    // SwiftUI 側はサインアップ動線をリセットして LoginView 経路を畳む。
                    signupFlow.reset()
                    signupRoute = .none
                })
                .environmentObject(signupFlow)
            }
        }
    }

    private var loginView: some View {
        let signupTap: () -> Void = {
            signupFlow.reset()
            signupRoute = .active
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

/// 既存ユーザの再ログイン → MFA 未セットアップが判明した場合の resume 画面。
///
/// SignUpFlowState を画面ローカルに作り、`startMfaSetup` で QR を取得してから
/// `phase` 駆動で MfaQr → MfaCode → RecoveryCodes に進む。
private struct MfaSetupResumeView: View {
    @StateObject private var flow = SignUpFlowState()

    var body: some View {
        Group {
            switch flow.phase {
            case .accountInput, .mfaQr:
                MfaQrView()
                    .environmentObject(flow)
            case .mfaCodeInput:
                MfaCodeView()
                    .environmentObject(flow)
            case .recoveryCodes:
                RecoveryCodesView(onComplete: {
                    // confirmRecoveryCodes 内で setAuthenticated まで進む。
                    flow.reset()
                })
                .environmentObject(flow)
            }
        }
        .onAppear {
            // 既に QR があれば再取得しない（再描画でリセットされないように）。
            if flow.mfaSetup == nil {
                flow.startMfaSetup()
            }
        }
    }
}
