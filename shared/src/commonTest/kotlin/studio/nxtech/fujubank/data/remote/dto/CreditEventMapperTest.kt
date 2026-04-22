package studio.nxtech.fujubank.data.remote.dto

import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CreditEventMapperTest {

    @Test
    fun toDomain_maps_transfer_event() {
        val dto = CreditEventDto(
            type = "credit",
            amount = 500,
            transactionId = "txn_1",
            transactionKind = "transfer",
            artifactId = null,
            fromUserId = "usr_sender",
            occurredAt = "2026-04-22T00:00:00Z",
        )

        val domain = assertNotNull(dto.toDomain())
        assertEquals("txn_1", domain.transactionId)
        assertEquals(500L, domain.amount)
        assertEquals(TransactionKind.TRANSFER, domain.kind)
        assertEquals("usr_sender", domain.counterpartyUserId)
        assertNull(domain.artifactId)
        assertEquals(Instant.parse("2026-04-22T00:00:00Z"), domain.occurredAt)
    }

    @Test
    fun toDomain_maps_mint_event_with_artifact() {
        val dto = CreditEventDto(
            type = "credit",
            amount = 1_200,
            transactionId = "txn_mint",
            transactionKind = "mint",
            artifactId = "art_1",
            fromUserId = null,
            occurredAt = "2026-04-22T01:23:45Z",
        )

        val domain = assertNotNull(dto.toDomain())
        assertEquals(TransactionKind.MINT, domain.kind)
        assertEquals("art_1", domain.artifactId)
        assertNull(domain.counterpartyUserId)
    }

    @Test
    fun toDomain_returns_null_for_unknown_kind() {
        val dto = CreditEventDto(
            type = "credit",
            amount = 1,
            transactionId = "txn_x",
            transactionKind = "burn",
            artifactId = null,
            fromUserId = null,
            occurredAt = "2026-04-22T00:00:00Z",
        )

        assertNull(dto.toDomain())
    }

    @Test
    fun toDomain_returns_null_for_invalid_occurred_at() {
        val dto = CreditEventDto(
            type = "credit",
            amount = 1,
            transactionId = "txn_x",
            transactionKind = "transfer",
            artifactId = null,
            fromUserId = "usr_sender",
            occurredAt = "not-a-timestamp",
        )

        assertNull(dto.toDomain())
    }
}
