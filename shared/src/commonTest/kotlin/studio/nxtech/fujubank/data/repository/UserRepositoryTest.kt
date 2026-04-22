package studio.nxtech.fujubank.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.remote.api.UserApi
import studio.nxtech.fujubank.domain.model.Transaction
import studio.nxtech.fujubank.domain.model.TransactionKind
import studio.nxtech.fujubank.domain.model.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class UserRepositoryTest {

    private fun httpClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                },
            )
        }
        defaultRequest {
            url("https://bank.example.test")
        }
    }

    @Test
    fun create_maps_user_response_to_domain() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "id": "usr_01HZY8X2B7",
                      "sub": "01HZY8X2B7K3J4M5N6P7Q8R9ST",
                      "balance_fuju": 0,
                      "created_at": "2026-04-21T12:34:56Z"
                    }
                    """.trimIndent(),
                ),
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val repository = UserRepository(UserApi(httpClient(engine)))

        val result = repository.create(subject = "01HZY8X2B7K3J4M5N6P7Q8R9ST")

        val success = assertIs<NetworkResult.Success<User>>(result)
        assertEquals("usr_01HZY8X2B7", success.value.id)
        assertEquals(0L, success.value.balanceFuju)
        assertEquals(Instant.parse("2026-04-21T12:34:56Z"), success.value.createdAt)
    }

    @Test
    fun get_maps_user_response_to_domain() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "id": "usr_01HZY8X2B7",
                      "sub": "s",
                      "balance_fuju": 1000,
                      "created_at": "2026-04-21T12:34:56Z"
                    }
                    """.trimIndent(),
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val repository = UserRepository(UserApi(httpClient(engine)))

        val result = repository.get("usr_01HZY8X2B7")

        val success = assertIs<NetworkResult.Success<User>>(result)
        assertEquals(1_000L, success.value.balanceFuju)
        assertEquals(Instant.parse("2026-04-21T12:34:56Z"), success.value.createdAt)
    }

    @Test
    fun transactions_maps_mint_to_domain_with_null_counterparty() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "transactions": [
                        {
                          "id": "txn_mint",
                          "transaction_kind": "mint",
                          "amount": 500,
                          "from_user_id": null,
                          "to_user_id": "usr_me",
                          "artifact_id": "art_1",
                          "occurred_at": "2026-04-21T00:00:00Z"
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val repository = UserRepository(UserApi(httpClient(engine)))

        val result = repository.transactions("usr_me")

        val success = assertIs<NetworkResult.Success<List<Transaction>>>(result)
        val txn = success.value.single()
        assertEquals("txn_mint", txn.id)
        assertEquals(TransactionKind.MINT, txn.kind)
        assertEquals(500L, txn.amount)
        assertNull(txn.counterpartyUserId)
        assertEquals("art_1", txn.artifactId)
        assertEquals(Instant.parse("2026-04-21T00:00:00Z"), txn.occurredAt)
    }

    @Test
    fun transactions_maps_outgoing_transfer_counterparty_to_recipient() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "transactions": [
                        {
                          "id": "txn_out",
                          "transaction_kind": "transfer",
                          "amount": 200,
                          "from_user_id": "usr_me",
                          "to_user_id": "usr_other",
                          "artifact_id": null,
                          "occurred_at": "2026-04-21T01:00:00Z"
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val repository = UserRepository(UserApi(httpClient(engine)))

        val result = repository.transactions("usr_me")

        val success = assertIs<NetworkResult.Success<List<Transaction>>>(result)
        val txn = success.value.single()
        assertEquals(TransactionKind.TRANSFER, txn.kind)
        assertEquals("usr_other", txn.counterpartyUserId)
        assertNull(txn.artifactId)
    }

    @Test
    fun transactions_maps_incoming_transfer_counterparty_to_sender() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "transactions": [
                        {
                          "id": "txn_in",
                          "transaction_kind": "transfer",
                          "amount": 300,
                          "from_user_id": "usr_other",
                          "to_user_id": "usr_me",
                          "artifact_id": null,
                          "occurred_at": "2026-04-21T02:00:00Z"
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val repository = UserRepository(UserApi(httpClient(engine)))

        val result = repository.transactions("usr_me")

        val success = assertIs<NetworkResult.Success<List<Transaction>>>(result)
        val txn = success.value.single()
        assertEquals(TransactionKind.TRANSFER, txn.kind)
        assertEquals("usr_other", txn.counterpartyUserId)
    }

    @Test
    fun transactions_empty_list_maps_to_empty_domain_list() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel("""{"transactions":[]}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val repository = UserRepository(UserApi(httpClient(engine)))

        val result = repository.transactions("usr_me")

        val success = assertIs<NetworkResult.Success<List<Transaction>>>(result)
        assertEquals(0, success.value.size)
    }
}
