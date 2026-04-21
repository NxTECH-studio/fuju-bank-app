package studio.nxtech.fujubank.data.remote.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LedgerTransferDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun transferRequest_serializes_snake_case_payload() {
        val encoded = json.encodeToString(
            TransferRequest.serializer(),
            TransferRequest(
                fromUserId = "usr_sender",
                toUserId = "usr_receiver",
                amount = 500L,
                idempotencyKey = "01HZY8X2B7K3J4M5N6P7Q8R9ST",
                memo = "ありがとう",
            ),
        )
        val expected = """
            {"from_user_id":"usr_sender","to_user_id":"usr_receiver","amount":500,"idempotency_key":"01HZY8X2B7K3J4M5N6P7Q8R9ST","memo":"ありがとう"}
        """.trimIndent()
        assertEquals(expected, encoded)
    }

    @Test
    fun transferRequest_memo_defaults_to_null() {
        val request = TransferRequest(
            fromUserId = "usr_sender",
            toUserId = "usr_receiver",
            amount = 1L,
            idempotencyKey = "key",
        )
        assertNull(request.memo)
    }

    @Test
    fun transferRequest_roundtrips_with_memo() {
        val original = TransferRequest(
            fromUserId = "usr_sender",
            toUserId = "usr_receiver",
            amount = 500L,
            idempotencyKey = "01HZY8X2B7K3J4M5N6P7Q8R9ST",
            memo = "お祝い",
        )
        val encoded = json.encodeToString(TransferRequest.serializer(), original)
        val decoded = json.decodeFromString(TransferRequest.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun transferRequest_roundtrips_without_memo() {
        val original = TransferRequest(
            fromUserId = "usr_sender",
            toUserId = "usr_receiver",
            amount = 1L,
            idempotencyKey = "key",
        )
        val encoded = json.encodeToString(TransferRequest.serializer(), original)
        val decoded = json.decodeFromString(TransferRequest.serializer(), encoded)
        assertEquals(original, decoded)
        assertNull(decoded.memo)
    }

    @Test
    fun transferResponse_deserializes_snake_case_payload() {
        val payload = """
            {
              "transaction_id": "txn_01HZY8X2B7",
              "new_balance": 999500
            }
        """.trimIndent()

        val decoded = json.decodeFromString(TransferResponse.serializer(), payload)

        assertEquals("txn_01HZY8X2B7", decoded.transactionId)
        assertEquals(999_500L, decoded.newBalance)
    }

    @Test
    fun transferResponse_handles_bigint_new_balance() {
        // bigint の範囲を確認（Int では溢れる値）。
        val payload = """
            {
              "transaction_id": "txn_big",
              "new_balance": 9223372036854775807
            }
        """.trimIndent()

        val decoded = json.decodeFromString(TransferResponse.serializer(), payload)

        assertEquals(Long.MAX_VALUE, decoded.newBalance)
    }

    @Test
    fun transferResponse_roundtrips() {
        val original = TransferResponse(
            transactionId = "txn_01HZY8X2B7",
            newBalance = 999_500L,
        )
        val encoded = json.encodeToString(TransferResponse.serializer(), original)
        val decoded = json.decodeFromString(TransferResponse.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun transferResponse_ignores_unknown_fields() {
        val payload = """
            {
              "transaction_id": "txn_1",
              "new_balance": 0,
              "occurred_at": "2026-04-21T00:00:00Z"
            }
        """.trimIndent()

        val decoded = json.decodeFromString(TransferResponse.serializer(), payload)

        assertEquals("txn_1", decoded.transactionId)
        assertEquals(0L, decoded.newBalance)
    }
}
