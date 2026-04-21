package studio.nxtech.fujubank.data.remote.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import studio.nxtech.fujubank.data.remote.ApiErrorCode
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.remote.dto.TransferResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LedgerApiTest {

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

    private fun HttpRequestData.bodyText(): String = (body as TextContent).text

    @Test
    fun transfer_returns_success_and_sends_idempotency_key_in_header_and_body() = runTest {
        var capturedRequest: HttpRequestData? = null
        val engine = MockEngine { request ->
            capturedRequest = request
            assertEquals("/ledger/transfer", request.url.encodedPath)
            assertEquals("POST", request.method.value)
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "transaction_id": "txn_01HZY8X2B7",
                      "new_balance": 9500
                    }
                    """.trimIndent(),
                ),
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = LedgerApi(
            client = httpClient(engine),
            idempotencyKeyFactory = { "idem_fixed_key" },
        )

        val result = api.transfer(
            fromUserId = "usr_from",
            toUserId = "usr_to",
            amount = 500,
            memo = "gift",
        )

        val success = assertIs<NetworkResult.Success<TransferResponse>>(result)
        assertEquals("txn_01HZY8X2B7", success.value.transactionId)
        assertEquals(9_500L, success.value.newBalance)

        val request = checkNotNull(capturedRequest)
        assertEquals("idem_fixed_key", request.headers["Idempotency-Key"])
        val body = request.bodyText()
        assertTrue(body.contains("\"idempotency_key\":\"idem_fixed_key\""))
        assertTrue(body.contains("\"from_user_id\":\"usr_from\""))
        assertTrue(body.contains("\"to_user_id\":\"usr_to\""))
        assertTrue(body.contains("\"amount\":500"))
        assertTrue(body.contains("\"memo\":\"gift\""))
    }

    @Test
    fun transfer_maps_422_insufficient_balance_to_failure() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "error": {
                        "code": "INSUFFICIENT_BALANCE",
                        "message": "balance is not enough"
                      }
                    }
                    """.trimIndent(),
                ),
                status = HttpStatusCode.UnprocessableEntity,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = LedgerApi(
            client = httpClient(engine),
            idempotencyKeyFactory = { "idem_any" },
        )

        val result = api.transfer(
            fromUserId = "usr_from",
            toUserId = "usr_to",
            amount = 10_000,
        )

        val failure = assertIs<NetworkResult.Failure>(result)
        assertEquals(ApiErrorCode.INSUFFICIENT_BALANCE, failure.error.code)
        assertEquals(422, failure.error.httpStatus)
        assertEquals("balance is not enough", failure.error.message)
    }

    @Test
    fun transfer_maps_mfa_required_to_failure() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "error": {
                        "code": "MFA_REQUIRED",
                        "message": "mfa required for large transfer"
                      }
                    }
                    """.trimIndent(),
                ),
                status = HttpStatusCode.Forbidden,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = LedgerApi(
            client = httpClient(engine),
            idempotencyKeyFactory = { "idem_any" },
        )

        val result = api.transfer(
            fromUserId = "usr_from",
            toUserId = "usr_to",
            amount = 1_000_000,
        )

        val failure = assertIs<NetworkResult.Failure>(result)
        assertEquals(ApiErrorCode.MFA_REQUIRED, failure.error.code)
        assertEquals(403, failure.error.httpStatus)
    }

    @Test
    fun transfer_reuses_idempotency_key_across_retries() = runTest {
        val capturedKeys = mutableListOf<String?>()
        val engine = MockEngine { request ->
            capturedKeys += request.headers["Idempotency-Key"]
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "transaction_id": "txn_same",
                      "new_balance": 9000
                    }
                    """.trimIndent(),
                ),
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = LedgerApi(
            client = httpClient(engine),
            // factory が呼ばれてしまうとテストが失敗する（明示指定時は呼ばれない想定）
            idempotencyKeyFactory = { error("idempotencyKeyFactory must not be called when key is supplied") },
        )
        val explicitKey = "idem_caller_owned"

        api.transfer(
            fromUserId = "usr_from",
            toUserId = "usr_to",
            amount = 1_000,
            idempotencyKey = explicitKey,
        )
        api.transfer(
            fromUserId = "usr_from",
            toUserId = "usr_to",
            amount = 1_000,
            idempotencyKey = explicitKey,
        )

        assertEquals(listOf<String?>(explicitKey, explicitKey), capturedKeys.toList())
    }
}
