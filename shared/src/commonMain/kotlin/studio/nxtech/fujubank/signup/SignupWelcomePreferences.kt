package studio.nxtech.fujubank.signup

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Welcome 画面の表示済みフラグを永続化するプリファレンス。
 *
 * `signup_completed = true` 以降は同一インストール内で Welcome を二度と出さない。
 * 多端末同期は対象外（端末ごとに 1 度ずつ表示される仕様）。
 */
class SignupWelcomePreferences(private val settings: Settings) {
    private val _signupCompleted = MutableStateFlow(settings.getBoolean(KEY, false))
    val signupCompleted: StateFlow<Boolean> = _signupCompleted.asStateFlow()

    fun markCompleted() {
        settings.putBoolean(KEY, true)
        _signupCompleted.value = true
    }

    fun resetForDebug() {
        settings.remove(KEY)
        _signupCompleted.value = false
    }

    private companion object {
        const val KEY = "signup_completed"
    }
}
