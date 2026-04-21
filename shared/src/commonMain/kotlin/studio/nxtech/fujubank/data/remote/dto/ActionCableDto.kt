package studio.nxtech.fujubank.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ActionCable のフレームは welcome / confirm_subscription / message で形状が異なるため、
// type と message の両方を nullable にして一つの DTO で受ける。
@Serializable
data class CableEnvelope(
    val type: String? = null,
    val identifier: String? = null,
    val message: JsonElement? = null,
)

// identifier は JSON 文字列化された JSON としてサーバに送るため、
// クライアントでは Json.encodeToString して envelope の identifier フィールドに詰める。
@Serializable
data class CableIdentifier(
    val channel: String,
    @SerialName("user_id")
    val userId: String,
)
