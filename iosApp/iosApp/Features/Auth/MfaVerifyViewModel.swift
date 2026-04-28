import Foundation
import Shared

/// MFA 入力画面の状態管理。pre_token を SessionStore.MfaPending から受け取って保持する。
@MainActor
final class MfaVerifyViewModel: ObservableObject {
    enum InputMode {
        case totp
        case recovery
    }

    @Published var mode: InputMode = .totp
    @Published var code: String = ""
    @Published private(set) var isSubmitting: Bool = false
    @Published var errorMessage: String?

    private let preToken: String

    init(preToken: String) {
        self.preToken = preToken
    }

    func switchMode(to newMode: InputMode) {
        guard !isSubmitting else { return }
        mode = newMode
        code = ""
        errorMessage = nil
    }

    func cancel() {
        // 一段階目に戻す。AuthCore の pre_token は時限式なので破棄しても問題ない。
        KoinIosKt.sessionStore().clear()
    }

    func submit() {
        guard !isSubmitting else { return }
        let trimmed = code.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else {
            errorMessage = "コードを入力してください"
            return
        }
        isSubmitting = true
        errorMessage = nil

        let totp: String? = mode == .totp ? trimmed : nil
        let recovery: String? = mode == .recovery ? trimmed : nil

        AuthFlowIosKt.verifyMfaAndProvision(
            authRepository: KoinIosKt.authRepository(),
            userRepository: KoinIosKt.userRepository(),
            sessionStore: KoinIosKt.sessionStore(),
            preToken: preToken,
            code: totp,
            recoveryCode: recovery
        ) { [weak self] outcome in
            Task { @MainActor in
                guard let self else { return }
                self.isSubmitting = false
                switch outcome {
                case is AuthFlowOutcome.Authenticated:
                    // SessionStore 側で Authenticated に切り替わるので AppRoot が遷移する。
                    self.code = ""
                    self.errorMessage = nil
                case let failure as AuthFlowOutcome.Failure:
                    self.errorMessage = failure.message
                case let netFailure as AuthFlowOutcome.NetworkFailure:
                    self.errorMessage = netFailure.message
                default:
                    // MfaRequired はここでは到達しない（MFA 側のエンドポイントは MFA を要求しない）。
                    self.errorMessage = "未知のエラーが発生しました"
                }
            }
        }
    }
}
