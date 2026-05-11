package studio.nxtech.fujubank.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.remote.api.AuthCoreUserApi
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
 *   AuthCore `/v1/user/profile` で `mfa_enabled` を確認 → SessionStore に Authenticated か
 *   MfaSetupRequired を設定する。
 * - MFA 必須なら SessionStore.MfaPending に切り替え、MfaVerifyScreen に画面遷移する。
 * - mfa_enabled = false の既存ユーザは SessionStore.MfaSetupRequired に切り替え、
 *   サインアップ動線と同じ MFA セットアップ画面群に誘導する（client-bank-21 の resume 経路）。
 */
class LoginViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val sessionStore: SessionStore,
    private val authCoreUserApi: AuthCoreUserApi = KoinPlatform.getKoin().get(),
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
                // mfa_enabled = false なら MFA セットアップを完了させてから Authenticated に倒す。
                // getProfile が落ちた場合は安全側に倒し、既存挙動の Authenticated に進める
                // （MFA セットアップ要求は次回ログイン時に再判定すれば良い）。
                val needsSetup = when (val profile = authCoreUserApi.getProfile()) {
                    is NetworkResult.Success -> !profile.value.mfaEnabled
                    is NetworkResult.Failure, is NetworkResult.NetworkFailure -> false
                }
                if (needsSetup) {
                    sessionStore.setMfaSetupRequired()
                    _state.update { LoginUiState() }
                } else {
                    // SessionStore.userId は AuthCore ULID (= external_user_id) を源泉とする。
                    // bank-backend が `sub` を必ず返すため `User.subject` は非 null。
                    sessionStore.setAuthenticated(
                        userId = provision.value.subject,
                        bankUserId = provision.value.id,
                    )
                    _state.update { LoginUiState() }
                }
            }
            is NetworkResult.Failure ->
                _state.update { it.copy(isSubmitting = false, errorMessage = AuthErrorMessages.forLogin(provision.error)) }
            is NetworkResult.NetworkFailure ->
                _state.update { it.copy(isSubmitting = false, errorMessage = AuthErrorMessages.forNetworkFailure()) }
        }
    }
}
