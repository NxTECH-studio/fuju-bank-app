import Foundation
import Shared

/// Welcome 画面の表示制御を担う ViewModel。
///
/// `signupCompletionSignal.pending`（プロセス内ワンショット）と
/// `signupWelcomePreferences.signupCompleted`（永続フラグ）を観測し、
/// `pending && !alreadyShown` の AND を `shouldShowWelcome` として公開する。
///
/// AppRoot は `Authenticated` 分岐内でこの値を見て WelcomeView を 1 段挟む。
/// `markShown()` が完了処理：永続フラグを立て、ワンショットを消費する。
@MainActor
final class WelcomeGateViewModel: ObservableObject {
    @Published private(set) var pending: Bool = false
    @Published private(set) var alreadyShown: Bool = false

    var shouldShowWelcome: Bool { pending && !alreadyShown }

    private let signal: SignupCompletionSignal
    private let preferences: SignupWelcomePreferences
    private var pendingToken: FlowToken?
    private var shownToken: FlowToken?

    init() {
        let signal = KoinIosKt.signupCompletionSignal()
        let preferences = KoinIosKt.signupWelcomePreferences()
        self.signal = signal
        self.preferences = preferences
        self.pendingToken = SignupWelcomeIosKt.observeSignupWelcomePending(signal: signal) { [weak self] value in
            Task { @MainActor in
                self?.pending = value.boolValue
            }
        }
        self.shownToken = SignupWelcomeIosKt.observeSignupCompleted(preferences: preferences) { [weak self] value in
            Task { @MainActor in
                self?.alreadyShown = value.boolValue
            }
        }
    }

    deinit {
        pendingToken?.close()
        shownToken?.close()
    }

    /// Welcome 表示が完了したら呼ぶ。永続フラグを立て、ワンショットを落とす。
    func markShown() {
        preferences.markCompleted()
        signal.consume()
    }
}
