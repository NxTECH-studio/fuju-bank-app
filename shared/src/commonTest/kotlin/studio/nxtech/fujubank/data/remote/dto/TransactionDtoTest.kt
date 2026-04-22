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
              "id": "txn_01HZY8X2B7",
              "transaction_kind": "mint",
              "amount": 1000,
              "from_user_id": null,
              "to_user_id": "usr_01HZY8X2B7",
              "artifact_id": "art_01HZY8X2B7",
              "occurred_at": "2026-04-21T12:34:56Z"
            }
        """.trimIndent()

        val decoded = json.decodeFromString(TransactionDto.serializer(), payload)

        assertEquals("txn_01HZY8X2B7", decoded.id)
        assertEquals(TransactionKind.MINT, decoded.kind)
        assertEquals(1_000L, decoded.amount)
        assertNull(decoded.fromUserId)
        assertEquals("usr_01HZY8X2B7", decoded.toUserId)
        assertEquals("art_01HZY8X2B7", decoded.artifactId)
        assertEquals("2026-04-21T12:34:56Z", decoded.occurredAt)
    }

    @Test
    fun transactionDto_deserializes_transfer_payload() {
        val payload = """
            {
              "id": "txn_02HZY8X2B7",
              "transaction_kind": "transfer",
              "amount": 500,
              "from_user_id": "usr_sender",
              "to_user_id": "usr_receiver",
              "artifact_id": null,
              "occurred_at": "2026-04-21T12:35:00Z"
            }
        """.trimIndent()

        val decoded = json.decodeFromString(TransactionDto.serializer(), payload)

        assertEquals(TransactionKind.TRANSFER, decoded.kind)
        assertEquals("usr_sender", decoded.fromUserId)
        assertEquals("usr_receiver", decoded.toUserId)
        assertNull(decoded.artifactId)
    }

    @Test
    fun transactionDto_mint_roundtrips() {
        val original = TransactionDto(
            id = "txn_01HZY8X2B7",
            kind = TransactionKind.MINT,
            amount = 1_000L,
            fromUserId = null,
            toUserId = "usr_01HZY8X2B7",
            artifactId = "art_01HZY8X2B7",
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
            amount = 500L,
            fromUserId = "usr_sender",
            toUserId = "usr_receiver",
            artifactId = null,
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
    fun transactionDto_handles_bigint_amount() {
        // bigint の範囲を確認（Int では溢れる値）。
        val payload = """
            {
              "id": "txn_big",
              "transaction_kind": "mint",
              "amount": 9223372036854775807,
              "from_user_id": null,
              "to_user_id": "usr_1",
              "artifact_id": "art_1",
              "occurred_at": "2026-04-21T00:00:00Z"
            }
        """.trimIndent()

        val decoded = json.decodeFromString(TransactionDto.serializer(), payload)

        assertEquals(Long.MAX_VALUE, decoded.amount)
    }

    @Test
    fun transactionListResponse_roundtrips() {
        val original = TransactionListResponse(
            transactions = listOf(
                TransactionDto(
                    id = "txn_01",
                    kind = TransactionKind.MINT,
                    amount = 1_000L,
                    fromUserId = null,
                    toUserId = "usr_1",
                    artifactId = "art_1",
                    occurredAt = "2026-04-21T12:34:56Z",
                ),
                TransactionDto(
                    id = "txn_02",
                    kind = TransactionKind.TRANSFER,
                    amount = 500L,
                    fromUserId = "usr_1",
                    toUserId = "usr_2",
                    artifactId = null,
                    occurredAt = "2026-04-21T12:35:00Z",
                ),
            ),
        )
        val encoded = json.encodeToString(TransactionListResponse.serializer(), original)
        val decoded = json.decodeFromString(TransactionListResponse.serializer(), encoded)
        assertEquals(original, decoded)
        assertEquals(2, decoded.transactions.size)
    }

    @Test
    fun transactionListResponse_ignores_unknown_fields() {
        val payload = """
            {
              "transactions": [],
              "next_cursor": "abc"
            }
        """.trimIndent()

        val decoded = json.decodeFromString(TransactionListResponse.serializer(), payload)

        assertEquals(0, decoded.transactions.size)
    }
}
