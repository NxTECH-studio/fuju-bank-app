import Foundation
import Shared

/// ログイン画面の状態管理。識別子（メール / 公開ID）+ パスワードで AuthCore にログインを試み、
/// 成否と MFA 必須の分岐を SessionStore 経由で AppRoot に伝える。
@MainActor
final class LoginViewModel: ObservableObject {
    @Published var identifier: String = ""
    @Published var password: String = ""
    @Published private(set) var isSubmitting: Bool = false
    @Published var errorMessage: String?

    func submit() {
        guard !isSubmitting else { return }
        guard !identifier.trimmingCharacters(in: .whitespaces).isEmpty,
              !password.isEmpty else {
            errorMessage = "メールアドレス/公開ID とパスワードを入力してください"
            return
        }
        isSubmitting = true
        errorMessage = nil

        // AuthFlowIos.kt の loginAndProvision はメインディスパッチャ上で動き、結果を
        // クロージャに返す。SwiftUI の State 更新は @MainActor で行う必要があるため Task でラップする。
        AuthFlowIosKt.loginAndProvision(
            authRepository: KoinIosKt.authRepository(),
            userRepository: KoinIosKt.userRepository(),
            sessionStore: KoinIosKt.sessionStore(),
            identifier: identifier,
            password: password
        ) { [weak self] outcome in
            Task { @MainActor in
                guard let self else { return }
                self.isSubmitting = false
                switch outcome {
                case is AuthFlowOutcome.Authenticated, is AuthFlowOutcome.MfaRequired:
                    // SessionStore 側が状態を切り替えてくれるので画面は AppRoot 経由で遷移する。
                    self.password = ""
                    self.errorMessage = nil
                case let failure as AuthFlowOutcome.Failure:
                    self.errorMessage = failure.message
                case let netFailure as AuthFlowOutcome.NetworkFailure:
                    self.errorMessage = netFailure.message
                default:
                    self.errorMessage = "未知のエラーが発生しました"
                }
            }
        }
    }
}
