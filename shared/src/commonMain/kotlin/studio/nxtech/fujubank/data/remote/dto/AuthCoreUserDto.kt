package studio.nxtech.fujubank.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * AuthCore (`fuju-system-authentication`) の `GET /v1/user/profile` レスポンス。
 *
 * AuthCore 側で持つユーザー情報（id / email / public_id / icon_url / mfa_enabled）を
 * 表す。bank 側の `/users/me` レスポンス（balance / name / public_key 等）と合わせて
 * `UserProfile` ドメインモデルにマージされる。
 */
@Serializable
data class AuthCoreUserResponse(
    val id: String,
    val email: String? = null,
    @SerialName("public_id")
    val publicId: String,
    @SerialName("icon_url")
    val iconUrl: String? = null,
    @SerialName("mfa_enabled")
    val mfaEnabled: Boolean = false,
)
