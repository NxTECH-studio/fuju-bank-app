import Foundation
import Shared

/// MFA 入力 → 検証成功後オンボーディング 3 画面（成功 / ようこそ / fujupay ロゴ）まで含めた状態管理。
///
/// SessionStore は最終 stage（`OnboardingStage.brand` 完了時）まで `setAuthenticated` を呼ばず
/// MfaPending のまま保つことで、AppRoot は MfaVerifyView を表示し続ける。
@MainActor
final class MfaVerifyViewModel: ObservableObject {
    enum OnboardingStage {
        case success
        case welcome
        case brand
    }

    enum Phase: Equatable {
        case input
        case onboarding(OnboardingStage)
    }

    @Published private(set) var phase: Phase = .input
    @Published var code: String = "" {
        didSet {
            // 数字のみ・最大 6 桁にサニタイズ。SwiftUI の didSet ループを避けるため
            // 変化があるときだけ書き戻す。
            let sanitized = Self.sanitize(code)
            if sanitized != code {
                code = sanitized
                return
            }
            if errorMessage != nil {
                errorMessage = nil
            }
        }
    }
    @Published private(set) var isSubmitting: Bool = false
    @Published var errorMessage: String?

    private let preToken: String
    /// SessionStore.setAuthenticated に渡す ULID + bank PK の対。Brand 完了時まで保持する。
    private struct PendingIds { let userId: String; let bankUserId: String }
    private var pendingIds: PendingIds?

    init(preToken: String) {
        self.preToken = preToken
    }

    static let codeLength = 6

    func cancel() {
        // 一段階目に戻す。pre_token は時限式なので破棄しても問題ない。Input phase 専用。
        KoinIosKt.sessionStore().clear()
    }

    func submit() {
        guard !isSubmitting, phase == .input else { return }
        guard code.count >= Self.codeLength else {
            errorMessage = "6 桁のコードを入力してください"
            return
        }
        // submit 開始時に念のため pendingIds を破棄。Input phase で submit を再試行するたびに
        // 直前の verify 結果を引き継がないようにする防御措置。
        pendingIds = nil
        isSubmitting = true
        errorMessage = nil

        // Recovery code 入力 UI は仮設で隠しているが、recoveryCode パラメータは
        // 後続タスク用にコードレベルで残しておく（常に nil）。
        AuthFlowIosKt.verifyMfaWithoutAuthenticating(
            authRepository: KoinIosKt.authRepository(),
            userRepository: KoinIosKt.userRepository(),
            sessionStore: KoinIosKt.sessionStore(),
            preToken: preToken,
            code: code,
            recoveryCode: nil
        ) { [weak self] outcome in
            Task { @MainActor in
                guard let self else { return }
                self.isSubmitting = false
                switch outcome {
                case let verified as MfaVerifyOutcome.Verified:
                    self.pendingIds = PendingIds(
                        userId: verified.userId,
                        bankUserId: verified.bankUserId,
                    )
                    self.code = ""
                    self.errorMessage = nil
                    self.phase = .onboarding(.success)
                case let failure as MfaVerifyOutcome.Failure:
                    self.errorMessage = failure.message
                case let netFailure as MfaVerifyOutcome.NetworkFailure:
                    self.errorMessage = netFailure.message
                default:
                    self.errorMessage = "未知のエラーが発生しました"
                }
            }
        }
    }

    func advanceOnboarding() {
        guard case let .onboarding(stage) = phase else { return }
        switch stage {
        case .success:
            phase = .onboarding(.welcome)
        case .welcome:
            phase = .onboarding(.brand)
        case .brand:
            guard let ids = pendingIds else { return }
            pendingIds = nil
            KoinIosKt.sessionStore().setAuthenticated(
                userId: ids.userId,
                bankUserId: ids.bankUserId,
            )
        }
    }

    private static func sanitize(_ raw: String) -> String {
        let digits = raw.filter { $0.isASCII && $0.isNumber }
        return String(digits.prefix(codeLength))
    }
}
