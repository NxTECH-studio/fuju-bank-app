package studio.nxtech.fujubank.features.signup

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SignUpFlowState(
    val email: String = "",
    val password: String = "",
    val otp: String = "",
)

/**
 * サインアップフロー全体（アカウント作成 → OTP → 成功）で共有する入力状態。
 *
 * 戻る/進むで入力値が消えないよう、3 画面が同じ ViewModel を参照する想定。
 * API 連携・バリデーションは本タスクでスコープ外。最低限「空でない」「6 桁の数字」のみで判定する。
 */
class SignUpFlowViewModel : ViewModel() {

    private val _state = MutableStateFlow(SignUpFlowState())
    val state: StateFlow<SignUpFlowState> = _state.asStateFlow()

    fun onEmailChange(value: String) {
        _state.update { it.copy(email = value) }
    }

    fun onPasswordChange(value: String) {
        _state.update { it.copy(password = value) }
    }

    fun onOtpChange(value: String) {
        // OTP は数字のみ・最大 6 桁。
        val sanitized = value.filter { it.isDigit() }.take(OTP_LENGTH)
        _state.update { it.copy(otp = sanitized) }
    }

    fun reset() {
        _state.value = SignUpFlowState()
    }

    companion object {
        const val OTP_LENGTH = 6
    }
}
