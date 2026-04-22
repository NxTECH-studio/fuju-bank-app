package studio.nxtech.fujubank.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import studio.nxtech.fujubank.domain.model.TransactionKind

@Serializable
data class TransactionListResponse(
    val transactions: List<TransactionDto>,
)

@Serializable
data class TransactionDto(
    val id: String,
    @SerialName("transaction_kind")
    val kind: TransactionKind,
    // bigint: クライアント側は小数計算に関与しないため Long で受ける。
    val amount: Long,
    // mint の場合は null。
    @SerialName("from_user_id")
    val fromUserId: String?,
    @SerialName("to_user_id")
    val toUserId: String?,
    // transfer の場合は null になる可能性あり。
    @SerialName("artifact_id")
    val artifactId: String?,
    // ISO8601 文字列。Instant への変換は Repository 層で行う。
    @SerialName("occurred_at")
    val occurredAt: String,
)
