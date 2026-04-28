package studio.nxtech.fujubank.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.repository.AuthRepository
import studio.nxtech.fujubank.data.repository.UserRepository
import studio.nxtech.fujubank.session.AuthErrorMessages
import studio.nxtech.fujubank.session.SessionStore

enum class MfaInputMode { TOTP, RECOVERY }

data class MfaVerifyUiState(
    val mode: MfaInputMode = MfaInputMode.TOTP,
    val code: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * MFA 入力画面の ViewModel。pre_token は SessionStore.MfaPending から流入させる前提で
 * コンストラクタ引数として明示する（画面が再生成されても破棄されない）。
 *
 * - TOTP / Recovery code をタブで切り替え。
 * - 確認ボタンで `verifyMfa` → 成功なら `provisionMe` → SessionStore.Authenticated。
 */
class MfaVerifyViewModel(
    private val preToken: String,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(MfaVerifyUiState())
    val state: StateFlow<MfaVerifyUiState> = _state.asStateFlow()

    fun onModeChange(mode: MfaInputMode) {
        _state.update { it.copy(mode = mode, code = "", errorMessage = null) }
    }

    fun onCodeChange(value: String) {
        _state.update { it.copy(code = value, errorMessage = null) }
    }

    fun cancel() {
        sessionStore.clear()
    }

    fun submit() {
        val current = _state.value
        if (current.isSubmitting) return
        if (current.code.isBlank()) {
            _state.update { it.copy(errorMessage = "コードを入力してください") }
            return
        }
        _state.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            val (totp, recovery) = when (current.mode) {
                MfaInputMode.TOTP -> current.code to null
                MfaInputMode.RECOVERY -> null to current.code
            }
            when (val verify = authRepository.verifyMfa(preToken, code = totp, recoveryCode = recovery)) {
                is NetworkResult.Success -> provisionAndAuthenticate()
                is NetworkResult.Failure ->
                    _state.update { it.copy(isSubmitting = false, errorMessage = AuthErrorMessages.forMfa(verify.error)) }
                is NetworkResult.NetworkFailure ->
                    _state.update { it.copy(isSubmitting = false, errorMessage = AuthErrorMessages.forNetworkFailure()) }
            }
        }
    }

    private suspend fun provisionAndAuthenticate() {
        when (val provision = userRepository.provisionMe()) {
            is NetworkResult.Success -> {
                sessionStore.setAuthenticated(provision.value.id)
                _state.update { MfaVerifyUiState() }
            }
            is NetworkResult.Failure ->
                _state.update { it.copy(isSubmitting = false, errorMessage = AuthErrorMessages.forMfa(provision.error)) }
            is NetworkResult.NetworkFailure ->
                _state.update { it.copy(isSubmitting = false, errorMessage = AuthErrorMessages.forNetworkFailure()) }
        }
    }
}
