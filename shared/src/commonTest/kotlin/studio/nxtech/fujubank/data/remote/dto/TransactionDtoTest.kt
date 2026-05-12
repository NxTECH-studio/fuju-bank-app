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
              "counterparty_public_id": null,
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
        assertNull(decoded.counterpartyPublicId)
        assertNull(decoded.memo)
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
              "counterparty_public_id": "alice",
              "occurred_at": "2026-04-21T12:35:00Z",
              "created_at": "2026-04-21T12:35:01Z"
            }
        """.trimIndent()

        val decoded = json.decodeFromString(TransactionDto.serializer(), payload)

        assertEquals(TransactionKind.TRANSFER, decoded.kind)
        assertEquals(TransactionDirectionWire.DEBIT, decoded.direction)
        assertEquals("usr_other", decoded.counterpartyUserId)
        assertEquals("alice", decoded.counterpartyPublicId)
        assertNull(decoded.artifactId)
        // memo フィールドが payload に存在しない場合はデフォルト null になる。
        assertNull(decoded.memo)
    }

    @Test
    fun transactionDto_deserializes_transfer_with_memo() {
        // 送金時に memo を付与した payload。client は memo をそのまま受け取って表示する。
        val payload = """
            {
              "entry_id": 110,
              "transaction_id": "txn_with_memo",
              "transaction_kind": "transfer",
              "direction": "credit",
              "amount": 1500,
              "artifact_id": null,
              "counterparty_user_id": "usr_other",
              "memo": "ランチ代",
              "occurred_at": "2026-04-22T12:00:00Z"
            }
        """.trimIndent()

        val decoded = json.decodeFromString(TransactionDto.serializer(), payload)

        assertEquals("ランチ代", decoded.memo)
    }

    @Test
    fun transactionDto_deserializes_memo_at_max_length() {
        // 80 文字ぴったりの memo はそのまま保持する（client 側で truncate しない）。
        val memo80 = "あ".repeat(80)
        val payload = """
            {
              "transaction_id": "txn_memo_80",
              "transaction_kind": "transfer",
              "direction": "debit",
              "amount": 100,
              "artifact_id": null,
              "counterparty_user_id": "usr_other",
              "memo": "$memo80",
              "occurred_at": "2026-04-22T13:00:00Z"
            }
        """.trimIndent()

        val decoded = json.decodeFromString(TransactionDto.serializer(), payload)

        assertEquals(80, decoded.memo?.length)
        assertEquals(memo80, decoded.memo)
    }

    @Test
    fun transactionDto_deserializes_memo_above_max_length_without_truncation() {
        // サーバが何らかの理由で 80 文字超を返してきても、client 側では truncate せず素通しする。
        // 表示側 (履歴行 / 詳細) で必要に応じて省略する責務分担。
        val memo90 = "x".repeat(90)
        val payload = """
            {
              "transaction_id": "txn_memo_90",
              "transaction_kind": "transfer",
              "direction": "debit",
              "amount": 200,
              "artifact_id": null,
              "counterparty_user_id": "usr_other",
              "memo": "$memo90",
              "occurred_at": "2026-04-22T14:00:00Z"
            }
        """.trimIndent()

        val decoded = json.decodeFromString(TransactionDto.serializer(), payload)

        assertEquals(90, decoded.memo?.length)
        assertEquals(memo90, decoded.memo)
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
            counterpartyPublicId = null,
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
            counterpartyPublicId = "alice",
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
              "counterparty_public_id": null,
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
                    counterpartyPublicId = null,
                    occurredAt = "2026-04-21T12:34:56Z",
                ),
                TransactionDto(
                    id = "txn_02",
                    kind = TransactionKind.TRANSFER,
                    direction = TransactionDirectionWire.DEBIT,
                    amount = 500L,
                    artifactId = null,
                    counterpartyUserId = "usr_2",
                    counterpartyPublicId = "bob",
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
