package studio.nxtech.fujubank.domain.model

import kotlin.time.Instant

data class User(
    val id: String,
    val balanceFuju: Long,
    val createdAt: Instant,
)

data class Transaction(
    val id: String,
    val kind: TransactionKind,
    val amount: Long,
    val counterpartyUserId: String?,
    val artifactId: String?,
    val occurredAt: Instant,
)
