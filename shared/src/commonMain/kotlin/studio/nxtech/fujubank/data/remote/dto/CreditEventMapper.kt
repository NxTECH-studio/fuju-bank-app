package studio.nxtech.fujubank.data.remote.dto

import kotlin.time.Instant
import studio.nxtech.fujubank.domain.model.CreditEvent

// 未知の transaction_kind や不正な occurred_at は UserChannel の Flow を落とさず黙殺する。
// 上流 (UserChannelClient.parseCreditEvent) でも同様に runCatching で握り潰しているため方針を合わせる。
fun CreditEventDto.toDomain(): CreditEvent? {
    val kind = when (transactionKind) {
        "mint" -> TransactionKind.MINT
        "transfer" -> TransactionKind.TRANSFER
        else -> return null
    }
    val occurred = runCatching { Instant.parse(occurredAt) }.getOrNull() ?: return null
    return CreditEvent(
        transactionId = transactionId,
        amount = amount,
        kind = kind,
        counterpartyUserId = fromUserId,
        artifactId = artifactId,
        occurredAt = occurred,
    )
}
