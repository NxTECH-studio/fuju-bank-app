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
import studio.nxtech.fujubank.data.remote.dto.UserResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UserMeApiTest {

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
    fun upsertMe_posts_to_users_me_and_returns_user_response() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/users/me", request.url.encodedPath)
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
        val api = UserMeApi(httpClient(engine))

        val result = api.upsertMe()

        val success = assertIs<NetworkResult.Success<UserResponse>>(result)
        assertEquals("usr_01HZY8X2B7", success.value.id)
        assertEquals(0L, success.value.balanceFuju)
    }

    @Test
    fun getMe_gets_users_me_and_returns_user_response() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/users/me", request.url.encodedPath)
            assertEquals("GET", request.method.value)
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "id": "usr_01HZY8X2B7",
                      "sub": "s",
                      "balance_fuju": 1234,
                      "created_at": "2026-04-21T12:34:56Z"
                    }
                    """.trimIndent(),
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = UserMeApi(httpClient(engine))

        val result = api.getMe()

        val success = assertIs<NetworkResult.Success<UserResponse>>(result)
        assertEquals(1_234L, success.value.balanceFuju)
    }

    @Test
    fun getMe_maps_401_to_unauthenticated_failure() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """{"error":{"code":"UNAUTHENTICATED","message":"token expired"}}""",
                ),
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = UserMeApi(httpClient(engine))

        val result = api.getMe()

        val failure = assertIs<NetworkResult.Failure>(result)
        assertEquals(ApiErrorCode.UNAUTHENTICATED, failure.error.code)
        assertEquals(401, failure.error.httpStatus)
    }
}
