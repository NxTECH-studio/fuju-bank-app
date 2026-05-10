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

/**
 * `POST /v1/auth/register` リクエストボディ。
 *
 * AuthCore は email / password / public_id すべて必須。
 * - email: RFC 5322 準拠
 * - password: 8 文字以上（zxcvbn スコア 2 以上を別途要求するが、クライアントでは前段検証なし）
 * - public_id: 半角英数字 4-16 文字（記号不可、数字のみも不可）
 *
 * 成功時はトークン非発行（[RegisterResponse] のみ）。続けて `/v1/auth/login` を叩く必要がある。
 */
@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    @SerialName("public_id") val publicId: String,
)

/**
 * `POST /v1/auth/register` 成功レスポンス（201 Created）。
 *
 * 認証トークンは含まれない。MFA セットアップ前のため `mfa_enabled = false` 固定。
 */
@Serializable
data class RegisterResponse(
    val id: String,
    val email: String,
    @SerialName("public_id") val publicId: String,
    @SerialName("created_at") val createdAt: String,
)
