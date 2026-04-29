package studio.nxtech.fujubank.signup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 「サインアップ画面発の Authenticated 遷移」を識別するためのプロセス内ワンショットシグナル。
 *
 * 永続化しない理由は、アプリ kill → 再起動で `bootstrap()` 経由の Authenticated になっても
 * Welcome を出したくないため。サインアップ動線が `arm()` → `setAuthenticated()` の順で
 * 呼ぶことを契約とする。
 */
class SignupCompletionSignal {
    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    fun arm() {
        _pending.value = true
    }

    fun consume() {
        _pending.value = false
    }
}
