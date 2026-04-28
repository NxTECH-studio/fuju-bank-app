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
import studio.nxtech.fujubank.data.repository.LoginResult
import studio.nxtech.fujubank.data.repository.UserRepository
import studio.nxtech.fujubank.session.AuthErrorMessages
import studio.nxtech.fujubank.session.SessionStore

data class LoginUiState(
    val identifier: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * ログイン画面の状態とアクションをまとめる ViewModel。
 *
 * - 入力欄の更新は [onIdentifierChange] / [onPasswordChange] で受ける（純粋）。
 * - [submit] を押すと AuthRepository.login → 成功なら bank `POST /users/me` で provision →
 *   SessionStore に Authenticated を伝搬する。
 * - MFA 必須なら SessionStore.MfaPending に切り替え、MfaVerifyScreen に画面遷移する。
 */
class LoginViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onIdentifierChange(value: String) {
        _state.update { it.copy(identifier = value, errorMessage = null) }
    }

    fun onPasswordChange(value: String) {
        _state.update { it.copy(password = value, errorMessage = null) }
    }

    fun submit() {
        val current = _state.value
        if (current.isSubmitting) return
        if (current.identifier.isBlank() || current.password.isBlank()) {
            _state.update { it.copy(errorMessage = "メールアドレス/公開ID とパスワードを入力してください") }
            return
        }
        _state.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = authRepository.login(current.identifier, current.password)) {
                is NetworkResult.Success -> handleLoginSuccess(result.value)
                is NetworkResult.Failure ->
                    _state.update { it.copy(isSubmitting = false, errorMessage = AuthErrorMessages.forLogin(result.error)) }
                is NetworkResult.NetworkFailure ->
                    _state.update { it.copy(isSubmitting = false, errorMessage = AuthErrorMessages.forNetworkFailure()) }
            }
        }
    }

    private suspend fun handleLoginSuccess(value: LoginResult) {
        when (value) {
            is LoginResult.NeedsMfa -> {
                sessionStore.setMfaPending(value.preToken)
                _state.update { it.copy(isSubmitting = false, password = "") }
            }
            is LoginResult.Authenticated -> provisionAndAuthenticate()
        }
    }

    private suspend fun provisionAndAuthenticate() {
        when (val provision = userRepository.provisionMe()) {
            is NetworkResult.Success -> {
                sessionStore.setAuthenticated(provision.value.id)
                _state.update { LoginUiState() }
            }
            is NetworkResult.Failure ->
                _state.update { it.copy(isSubmitting = false, errorMessage = AuthErrorMessages.forLogin(provision.error)) }
            is NetworkResult.NetworkFailure ->
                _state.update { it.copy(isSubmitting = false, errorMessage = AuthErrorMessages.forNetworkFailure()) }
        }
    }
}
