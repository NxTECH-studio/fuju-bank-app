package studio.nxtech.fujubank.session

import studio.nxtech.fujubank.data.remote.ApiError
import studio.nxtech.fujubank.data.remote.ApiErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `AuthErrorMessages` のエラーコード → 日本語文言マッピング検証。
 * iOS / Android で文言が揃うことを保証する純粋関数なので commonTest で確定的に書ける。
 */
class AuthErrorMessagesTest {

    private fun apiError(code: ApiErrorCode, status: Int = 401): ApiError =
        ApiError(code = code, message = "server-side message", httpStatus = status)

    // ---------- forLogin ----------

    @Test
    fun login_invalid_credentials_returns_user_facing_message() {
        val msg = AuthErrorMessages.forLogin(apiError(ApiErrorCode.INVALID_CREDENTIALS))
        assertTrue(msg.contains("間違っています"), "msg=$msg")
    }

    @Test
    fun login_account_locked_message_indicates_retry_later() {
        val msg = AuthErrorMessages.forLogin(apiError(ApiErrorCode.ACCOUNT_LOCKED, status = 429))
        assertTrue(msg.contains("試行が多すぎます") || msg.contains("待ってから"), "msg=$msg")
    }

    @Test
    fun login_rate_limit_returns_dedicated_message() {
        val msg = AuthErrorMessages.forLogin(apiError(ApiErrorCode.RATE_LIMIT_EXCEEDED, status = 429))
        assertTrue(msg.contains("リクエストが多すぎます"), "msg=$msg")
    }

    @Test
    fun login_validation_failed_indicates_input_check() {
        val msg = AuthErrorMessages.forLogin(apiError(ApiErrorCode.VALIDATION_FAILED, status = 400))
        assertTrue(msg.contains("入力内容"), "msg=$msg")
    }

    @Test
    fun login_unknown_code_falls_back_to_generic() {
        // 未知 / マップ外コードは generic 文言にフォールバック。
        val genericForUnknown = AuthErrorMessages.forLogin(apiError(ApiErrorCode.UNKNOWN, status = 500))
        val genericForMfa = AuthErrorMessages.forLogin(apiError(ApiErrorCode.MFA_REQUIRED))
        assertEquals(genericForUnknown, genericForMfa, "未マップは同一の generic にフォールバックすべき")
        assertTrue(genericForUnknown.contains("ログイン"), "msg=$genericForUnknown")
    }

    // ---------- forMfa ----------

    @Test
    fun mfa_totp_invalid_returns_user_facing_message() {
        val msg = AuthErrorMessages.forMfa(apiError(ApiErrorCode.TOTP_CODE_INVALID))
        assertTrue(msg.contains("認証コード"), "msg=$msg")
    }

    @Test
    fun mfa_recovery_code_invalid_returns_distinct_message() {
        val totp = AuthErrorMessages.forMfa(apiError(ApiErrorCode.TOTP_CODE_INVALID))
        val recovery = AuthErrorMessages.forMfa(apiError(ApiErrorCode.RECOVERY_CODE_INVALID))
        assertTrue(totp != recovery, "TOTP と RecoveryCode は別文言であるべき")
        assertTrue(recovery.contains("リカバリコード"), "msg=$recovery")
    }

    @Test
    fun mfa_token_expired_message_indicates_session_expiry() {
        val msg = AuthErrorMessages.forMfa(apiError(ApiErrorCode.TOKEN_EXPIRED))
        assertTrue(
            msg.contains("有効期限") || msg.contains("やり直して"),
            "msg=$msg",
        )
    }

    @Test
    fun mfa_unknown_code_falls_back_to_generic() {
        val msg = AuthErrorMessages.forMfa(apiError(ApiErrorCode.UNKNOWN, status = 500))
        assertTrue(msg.contains("認証に失敗"), "msg=$msg")
    }

    // ---------- forNetworkFailure ----------

    @Test
    fun network_failure_message_is_stable() {
        val msg = AuthErrorMessages.forNetworkFailure()
        assertTrue(msg.contains("ネットワーク"), "msg=$msg")
        assertTrue(msg.contains("通信状況"), "msg=$msg")
    }
}
