package studio.nxtech.fujubank.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `POST /v1/auth/mfa/verify` リクエストボディ。
 *
 * `code`（TOTP）または `recovery_code`（リカバリコード）のいずれかを指定する。
 * 両方 null だとサーバが `VALIDATION_FAILED` を返す。
 *
 * `explicitNulls = false` の Json 設定により、null フィールドは送信されない。
 */
@Serializable
data class MfaVerifyRequest(
    val code: String? = null,
    @SerialName("recovery_code") val recoveryCode: String? = null,
)
