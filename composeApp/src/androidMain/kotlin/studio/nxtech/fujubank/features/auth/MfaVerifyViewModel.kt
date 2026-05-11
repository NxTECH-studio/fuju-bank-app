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

/**
 * MFA 入力 → 検証成功後オンボーディング 3 画面（成功 / ようこそ / fujupay ロゴ）まで含めた状態。
 *
 * SessionStore は最終 stage（[OnboardingStage.Brand] 完了時）まで `setAuthenticated` を呼ばず
 * MfaPending のままに保つ。これにより AppRoot は MfaVerifyScreen を出し続け、
 * オンボーディング 3 画面が完了してからホームへ遷移できる。
 */
data class MfaVerifyUiState(
    val phase: MfaPhase = MfaPhase.Input,
    val code: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface MfaPhase {
    data object Input : MfaPhase
    data class Onboarding(val stage: OnboardingStage) : MfaPhase
}

enum class OnboardingStage { Success, Welcome, Brand }

private const val MFA_CODE_LENGTH = 6

/**
 * MFA 入力画面の ViewModel。pre_token は SessionStore.MfaPending から流入させる前提で
 * コンストラクタ引数として明示する（画面が再生成されても破棄されない）。
 *
 * - TOTP 6 桁を入力 → `verifyMfa` → `provisionMe` の順に確認。
 * - 検証成功後は SessionStore は MfaPending のまま、UI 内 phase を Onboarding(Success) に切り替える。
 * - [advanceOnboarding] で Success → Welcome → Brand と進み、Brand 完了時のみ `setAuthenticated` を呼ぶ。
 */
class MfaVerifyViewModel(
    private val preToken: String,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(MfaVerifyUiState())
    val state: StateFlow<MfaVerifyUiState> = _state.asStateFlow()

    /** SessionStore.setAuthenticated に渡す ULID + bank PK の対。Brand 完了時まで保持する。 */
    private data class PendingIds(val userId: String, val bankUserId: String)

    // verify+provision 成功後、Brand 完了時に setAuthenticated するまで保持する識別子の対。
    private var pendingIds: PendingIds? = null

    fun onCodeChange(value: String) {
        // 数字のみ・最大 6 桁にサニタイズ。ペースト時の余分な空白・ハイフン等を除去する。
        val sanitized = value.filter { it.isDigit() }.take(MFA_CODE_LENGTH)
        _state.update { it.copy(code = sanitized, errorMessage = null) }
    }

    fun cancel() {
        // pre_token は時限式なので破棄しても問題ない。Input phase 専用。
        sessionStore.clear()
    }

    fun submit() {
        val current = _state.value
        if (current.isSubmitting || current.phase !is MfaPhase.Input) return
        if (current.code.length < MFA_CODE_LENGTH) {
            _state.update { it.copy(errorMessage = "6 桁のコードを入力してください") }
            return
        }
        // submit 開始時に念のため pendingIds を破棄。Input phase で submit を再試行するたびに
        // 直前の verify 結果を引き継がないようにする防御措置。
        pendingIds = null
        _state.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            // Recovery code 入力 UI は仮設で隠しているが、AuthRepository.verifyMfa の
            // recoveryCode パラメータは後続タスク用に残しておく。
            when (val verify = authRepository.verifyMfa(preToken, code = current.code, recoveryCode = null)) {
                is NetworkResult.Success -> provisionAndStartOnboarding()
                is NetworkResult.Failure ->
                    _state.update { it.copy(isSubmitting = false, errorMessage = AuthErrorMessages.forMfa(verify.error)) }
                is NetworkResult.NetworkFailure ->
                    _state.update { it.copy(isSubmitting = false, errorMessage = AuthErrorMessages.forNetworkFailure()) }
            }
        }
    }

    fun advanceOnboarding() {
        val current = _state.value
        val onboarding = current.phase as? MfaPhase.Onboarding ?: return
        when (onboarding.stage) {
            OnboardingStage.Success ->
                _state.update { it.copy(phase = MfaPhase.Onboarding(OnboardingStage.Welcome)) }
            OnboardingStage.Welcome ->
                _state.update { it.copy(phase = MfaPhase.Onboarding(OnboardingStage.Brand)) }
            OnboardingStage.Brand -> {
                val ids = pendingIds ?: return
                pendingIds = null
                sessionStore.setAuthenticated(
                    userId = ids.userId,
                    bankUserId = ids.bankUserId,
                )
            }
        }
    }

    private suspend fun provisionAndStartOnboarding() {
        when (val provision = userRepository.provisionMe()) {
            is NetworkResult.Success -> {
                // SessionStore.userId は AuthCore の ULID (= bank の external_user_id) を源泉とする。
                // bank-backend が `sub` を必ず返すため `User.subject` は非 null。
                pendingIds = PendingIds(
                    userId = provision.value.subject,
                    bankUserId = provision.value.id,
                )
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        code = "",
                        phase = MfaPhase.Onboarding(OnboardingStage.Success),
                    )
                }
            }
            is NetworkResult.Failure ->
                _state.update { it.copy(isSubmitting = false, errorMessage = AuthErrorMessages.forMfa(provision.error)) }
            is NetworkResult.NetworkFailure ->
                _state.update { it.copy(isSubmitting = false, errorMessage = AuthErrorMessages.forNetworkFailure()) }
        }
    }
}
