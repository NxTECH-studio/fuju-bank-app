package studio.nxtech.fujubank.data.remote.dto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActionCableDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun cableEnvelope_decodes_welcome_frame() {
        val payload = """{"type":"welcome"}"""

        val decoded = json.decodeFromString(CableEnvelope.serializer(), payload)

        assertEquals("welcome", decoded.type)
        assertNull(decoded.identifier)
        assertNull(decoded.message)
    }

    @Test
    fun cableEnvelope_decodes_confirm_subscription_frame() {
        val payload = """
            {"type":"confirm_subscription","identifier":"{\"channel\":\"UserChannel\",\"user_id\":\"usr_1\"}"}
        """.trimIndent()

        val decoded = json.decodeFromString(CableEnvelope.serializer(), payload)

        assertEquals("confirm_subscription", decoded.type)
        assertEquals("{\"channel\":\"UserChannel\",\"user_id\":\"usr_1\"}", decoded.identifier)
        assertNull(decoded.message)
    }

    @Test
    fun cableEnvelope_decodes_message_frame_with_credit_payload() {
        val payload = """
            {
              "identifier": "{\"channel\":\"UserChannel\",\"user_id\":\"usr_1\"}",
              "message": {
                "type": "credit",
                "amount": 1000,
                "transaction_id": "txn_01HZY8X2B7",
                "transaction_kind": "transfer",
                "artifact_id": null,
                "from_user_id": "usr_sender",
                "occurred_at": "2026-04-21T00:00:00Z"
              }
            }
        """.trimIndent()

        val decoded = json.decodeFromString(CableEnvelope.serializer(), payload)

        assertNull(decoded.type)
        assertEquals("{\"channel\":\"UserChannel\",\"user_id\":\"usr_1\"}", decoded.identifier)
        val message = assertNotNull(decoded.message)
        assertEquals("credit", message.jsonObject["type"]?.jsonPrimitive?.content)
    }

    @Test
    fun cableIdentifier_serializes_snake_case_payload() {
        val identifier = CableIdentifier(channel = "UserChannel", userId = "usr_01HZY8X2B7")

        val encoded = json.encodeToString(CableIdentifier.serializer(), identifier)

        assertEquals("""{"channel":"UserChannel","user_id":"usr_01HZY8X2B7"}""", encoded)
    }

    @Test
    fun cableIdentifier_roundtrips() {
        val original = CableIdentifier(channel = "UserChannel", userId = "usr_01HZY8X2B7")
        val encoded = json.encodeToString(CableIdentifier.serializer(), original)
        val decoded = json.decodeFromString(CableIdentifier.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun creditEventDto_decodes_transfer_payload() {
        val payload = """
            {
              "type": "credit",
              "amount": 500,
              "transaction_id": "txn_01HZY8X2B7",
              "transaction_kind": "transfer",
              "artifact_id": null,
              "from_user_id": "usr_sender",
              "occurred_at": "2026-04-21T00:00:00Z"
            }
        """.trimIndent()

        val decoded = json.decodeFromString(CreditEventDto.serializer(), payload)

        assertEquals("credit", decoded.type)
        assertEquals(500L, decoded.amount)
        assertEquals("txn_01HZY8X2B7", decoded.transactionId)
        assertEquals("transfer", decoded.transactionKind)
        assertNull(decoded.artifactId)
        assertEquals("usr_sender", decoded.fromUserId)
        assertEquals("2026-04-21T00:00:00Z", decoded.occurredAt)
        assertNull(decoded.metadata)
    }

    @Test
    fun creditEventDto_decodes_mint_payload_with_artifact() {
        val payload = """
            {
              "type": "credit",
              "amount": 1200,
              "transaction_id": "txn_mint_1",
              "transaction_kind": "mint",
              "artifact_id": "art_01HZY8X2B7",
              "from_user_id": null,
              "occurred_at": "2026-04-21T01:23:45Z"
            }
        """.trimIndent()

        val decoded = json.decodeFromString(CreditEventDto.serializer(), payload)

        assertEquals("mint", decoded.transactionKind)
        assertEquals("art_01HZY8X2B7", decoded.artifactId)
        assertNull(decoded.fromUserId)
    }

    @Test
    fun creditEventDto_decodes_payload_with_metadata_object() {
        val payload = """
            {
              "type": "credit",
              "amount": 1,
              "transaction_id": "txn_meta",
              "transaction_kind": "transfer",
              "artifact_id": null,
              "from_user_id": "usr_sender",
              "occurred_at": "2026-04-21T00:00:00Z",
              "metadata": {"memo": "お祝い"}
            }
        """.trimIndent()

        val decoded = json.decodeFromString(CreditEventDto.serializer(), payload)

        val metadata = assertNotNull(decoded.metadata)
        assertTrue(metadata is JsonObject)
        assertEquals("お祝い", metadata["memo"]?.jsonPrimitive?.content)
    }

    @Test
    fun creditEventDto_handles_bigint_amount() {
        val payload = """
            {
              "type": "credit",
              "amount": 9223372036854775807,
              "transaction_id": "txn_big",
              "transaction_kind": "mint",
              "artifact_id": null,
              "from_user_id": null,
              "occurred_at": "2026-04-21T00:00:00Z"
            }
        """.trimIndent()

        val decoded = json.decodeFromString(CreditEventDto.serializer(), payload)

        assertEquals(Long.MAX_VALUE, decoded.amount)
    }

    @Test
    fun creditEventDto_ignores_unknown_fields() {
        val payload = """
            {
              "type": "credit",
              "amount": 10,
              "transaction_id": "txn_unknown",
              "transaction_kind": "transfer",
              "artifact_id": null,
              "from_user_id": "usr_sender",
              "occurred_at": "2026-04-21T00:00:00Z",
              "server_sequence": 42
            }
        """.trimIndent()

        val decoded = json.decodeFromString(CreditEventDto.serializer(), payload)

        assertEquals(10L, decoded.amount)
        assertEquals("txn_unknown", decoded.transactionId)
    }
}
