package studio.nxtech.fujubank.features.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import studio.nxtech.fujubank.data.remote.ApiErrorCode
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.repository.AuthRepository
import studio.nxtech.fujubank.data.repository.LoginResult
import studio.nxtech.fujubank.data.repository.UserRepository
import studio.nxtech.fujubank.session.AuthErrorMessages
import studio.nxtech.fujubank.session.SessionStore
import studio.nxtech.fujubank.signup.SignupCompletionSignal

/**
 * サインアップフロー全体（アカウント作成 → MFA QR → コード入力 → recovery codes）で
 * 共有する状態と遷移制御。
 */
enum class SignUpPhase {
    AccountInput,
    MfaQr,
    MfaCodeInput,
    RecoveryCodes,
}

data class SignUpFlowState(
    val email: String = "",
    val password: String = "",
    val publicId: String = "",
    val totpCode: String = "",
    val phase: SignUpPhase = SignUpPhase.AccountInput,
    val isSubmitting: Boolean = false,
    // フィールド単位のバリデーションエラー / インラインエラー（赤字）。
    val emailError: String? = null,
    val publicIdError: String? = null,
    // フォーム全体に出すエラー（CTA 下の赤字）。
    val formError: String? = null,
    // mfa/register 成功時に保持する secret + QR + recoveryCodes。
    val mfaSetup: MfaSetupUiBundle? = null,
    // mfa/enable 失敗時のメッセージ。
    val mfaCodeError: String? = null,
    // recovery codes 画面の「保存しました」チェック。
    val recoverySaved: Boolean = false,
    // 完了済みのユーザー識別子（recovery codes 画面で setAuthenticated に使う）。
    // - [pendingUserId]: AuthCore ULID (= external_user_id)。SessionStore.userId 源泉。
    // - [pendingBankUserId]: bank PK 文字列。SessionStore.bankUserId 源泉（取引履歴 API 等）。
    val pendingUserId: String? = null,
    val pendingBankUserId: String? = null,
)

/**
 * ViewModel が UI に渡す MFA セットアップバンドル。
 *
 * `qrPngBase64` は AuthCore の data URL から接頭を剥がした生 base64。
 * decode → ImageBitmap 化は UI 層の責務（`expect fun decodeBase64Png` を使う）。
 */
data class MfaSetupUiBundle(
    val secret: String,
    val qrPngBase64: String,
    val recoveryCodes: List<String>,
)

class SignUpFlowViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val sessionStore: SessionStore,
    private val signupCompletionSignal: SignupCompletionSignal,
) : ViewModel() {

    private val _state = MutableStateFlow(SignUpFlowState())
    val state: StateFlow<SignUpFlowState> = _state.asStateFlow()

    fun onEmailChange(value: String) {
        _state.update {
            it.copy(
                email = value,
                emailError = null,
                formError = null,
            )
        }
    }

    fun onPasswordChange(value: String) {
        _state.update { it.copy(password = value, formError = null) }
    }

    fun onPublicIdChange(value: String) {
        _state.update {
            it.copy(
                publicId = value,
                publicIdError = validatePublicId(value),
                formError = null,
            )
        }
    }

    fun onTotpCodeChange(value: String) {
        // 数字のみ・最大 6 桁。CTA タップで明示送信するので、6 桁完了時の自動 submit は行わない。
        val sanitized = value.filter { it.isDigit() }.take(TOTP_LENGTH)
        _state.update { it.copy(totpCode = sanitized, mfaCodeError = null) }
    }

    fun onRecoverySavedChange(checked: Boolean) {
        _state.update { it.copy(recoverySaved = checked) }
    }

    /**
     * アカウント情報入力 → register → 自動 login → provisionMe → MFA QR を取得して MfaQr 画面へ。
     */
    fun submitAccount() {
        val current = _state.value
        if (current.isSubmitting) return
        if (current.email.isBlank() || current.password.isBlank() || current.publicId.isBlank()) {
            _state.update { it.copy(formError = "メールアドレス・パスワード・ユーザー ID をすべて入力してください") }
            return
        }
        val publicIdError = validatePublicId(current.publicId)
        if (publicIdError != null) {
            _state.update { it.copy(publicIdError = publicIdError) }
            return
        }
        _state.update {
            it.copy(
                isSubmitting = true,
                emailError = null,
                publicIdError = null,
                formError = null,
            )
        }
        viewModelScope.launch {
            when (val register = authRepository.register(current.email, current.password, current.publicId)) {
                is NetworkResult.Success -> autoLoginAndStartMfaSetup(current.email, current.password)
                is NetworkResult.Failure -> {
                    val message = AuthErrorMessages.forRegister(register.error)
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            // フィールド対応のエラーは欄下インラインに、その他は全体エラーに振り分ける。
                            emailError = if (register.error.code == ApiErrorCode.USER_ALREADY_EXISTS) message else null,
                            publicIdError = when (register.error.code) {
                                ApiErrorCode.PUBLIC_ID_ALREADY_EXISTS,
                                ApiErrorCode.PUBLIC_ID_RESERVED,
                                ApiErrorCode.PUBLIC_ID_INVALID,
                                -> message
                                else -> null
                            },
                            formError = when (register.error.code) {
                                ApiErrorCode.USER_ALREADY_EXISTS,
                                ApiErrorCode.PUBLIC_ID_ALREADY_EXISTS,
                                ApiErrorCode.PUBLIC_ID_RESERVED,
                                ApiErrorCode.PUBLIC_ID_INVALID,
                                -> null
                                else -> message
                            },
                        )
                    }
                }
                is NetworkResult.NetworkFailure -> _state.update {
                    it.copy(isSubmitting = false, formError = AuthErrorMessages.forNetworkFailure())
                }
            }
        }
    }

    private suspend fun autoLoginAndStartMfaSetup(email: String, password: String) {
        when (val login = authRepository.login(email, password)) {
            is NetworkResult.Success -> when (login.value) {
                is LoginResult.Authenticated -> {
                    when (userRepository.provisionMe()) {
                        is NetworkResult.Success -> startMfaSetup()
                        // provisionMe 失敗は致命傷ではないが、続けて mfa/register が必要なため
                        // bank user 行ができていないと後段で困る。ここでログイン画面に戻す。
                        is NetworkResult.Failure, is NetworkResult.NetworkFailure -> _state.update {
                            it.copy(
                                isSubmitting = false,
                                formError = "アカウントは作成されました。再度ログインしてください",
                            )
                        }
                    }
                }
                // register 直後なので NeedsMfa は通常返らない想定。来たら救済としてログイン画面へ戻す。
                is LoginResult.NeedsMfa -> _state.update {
                    it.copy(
                        isSubmitting = false,
                        formError = "アカウントは作成されました。再度ログインしてください",
                    )
                }
            }
            is NetworkResult.Failure, is NetworkResult.NetworkFailure -> _state.update {
                it.copy(
                    isSubmitting = false,
                    formError = "アカウントは作成されました。再度ログインしてください",
                )
            }
        }
    }

    private suspend fun startMfaSetup() {
        when (val setup = authRepository.setupMfa()) {
            is NetworkResult.Success -> _state.update {
                it.copy(
                    isSubmitting = false,
                    phase = SignUpPhase.MfaQr,
                    mfaSetup = MfaSetupUiBundle(
                        secret = setup.value.secret,
                        qrPngBase64 = setup.value.qrPngBase64,
                        recoveryCodes = setup.value.recoveryCodes,
                    ),
                    formError = null,
                )
            }
            is NetworkResult.Failure -> _state.update {
                it.copy(
                    isSubmitting = false,
                    formError = AuthErrorMessages.forMfaSetup(setup.error),
                )
            }
            is NetworkResult.NetworkFailure -> _state.update {
                it.copy(isSubmitting = false, formError = AuthErrorMessages.forNetworkFailure())
            }
        }
    }

    /**
     * MFA QR 画面の「QR を再生成」リンク。`mfa/register` を再実行して新 secret に上書き。
     */
    fun regenerateMfaQr() {
        if (_state.value.isSubmitting) return
        _state.update { it.copy(isSubmitting = true, formError = null) }
        viewModelScope.launch { startMfaSetup() }
    }

    /** MfaQr → MfaCodeInput 画面遷移。 */
    fun goToMfaCodeInput() {
        _state.update { it.copy(phase = SignUpPhase.MfaCodeInput, totpCode = "", mfaCodeError = null) }
    }

    /**
     * TOTP コード送信 → 有効化 → provisionMe。成功なら RecoveryCodes 画面へ進む。
     */
    fun submitMfaCode() {
        val current = _state.value
        if (current.isSubmitting) return
        if (current.totpCode.length != TOTP_LENGTH) {
            _state.update { it.copy(mfaCodeError = "6 桁のコードを入力してください") }
            return
        }
        _state.update { it.copy(isSubmitting = true, mfaCodeError = null) }
        viewModelScope.launch {
            when (val enable = authRepository.enableMfa(current.totpCode)) {
                is NetworkResult.Success -> provisionAndMoveToRecoveryCodes()
                is NetworkResult.Failure -> {
                    if (enable.error.code == ApiErrorCode.MFA_ALREADY_ENABLED) {
                        provisionAndMoveToRecoveryCodes()
                    } else {
                        _state.update {
                            it.copy(
                                isSubmitting = false,
                                mfaCodeError = AuthErrorMessages.forMfaEnable(enable.error),
                            )
                        }
                    }
                }
                is NetworkResult.NetworkFailure -> _state.update {
                    it.copy(isSubmitting = false, mfaCodeError = AuthErrorMessages.forNetworkFailure())
                }
            }
        }
    }

    private suspend fun provisionAndMoveToRecoveryCodes() {
        when (val provision = userRepository.provisionMe()) {
            is NetworkResult.Success -> {
                // SessionStore.userId は AuthCore の ULID (= bank の external_user_id) を源泉とする。
                // `/users/me` レスポンスが `sub` を欠落している場合は送金 API などに渡せる識別子が
                // 取れていないため、recovery codes 画面に進めずエラー表示にする。
                val subject = provision.value.subject
                if (subject == null) {
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            mfaCodeError = "セッション情報を取得できませんでした。もう一度ログインしてください",
                        )
                    }
                    return
                }
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        phase = SignUpPhase.RecoveryCodes,
                        pendingUserId = subject,
                        pendingBankUserId = provision.value.id,
                        recoverySaved = false,
                    )
                }
            }
            is NetworkResult.Failure -> _state.update {
                it.copy(
                    isSubmitting = false,
                    mfaCodeError = AuthErrorMessages.forMfaEnable(provision.error),
                )
            }
            is NetworkResult.NetworkFailure -> _state.update {
                it.copy(isSubmitting = false, mfaCodeError = AuthErrorMessages.forNetworkFailure())
            }
        }
    }

    /**
     * Recovery codes 画面の「次へ」CTA。
     * SignupCompletionSignal を arm してから setAuthenticated を呼ぶ契約。
     */
    fun confirmRecoveryCodes(): Boolean {
        val userId = _state.value.pendingUserId ?: return false
        val bankUserId = _state.value.pendingBankUserId ?: return false
        if (!_state.value.recoverySaved) return false
        signupCompletionSignal.arm()
        sessionStore.setAuthenticated(userId = userId, bankUserId = bankUserId)
        return true
    }

    fun reset() {
        _state.value = SignUpFlowState()
    }

    private fun validatePublicId(value: String): String? = when {
        value.isEmpty() -> null
        value.length < PUBLIC_ID_MIN -> "4 文字以上必要です"
        value.length > PUBLIC_ID_MAX -> "16 文字以下にしてください"
        !value.all { it.isLetterOrDigit() && it.code < 0x80 } -> "半角英数字のみ使用できます"
        else -> null
    }

    companion object {
        const val TOTP_LENGTH = 6
        const val PUBLIC_ID_MIN = 4
        const val PUBLIC_ID_MAX = 16
    }
}
