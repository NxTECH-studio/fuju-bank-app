package studio.nxtech.fujubank.domain.model

import kotlin.time.Instant

data class User(
    // bank PK を文字列化したもの。`/users/:id/transactions` などの bank 内部経路で使う。
    val id: String,
    // AuthCore の ULID (= bank の external_user_id)。`/ledger/transfer` の from/to や
    // `SessionStore.userId` の源泉として使う。bank-backend 2026-05 以降 `serialize_user` で
    // 必ず返るため非 null（欠落時は DTO 層の deserialize で NetworkFailure に倒れる）。
    val subject: String,
    val balanceFuju: Long,
    val createdAt: Instant,
)

data class Transaction(
    val id: String,
    val kind: TransactionKind,
    val direction: TransactionDirection,
    val amount: Long,
    val counterpartyUserId: String?,
    // transfer のとき counterparty の AuthCore 公開ハンドル (public_id)。mint は null。
    // UI 表示はこちらを `@{publicId}` 形式で出す（counterpartyUserId は内部経路用に保持）。
    val counterpartyPublicId: String?,
    val artifactId: String?,
    val occurredAt: Instant,
    // 送金時の任意メモ（最大 80 文字）。mint や memo 未指定 transfer は null。
    val memo: String? = null,
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
