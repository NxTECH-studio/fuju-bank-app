import Foundation
import Shared

/// サインアップ画面のフェーズ。SwiftUI 側の View 切替に使う。
enum SignUpPhase {
    case accountInput
    case mfaQr
    case mfaCodeInput
    case recoveryCodes
}

/// MFA セットアップで取得した secret + QR + recovery codes の Swift 表現。
/// `qrPngBase64` は AuthCore の data URL から接頭を剥がした生 base64。
struct MfaSetupBundleUi {
    let secret: String
    let qrPngBase64: String
    let recoveryCodes: [String]
}

/// サインアップフロー全体（アカウント作成 → MFA QR → コード入力 → recovery codes）の状態と
/// Kotlin 側 AuthFlowIos ファサード呼び出しを束ねる ObservableObject。
@MainActor
final class SignUpFlowState: ObservableObject {
    @Published var email: String = ""
    @Published var password: String = ""
    @Published var publicId: String = ""
    @Published var totpCode: String = ""
    @Published var phase: SignUpPhase = .accountInput
    @Published var isSubmitting: Bool = false
    @Published var emailError: String? = nil
    @Published var publicIdError: String? = nil
    @Published var formError: String? = nil
    @Published var mfaSetup: MfaSetupBundleUi? = nil
    @Published var mfaCodeError: String? = nil
    @Published var recoverySaved: Bool = false
    @Published private(set) var pendingUserId: String? = nil

    static let totpLength = 6
    static let publicIdMin = 4
    static let publicIdMax = 16

    func reset() {
        email = ""
        password = ""
        publicId = ""
        totpCode = ""
        phase = .accountInput
        isSubmitting = false
        emailError = nil
        publicIdError = nil
        formError = nil
        mfaSetup = nil
        mfaCodeError = nil
        recoverySaved = false
        pendingUserId = nil
    }

    func updatePublicId(_ value: String) {
        publicId = value
        publicIdError = Self.validatePublicId(value)
        formError = nil
    }

    func updateEmail(_ value: String) {
        email = value
        emailError = nil
        formError = nil
    }

    func updatePassword(_ value: String) {
        password = value
        formError = nil
    }

    func updateTotpCode(_ raw: String) {
        // 数字のみ・最大 totpLength 桁。CTA タップで明示送信のため自動 submit はしない。
        let digits = raw.filter { $0.isNumber }
        totpCode = String(digits.prefix(Self.totpLength))
        mfaCodeError = nil
    }

    /// アカウント情報入力 → register → 自動 login → provisionMe → MFA QR を取得し MfaQr 画面へ。
    func submitAccount() {
        guard !isSubmitting else { return }
        guard !email.isEmpty, !password.isEmpty, !publicId.isEmpty else {
            formError = "メールアドレス・パスワード・ユーザー ID をすべて入力してください"
            return
        }
        if let publicIdError = Self.validatePublicId(publicId) {
            self.publicIdError = publicIdError
            return
        }
        isSubmitting = true
        emailError = nil
        publicIdError = nil
        formError = nil
        AuthFlowIosKt.registerAndAutoLogin(
            authRepository: KoinIosKt.authRepository(),
            userRepository: KoinIosKt.userRepository(),
            sessionStore: KoinIosKt.sessionStore(),
            email: email,
            password: password,
            publicId: publicId,
            onResult: { [weak self] outcome in
                Task { @MainActor in
                    self?.handleRegisterOutcome(outcome)
                }
            },
        )
    }

    private func handleRegisterOutcome(_ outcome: RegisterOutcome) {
        switch outcome {
        case is RegisterOutcome.Started:
            // register + login + provisionMe 成功。続けて MFA QR を取得。
            self.startMfaSetup()
        case is RegisterOutcome.LoginAfterRegisterFailed:
            isSubmitting = false
            formError = "アカウントは作成されました。再度ログインしてください"
        case let failure as RegisterOutcome.Failure:
            isSubmitting = false
            let code = failure.error.code
            if code == ApiErrorCode.userAlreadyExists {
                emailError = failure.message
            } else if code == ApiErrorCode.publicIdAlreadyExists ||
                code == ApiErrorCode.publicIdReserved ||
                code == ApiErrorCode.publicIdInvalid {
                publicIdError = failure.message
            } else {
                formError = failure.message
            }
        case let networkFailure as RegisterOutcome.NetworkFailure:
            isSubmitting = false
            formError = networkFailure.message
        default:
            isSubmitting = false
            formError = "予期しないエラーが発生しました"
        }
    }

