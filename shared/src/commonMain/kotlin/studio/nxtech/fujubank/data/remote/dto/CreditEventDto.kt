package studio.nxtech.fujubank.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// UserChannel から push される credit イベントのペイロード。
// message 内の type === "credit" でアプリ側イベントを判別する。
@Serializable
data class CreditEventDto(
    val type: String,
    // bigint: クライアント側は小数計算に関与しないため Long で受ける。
    val amount: Long,
    @SerialName("transaction_id")
    val transactionId: String,
    @SerialName("transaction_kind")
    val transactionKind: String,
    // transfer 以外（mint など）では null。
    @SerialName("artifact_id")
    val artifactId: String?,
    // mint の場合は null。
    @SerialName("from_user_id")
    val fromUserId: String?,
    // ISO8601 文字列。Instant への変換は Repository 層で行う。
    @SerialName("occurred_at")
    val occurredAt: String,
    // 将来的な拡張フィールド。形状が未確定なので JsonElement で受ける。
    val metadata: JsonElement? = null,
)
