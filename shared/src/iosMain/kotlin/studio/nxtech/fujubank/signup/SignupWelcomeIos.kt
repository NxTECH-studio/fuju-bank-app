package studio.nxtech.fujubank.signup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import studio.nxtech.fujubank.session.FlowToken

/**
 * Swift 側から `SignupCompletionSignal.pending` を観測するための薄い API。
 *
 * `SessionStoreIos` の `observeBootstrapped` と同じスタイルで、初期値も subscribe 直後に
 * 1 回 emit される。
 */
fun observeSignupWelcomePending(
    signal: SignupCompletionSignal,
    onChange: (Boolean) -> Unit,
): FlowToken = observeBoolean(signal.pending, onChange)

/**
 * Swift 側から `SignupWelcomePreferences.signupCompleted` を観測するための薄い API。
 */
fun observeSignupCompleted(
    preferences: SignupWelcomePreferences,
    onChange: (Boolean) -> Unit,
): FlowToken = observeBoolean(preferences.signupCompleted, onChange)

private fun observeBoolean(flow: StateFlow<Boolean>, onChange: (Boolean) -> Unit): FlowToken {
    val scope = CoroutineScope(Dispatchers.Main)
    val job = scope.launch {
        flow.collect { value -> onChange(value) }
    }
    return FlowToken(scope, job)
}
