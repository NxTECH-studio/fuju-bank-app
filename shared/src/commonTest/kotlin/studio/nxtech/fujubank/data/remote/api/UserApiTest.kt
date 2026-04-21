package studio.nxtech.fujubank.data.remote.api

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
import kotlinx.serialization.json.Json
import studio.nxtech.fujubank.data.remote.ApiErrorCode
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.remote.dto.CreateUserRequest
import studio.nxtech.fujubank.data.remote.dto.TransactionKind
import studio.nxtech.fujubank.data.remote.dto.TransactionListResponse
import studio.nxtech.fujubank.data.remote.dto.UserResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UserApiTest {

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
    fun create_returns_success_for_201_payload() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/users", request.url.encodedPath)
            assertEquals("POST", request.method.value)
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
        val api = UserApi(httpClient(engine))

        val result = api.create(CreateUserRequest(subject = "01HZY8X2B7K3J4M5N6P7Q8R9ST"))

        val success = assertIs<NetworkResult.Success<UserResponse>>(result)
        assertEquals("usr_01HZY8X2B7", success.value.id)
        assertEquals(0L, success.value.balanceFuju)
    }

    @Test
    fun get_returns_success_for_200_payload() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/users/usr_01HZY8X2B7", request.url.encodedPath)
            assertEquals("GET", request.method.value)
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
        val api = UserApi(httpClient(engine))

        val result = api.get("usr_01HZY8X2B7")

        val success = assertIs<NetworkResult.Success<UserResponse>>(result)
        assertEquals(1_000L, success.value.balanceFuju)
    }

    @Test
    fun get_maps_404_to_not_found_failure() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "error": {
                        "code": "NOT_FOUND",
                        "message": "user not found"
                      }
                    }
                    """.trimIndent(),
                ),
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = UserApi(httpClient(engine))

        val result = api.get("usr_missing")

        val failure = assertIs<NetworkResult.Failure>(result)
        assertEquals(ApiErrorCode.NOT_FOUND, failure.error.code)
        assertEquals(404, failure.error.httpStatus)
        assertEquals("user not found", failure.error.message)
    }

    @Test
    fun transactions_returns_success_for_200_payload() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/users/usr_01HZY8X2B7/transactions", request.url.encodedPath)
            assertEquals("GET", request.method.value)
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "transactions": [
                        {
                          "id": "txn_1",
                          "transaction_kind": "mint",
                          "amount": 500,
                          "from_user_id": null,
                          "to_user_id": "usr_01HZY8X2B7",
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
        val api = UserApi(httpClient(engine))

        val result = api.transactions("usr_01HZY8X2B7")

        val success = assertIs<NetworkResult.Success<TransactionListResponse>>(result)
        assertEquals(1, success.value.transactions.size)
        assertEquals(TransactionKind.MINT, success.value.transactions[0].kind)
    }

    @Test
    fun transactions_maps_401_to_unauthenticated_failure() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "error": {
                        "code": "UNAUTHENTICATED",
                        "message": "token expired"
                      }
                    }
                    """.trimIndent(),
                ),
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = UserApi(httpClient(engine))

        val result = api.transactions("usr_01HZY8X2B7")

        val failure = assertIs<NetworkResult.Failure>(result)
        assertEquals(ApiErrorCode.UNAUTHENTICATED, failure.error.code)
        assertEquals(401, failure.error.httpStatus)
        assertTrue(failure.error.message.isNotEmpty())
    }
}
