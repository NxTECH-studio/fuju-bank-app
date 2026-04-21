package studio.nxtech.fujubank.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateUserRequest(
    @SerialName("sub")
    val subject: String,
)

@Serializable
data class UserResponse(
    val id: String,
    @SerialName("sub")
    val subject: String,
    // bigint: クライアント側は小数計算に関与しないため Long で受ける。
    @SerialName("balance_fuju")
    val balanceFuju: Long,
    // ISO8601 文字列。Instant への変換は Repository 層で行う。
    @SerialName("created_at")
    val createdAt: String,
)
