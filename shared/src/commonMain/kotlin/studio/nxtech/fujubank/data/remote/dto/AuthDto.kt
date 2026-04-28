package studio.nxtech.fujubank.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `POST /v1/auth/login` リクエストボディ。
 *
 * `identifier` はメールアドレス、または公開 ID（4-16 文字英数字）のいずれか。
 * AuthCore の README §2.1 / §3 を参照。
 */
@Serializable
data class LoginRequest(
    val identifier: String,
    val password: String,
)

/**
 * 認証成功時の access_token レスポンス。
 *
 * - `/v1/auth/login`（MFA 未要求の場合）
 * - `/v1/auth/mfa/verify`
 * - `/v1/auth/refresh`
 *
 * いずれも同じ形で返る。refresh_token は **HttpOnly cookie** として配送されるため
 * ボディには含まれない。
 */
@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Long,
)

/**
 * MFA が要求されたときの暫定トークンレスポンス。
 *
 * `mfa_required: true` が含まれる点で `TokenResponse` と区別する。
 * `pre_token` は `/v1/auth/mfa/verify` へ Authorization: Bearer として渡す。
 */
@Serializable
data class PreTokenResponse(
    @SerialName("pre_token") val preToken: String,
    @SerialName("mfa_required") val mfaRequired: Boolean,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Long,
)
