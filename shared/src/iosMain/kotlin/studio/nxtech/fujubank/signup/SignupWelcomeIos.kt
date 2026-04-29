package studio.nxtech.fujubank.signup

import studio.nxtech.fujubank.session.FlowToken
import studio.nxtech.fujubank.session.observeFlow

/**
 * Swift 側から `SignupCompletionSignal.pending` を観測するための薄い API。
 *
 * `SessionStoreIos` の `observeBootstrapped` と同じスタイルで、初期値も subscribe 直後に
 * 1 回 emit される。観測の実体は [observeFlow] を共用する。
 */
fun observeSignupWelcomePending(
    signal: SignupCompletionSignal,
    onChange: (Boolean) -> Unit,
): FlowToken = observeFlow(signal.pending) { value -> onChange(value) }

/**
 * Swift 側から `SignupWelcomePreferences.signupCompleted` を観測するための薄い API。
 */
fun observeSignupCompleted(
    preferences: SignupWelcomePreferences,
    onChange: (Boolean) -> Unit,
): FlowToken = observeFlow(preferences.signupCompleted) { value -> onChange(value) }
