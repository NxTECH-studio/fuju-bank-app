package studio.nxtech.fujubank.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TransferRequest(
    @SerialName("from_user_id")
    val fromUserId: String,
    @SerialName("to_user_id")
    val toUserId: String,
    // bigint: クライアント側は小数計算に関与しないため Long で送る。
    val amount: Long,
    // ULID / UUIDv4 をクライアントで採番しリトライ時の重複防止に使う。
    @SerialName("idempotency_key")
    val idempotencyKey: String,
    val memo: String? = null,
)

@Serializable
data class TransferResponse(
    @SerialName("transaction_id")
    val transactionId: String,
    // bigint: クライアント側は小数計算に関与しないため Long で受ける。
    @SerialName("new_balance")
    val newBalance: Long,
)
