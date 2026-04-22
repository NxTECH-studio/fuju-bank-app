package studio.nxtech.fujubank.data.remote.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UserChannelClientTest {

    private fun newClient(): UserChannelClient {
        val engine = MockEngine { respond("", HttpStatusCode.OK) }
        return UserChannelClient(
            client = HttpClient(engine),
            cableUrl = "wss://bank.example.test/cable",
            scope = CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
        )
    }

    @Test
    fun parseCreditEvent_returns_null_for_welcome_frame() {
        val result = newClient().parseCreditEvent("""{"type":"welcome"}""")
        assertNull(result)
    }

    @Test
    fun parseCreditEvent_returns_null_for_ping_frame() {
        val result = newClient().parseCreditEvent("""{"type":"ping","message":1714646400}""")
        assertNull(result)
    }

    @Test
    fun parseCreditEvent_returns_null_for_confirm_subscription_frame() {
        val result = newClient().parseCreditEvent(
            """{"type":"confirm_subscription","identifier":"{\"channel\":\"UserChannel\"}"}""",
        )
        assertNull(result)
    }

    @Test
    fun parseCreditEvent_decodes_transfer_credit_frame() {
        val frame = """
            {
              "identifier": "{\"channel\":\"UserChannel\",\"user_id\":\"usr_1\"}",
              "message": {
                "type": "credit",
                "amount": 500,
                "transaction_id": "txn_01HZY8X2B7",
                "transaction_kind": "transfer",
                "artifact_id": null,
                "from_user_id": "usr_sender",
                "occurred_at": "2026-04-22T00:00:00Z"
              }
            }
        """.trimIndent()

        val credit = assertNotNull(newClient().parseCreditEvent(frame))
        assertEquals("credit", credit.type)
        assertEquals(500L, credit.amount)
        assertEquals("txn_01HZY8X2B7", credit.transactionId)
        assertEquals("transfer", credit.transactionKind)
        assertNull(credit.artifactId)
        assertEquals("usr_sender", credit.fromUserId)
    }

    @Test
    fun parseCreditEvent_decodes_mint_credit_frame_with_artifact() {
        val frame = """
            {
              "identifier": "{\"channel\":\"UserChannel\",\"user_id\":\"usr_1\"}",
              "message": {
                "type": "credit",
                "amount": 1200,
                "transaction_id": "txn_mint_1",
                "transaction_kind": "mint",
                "artifact_id": "art_01HZY8X2B7",
                "from_user_id": null,
                "occurred_at": "2026-04-22T01:23:45Z"
              }
            }
        """.trimIndent()

        val credit = assertNotNull(newClient().parseCreditEvent(frame))
        assertEquals("mint", credit.transactionKind)
        assertEquals("art_01HZY8X2B7", credit.artifactId)
        assertNull(credit.fromUserId)
    }

    @Test
    fun parseCreditEvent_returns_null_for_non_credit_message() {
        val frame = """
            {
              "identifier": "{\"channel\":\"UserChannel\"}",
              "message": {
                "type": "other",
                "amount": 1,
                "transaction_id": "txn_x",
                "transaction_kind": "transfer",
                "artifact_id": null,
                "from_user_id": "usr_sender",
                "occurred_at": "2026-04-22T00:00:00Z"
              }
            }
        """.trimIndent()

        assertNull(newClient().parseCreditEvent(frame))
    }

    @Test
    fun parseCreditEvent_returns_null_for_malformed_json() {
        assertNull(newClient().parseCreditEvent("not a json"))
    }

    @Test
    fun parseCreditEvent_returns_null_when_message_is_missing_required_field() {
        // occurred_at が欠落しているので CreditEventDto へのデコードに失敗する。
        val frame = """
            {
              "message": {
                "type": "credit",
                "amount": 1,
                "transaction_id": "txn_x",
                "transaction_kind": "transfer",
                "artifact_id": null,
                "from_user_id": "usr_sender"
              }
            }
        """.trimIndent()

        assertNull(newClient().parseCreditEvent(frame))
    }
}
