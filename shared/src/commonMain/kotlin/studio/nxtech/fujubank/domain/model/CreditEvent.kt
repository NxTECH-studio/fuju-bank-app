package studio.nxtech.fujubank.domain.model

import kotlin.time.Instant
import studio.nxtech.fujubank.data.remote.dto.TransactionKind

// UserChannel から push される credit イベントのドメイン表現。
// fromUserId は mint の場合 null、transfer の場合は送金元ユーザ ID。
data class CreditEvent(
    val transactionId: String,
    val amount: Long,
    val kind: TransactionKind,
    val counterpartyUserId: String?,
    val artifactId: String?,
    val occurredAt: Instant,
)
