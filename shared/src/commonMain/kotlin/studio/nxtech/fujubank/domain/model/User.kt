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
    val direction: TransactionDirection,
    val amount: Long,
    val counterpartyUserId: String?,
    val artifactId: String?,
    val occurredAt: Instant,
)

/**
 * 自分から見た取引の向き。`kind` だけでは送金/受取の区別ができないため、Repository 層で
 * `kind` と server から返る `direction` (credit/debit) を組み合わせて付与する。
 *
 * - [Mint]: 新規発行で残高が増えた取引（`kind = mint`、`counterparty_user_id` は null）。
 * - [Incoming]: 他者からの transfer で残高が増えた取引（`kind = transfer` + `direction = credit`）。
 * - [Outgoing]: 他者への transfer で残高が減った取引（`kind = transfer` + `direction = debit`）。
 */
enum class TransactionDirection { Mint, Incoming, Outgoing }
