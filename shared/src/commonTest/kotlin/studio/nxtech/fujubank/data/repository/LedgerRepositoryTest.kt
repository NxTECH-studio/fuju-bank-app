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
import kotlinx.serialization.json.Json
import studio.nxtech.fujubank.data.remote.ApiErrorCode
import studio.nxtech.fujubank.data.remote.api.LedgerApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LedgerRepositoryTest {

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
    fun transfer_returns_success_with_generated_idempotency_key() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "transaction_id": "txn_new",
                      "new_balance": 8500
                    }
                    """.trimIndent(),
                ),
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = LedgerApi(
            client = httpClient(engine),
            idempotencyKeyFactory = { "idem_api_default" },
        )
        val repo = LedgerRepository(
            ledgerApi = api,
            idempotencyKeyFactory = { "idem_repo_new" },
            useDummyData = false,
        )

        val result = repo.transfer(
            from = "usr_from",
            to = "usr_to",
            amount = 500,
            memo = "gift",
        )

        val success = assertIs<TransferResult.Success>(result)
        assertEquals("txn_new", success.transactionId)
        assertEquals(8_500L, success.newBalance)
    }

    @Test
    fun transfer_insufficient_balance_returns_failure() = runTest {
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
            idempotencyKeyFactory = { "idem_api_default" },
        )
        val repo = LedgerRepository(
            ledgerApi = api,
            idempotencyKeyFactory = { "idem_repo_new" },
            useDummyData = false,
        )

        val result = repo.transfer(
            from = "usr_from",
            to = "usr_to",
            amount = 10_000,
        )

        val failure = assertIs<TransferResult.Failure>(result)
        assertEquals(ApiErrorCode.INSUFFICIENT_BALANCE, failure.error.code)
        assertEquals(422, failure.error.httpStatus)
    }

    @Test
    fun transfer_mfa_required_returns_retry_key_and_retry_with_same_key_succeeds() = runTest {
        val capturedKeys = mutableListOf<String?>()
        var callCount = 0
        val engine = MockEngine { request ->
            capturedKeys += request.headers["Idempotency-Key"]
            callCount += 1
            if (callCount == 1) {
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
            } else {
                respond(
                    content = ByteReadChannel(
                        """
                        {
                          "transaction_id": "txn_after_mfa",
                          "new_balance": 500
                        }
                        """.trimIndent(),
                    ),
                    status = HttpStatusCode.Created,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val api = LedgerApi(
            client = httpClient(engine),
            // Repository 側で採番したキーが常に使われる想定。API 側 factory は使わない。
            idempotencyKeyFactory = { error("LedgerApi factory must not be called") },
        )
        val repo = LedgerRepository(
            ledgerApi = api,
            idempotencyKeyFactory = { "idem_repo_generated" },
            useDummyData = false,
        )

        val first = repo.transfer(
            from = "usr_from",
            to = "usr_to",
            amount = 1_000_000,
        )
        val mfa = assertIs<TransferResult.MfaRequired>(first)
        assertEquals("idem_repo_generated", mfa.retryKey)

        val second = repo.transfer(
            from = "usr_from",
            to = "usr_to",
            amount = 1_000_000,
            retryKey = mfa.retryKey,
        )
        val success = assertIs<TransferResult.Success>(second)
        assertEquals("txn_after_mfa", success.transactionId)
        assertEquals(500L, success.newBalance)

        // 2 回の呼び出しで同じ Idempotency-Key が送信されていることを確認
        assertEquals(
            listOf<String?>("idem_repo_generated", "idem_repo_generated"),
            capturedKeys.toList(),
        )
    }

    @Test
    fun transfer_dummy_mode_returns_success_without_calling_api() = runTest {
        val engine = MockEngine { error("API must not be called in dummy mode") }
        val api = LedgerApi(httpClient(engine))
        val repo = LedgerRepository(
            ledgerApi = api,
            idempotencyKeyFactory = { "idem_dummy_key" },
            useDummyData = true,
        )

        val result = repo.transfer(from = "usr_from", to = "usr_to", amount = 1_000)

        val success = assertIs<TransferResult.Success>(result)
        // 1_234_567 - 1_000 = 1_233_567
        assertEquals(1_233_567L, success.newBalance)
        // ダミー ID は "txn_dummy_send_" + idempotencyKey の先頭 8 文字
        assertEquals("txn_dummy_send_idem_dum", success.transactionId)
    }

    @Test
    fun transfer_dummy_mode_insufficient_balance_when_amount_exceeds_threshold() = runTest {
        val engine = MockEngine { error("API must not be called in dummy mode") }
        val api = LedgerApi(httpClient(engine))
        val repo = LedgerRepository(
            ledgerApi = api,
            idempotencyKeyFactory = { "idem_dummy_key" },
            useDummyData = true,
        )

        val result = repo.transfer(from = "usr_from", to = "usr_to", amount = 10_000_000)

        val failure = assertIs<TransferResult.Failure>(result)
        assertEquals(ApiErrorCode.INSUFFICIENT_BALANCE, failure.error.code)
    }
}