    /// MFA QR の取得（mfa/register）。register 直後の自動呼び出しと「再生成」リンクから呼ばれる。
    func startMfaSetup() {
        if !isSubmitting {
            isSubmitting = true
            formError = nil
        }
        AuthFlowIosKt.startMfaSetup(
            authRepository: KoinIosKt.authRepository(),
            sessionStore: KoinIosKt.sessionStore(),
            onResult: { [weak self] outcome in
                Task { @MainActor in
                    self?.handleMfaSetupOutcome(outcome)
                }
            },
        )
    }

    private func handleMfaSetupOutcome(_ outcome: MfaSetupOutcome) {
        switch outcome {
        case let ready as MfaSetupOutcome.Ready:
            isSubmitting = false
            mfaSetup = MfaSetupBundleUi(
                secret: ready.secret,
                qrPngBase64: ready.qrPngBase64,
                recoveryCodes: ready.recoveryCodes,
            )
            phase = .mfaQr
            formError = nil
        case let failure as MfaSetupOutcome.Failure:
            isSubmitting = false
            formError = failure.message
        case let networkFailure as MfaSetupOutcome.NetworkFailure:
            isSubmitting = false
            formError = networkFailure.message
        default:
            isSubmitting = false
            formError = "予期しないエラーが発生しました"
        }
    }

    /// MfaQr 画面の「コードを入力する」CTA。MfaCodeInput 画面に遷移。
    func goToMfaCodeInput() {
        totpCode = ""
        mfaCodeError = nil
        phase = .mfaCodeInput
    }

    /// TOTP コード送信 → 有効化 → provisionMe。成功なら RecoveryCodes 画面へ。
    func submitMfaCode() {
        guard !isSubmitting else { return }
        guard totpCode.count == Self.totpLength else {
            mfaCodeError = "6 桁のコードを入力してください"
            return
        }
        isSubmitting = true
        mfaCodeError = nil
        AuthFlowIosKt.enableMfaAndProvision(
            authRepository: KoinIosKt.authRepository(),
            userRepository: KoinIosKt.userRepository(),
            sessionStore: KoinIosKt.sessionStore(),
            code: totpCode,
            onResult: { [weak self] outcome in
                Task { @MainActor in
                    self?.handleMfaEnableOutcome(outcome)
                }
            },
        )
    }

    private func handleMfaEnableOutcome(_ outcome: MfaEnableOutcome) {
        switch outcome {
        case let enabled as MfaEnableOutcome.Enabled:
            isSubmitting = false
            pendingUserId = enabled.userId
            recoverySaved = false
            phase = .recoveryCodes
        case let failure as MfaEnableOutcome.Failure:
            isSubmitting = false
            mfaCodeError = failure.message
        case let networkFailure as MfaEnableOutcome.NetworkFailure:
            isSubmitting = false
            mfaCodeError = networkFailure.message
        default:
            isSubmitting = false
            mfaCodeError = "予期しないエラーが発生しました"
        }
    }

    /// Recovery codes 画面の「次へ」CTA。
    /// SignupCompletionSignal.arm → SessionStore.setAuthenticated の順に呼ぶ契約。
    /// 成功時 true を返す。SwiftUI 側はこれを使って一度だけリセットすれば良い。
    @discardableResult
    func confirmRecoveryCodes() -> Bool {
        guard let userId = pendingUserId, recoverySaved else { return false }
        KoinIosKt.signupCompletionSignal().arm()
        KoinIosKt.sessionStore().setAuthenticated(userId: userId)
        return true
    }

    static func validatePublicId(_ value: String) -> String? {
        if value.isEmpty { return nil }
        if value.count < publicIdMin { return "4 文字以上必要です" }
        if value.count > publicIdMax { return "16 文字以下にしてください" }
        let allowed = CharacterSet.alphanumerics
        if value.unicodeScalars.allSatisfy({ allowed.contains($0) && $0.isASCII }) == false {
            return "半角英数字のみ使用できます"
        }
        return nil
    }
}
