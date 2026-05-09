package studio.nxtech.fujubank.data.remote.dto

import kotlinx.serialization.json.Json
import studio.nxtech.fujubank.domain.model.TransactionKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TransactionDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun transactionDto_deserializes_mint_payload() {
        val payload = """
            {
              "entry_id": 100,
              "transaction_id": "txn_01HZY8X2B7",
              "transaction_kind": "mint",
              "direction": "credit",
              "amount": 1000,
              "artifact_id": "art_01HZY8X2B7",
              "counterparty_user_id": null,
              "memo": null,
              "metadata": null,
              "occurred_at": "2026-04-21T12:34:56Z",
              "created_at": "2026-04-21T12:34:57Z"
            }
        """.trimIndent()

        val decoded = json.decodeFromString(TransactionDto.serializer(), payload)

        assertEquals("txn_01HZY8X2B7", decoded.id)
        assertEquals(TransactionKind.MINT, decoded.kind)
        assertEquals(TransactionDirectionWire.CREDIT, decoded.direction)
        assertEquals(1_000L, decoded.amount)
        assertEquals("art_01HZY8X2B7", decoded.artifactId)
        assertNull(decoded.counterpartyUserId)
        assertEquals("2026-04-21T12:34:56Z", decoded.occurredAt)
    }

    @Test
    fun transactionDto_deserializes_transfer_payload() {
        val payload = """
            {
              "entry_id": 101,
              "transaction_id": "txn_02HZY8X2B7",
              "transaction_kind": "transfer",
              "direction": "debit",
              "amount": 500,
              "artifact_id": null,
              "counterparty_user_id": "usr_other",
              "occurred_at": "2026-04-21T12:35:00Z",
              "created_at": "2026-04-21T12:35:01Z"
            }
        """.trimIndent()

        val decoded = json.decodeFromString(TransactionDto.serializer(), payload)

        assertEquals(TransactionKind.TRANSFER, decoded.kind)
        assertEquals(TransactionDirectionWire.DEBIT, decoded.direction)
        assertEquals("usr_other", decoded.counterpartyUserId)
        assertNull(decoded.artifactId)
    }

    @Test
    fun transactionDto_mint_roundtrips() {
        val original = TransactionDto(
            id = "txn_01HZY8X2B7",
            kind = TransactionKind.MINT,
            direction = TransactionDirectionWire.CREDIT,
            amount = 1_000L,
            artifactId = "art_01HZY8X2B7",
            counterpartyUserId = null,
            occurredAt = "2026-04-21T12:34:56Z",
        )
        val encoded = json.encodeToString(TransactionDto.serializer(), original)
        val decoded = json.decodeFromString(TransactionDto.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun transactionDto_transfer_roundtrips() {
        val original = TransactionDto(
            id = "txn_02HZY8X2B7",
            kind = TransactionKind.TRANSFER,
            direction = TransactionDirectionWire.DEBIT,
            amount = 500L,
            artifactId = null,
            counterpartyUserId = "usr_other",
            occurredAt = "2026-04-21T12:35:00Z",
        )
        val encoded = json.encodeToString(TransactionDto.serializer(), original)
        val decoded = json.decodeFromString(TransactionDto.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun transactionKind_serializes_as_snake_case() {
        val encodedMint = json.encodeToString(TransactionKind.serializer(), TransactionKind.MINT)
        val encodedTransfer =
            json.encodeToString(TransactionKind.serializer(), TransactionKind.TRANSFER)
        assertEquals("\"mint\"", encodedMint)
        assertEquals("\"transfer\"", encodedTransfer)
    }

    @Test
    fun transactionDirectionWire_serializes_as_lowercase() {
        val credit =
            json.encodeToString(TransactionDirectionWire.serializer(), TransactionDirectionWire.CREDIT)
        val debit =
            json.encodeToString(TransactionDirectionWire.serializer(), TransactionDirectionWire.DEBIT)
        assertEquals("\"credit\"", credit)
        assertEquals("\"debit\"", debit)
    }

    @Test
    fun transactionDto_handles_bigint_amount() {
        // bigint の範囲を確認（Int では溢れる値）。
        val payload = """
            {
              "transaction_id": "txn_big",
              "transaction_kind": "mint",
              "direction": "credit",
              "amount": 9223372036854775807,
              "artifact_id": "art_1",
              "counterparty_user_id": null,
              "occurred_at": "2026-04-21T00:00:00Z"
            }
        """.trimIndent()

        val decoded = json.decodeFromString(TransactionDto.serializer(), payload)

        assertEquals(Long.MAX_VALUE, decoded.amount)
    }

    @Test
    fun transactionListResponse_roundtrips() {
        val original = TransactionListResponse(
            data = listOf(
                TransactionDto(
                    id = "txn_01",
                    kind = TransactionKind.MINT,
                    direction = TransactionDirectionWire.CREDIT,
                    amount = 1_000L,
                    artifactId = "art_1",
                    counterpartyUserId = null,
                    occurredAt = "2026-04-21T12:34:56Z",
                ),
                TransactionDto(
                    id = "txn_02",
                    kind = TransactionKind.TRANSFER,
                    direction = TransactionDirectionWire.DEBIT,
                    amount = 500L,
                    artifactId = null,
                    counterpartyUserId = "usr_2",
                    occurredAt = "2026-04-21T12:35:00Z",
                ),
            ),
        )
        val encoded = json.encodeToString(TransactionListResponse.serializer(), original)
        val decoded = json.decodeFromString(TransactionListResponse.serializer(), encoded)
        assertEquals(original, decoded)
        assertEquals(2, decoded.data.size)
    }

    @Test
    fun transactionListResponse_ignores_unknown_fields() {
        val payload = """
            {
              "data": [],
              "next_cursor": "abc"
            }
        """.trimIndent()

        val decoded = json.decodeFromString(TransactionListResponse.serializer(), payload)

        assertEquals(0, decoded.data.size)
    }
}
