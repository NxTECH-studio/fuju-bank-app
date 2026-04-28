package studio.nxtech.fujubank.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import studio.nxtech.fujubank.auth.TokenStorage
import studio.nxtech.fujubank.data.remote.ApiErrorCode
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.remote.api.AuthApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthRepositoryTest {

    private class FakeTokenStorage : TokenStorage {
        var access: String? = null
        var expiresAt: Long? = null
        var clearCalls: Int = 0

        override suspend fun loadAccess(): String? = access

        override suspend fun loadExpiresAt(): Long? = expiresAt

        override suspend fun saveAccess(token: String, expiresAt: Long?) {
            this.access = token
            this.expiresAt = expiresAt
        }

        override suspend fun clear() {
            clearCalls += 1
            access = null
            expiresAt = null
        }
    }

    private fun httpClient(engine: MockEngine, withCookies: Boolean = true): HttpClient =
        HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        explicitNulls = false
                    },
                )
            }
            if (withCookies) {
                install(HttpCookies)
            }
            defaultRequest {
                url("https://authcore.example.test")
            }
        }

    private fun repository(
        engine: MockEngine,
        storage: TokenStorage,
        baseUrl: String = "https://authcore.example.test",
        withCookies: Boolean = true,
    ): AuthRepository = AuthRepository(
        authApi = AuthApi(
            client = httpClient(engine, withCookies = withCookies),
            authCoreBaseUrl = baseUrl,
        ),
        tokenStorage = storage,
    )

    @Test
    fun login_success_saves_access_and_returns_authenticated() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "access_token": "at_123",
                      "token_type": "Bearer",
                      "expires_in": 900
                    }
                    """.trimIndent(),
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    HttpHeaders.SetCookie to listOf(
                        "refresh_token=rt_456; Path=/v1/auth; HttpOnly; Max-Age=2592000",
                    ),
                ),
            )
        }
        val storage = FakeTokenStorage()
        val repo = repository(engine, storage)

        val result = repo.login(identifier = "user@example.test", password = "secret")

        val success = assertIs<NetworkResult.Success<LoginResult>>(result)
        val authenticated = assertIs<LoginResult.Authenticated>(success.value)
        assertEquals("at_123", authenticated.accessToken)
        assertEquals(900L, authenticated.expiresIn)
        assertEquals("at_123", storage.access)
        // 既定の nowMillis = { 0L } では expiresAt は null になる（時刻提供は呼び出し側の責務）。
        assertNull(storage.expiresAt)

        // 送信先 URL が `/v1/auth/login` であること。
        val request = engine.requestHistory.single()
        assertTrue(request.url.encodedPath.endsWith("/v1/auth/login"))
    }

    @Test
    fun login_with_explicit_clock_records_expires_at() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """{"access_token":"at","token_type":"Bearer","expires_in":900}""",
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val storage = FakeTokenStorage()
        val repo = AuthRepository(
            authApi = AuthApi(
                client = httpClient(engine),
                authCoreBaseUrl = "https://authcore.example.test",
            ),
            tokenStorage = storage,
            nowMillis = { 1_000_000L },
        )

        repo.login(identifier = "u", password = "p")

        val expiresAt = assertNotNull(storage.expiresAt)
        assertEquals(1_000_000L + 900L * 1000L, expiresAt)
    }

    @Test
    fun login_with_mfa_required_returns_needs_mfa_and_does_not_save_access() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "pre_token": "pt_xyz",
                      "mfa_required": true,
                      "token_type": "Bearer",
                      "expires_in": 600
                    }
                    """.trimIndent(),
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val storage = FakeTokenStorage()
        val repo = repository(engine, storage)

        val result = repo.login(identifier = "user@example.test", password = "secret")

        val success = assertIs<NetworkResult.Success<LoginResult>>(result)
        val needsMfa = assertIs<LoginResult.NeedsMfa>(success.value)
        assertEquals("pt_xyz", needsMfa.preToken)
        assertEquals(600L, needsMfa.expiresIn)
        assertNull(storage.access)
    }

    @Test
    fun login_invalid_credentials_returns_failure() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """{"error":{"code":"INVALID_CREDENTIALS","message":"bad creds"}}""",
                ),
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val storage = FakeTokenStorage()
        val repo = repository(engine, storage)

        val result = repo.login(identifier = "u", password = "p")

        val failure = assertIs<NetworkResult.Failure>(result)
        assertEquals(ApiErrorCode.INVALID_CREDENTIALS, failure.error.code)
        assertNull(storage.access)
    }

    @Test
    fun verifyMfa_success_saves_access_and_returns_unit() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """{"access_token":"at_mfa","token_type":"Bearer","expires_in":900}""",
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val storage = FakeTokenStorage()
        val repo = repository(engine, storage)

        val result = repo.verifyMfa(preToken = "pt", code = "123456")

        assertIs<NetworkResult.Success<Unit>>(result)
        assertEquals("at_mfa", storage.access)

        val request = engine.requestHistory.single()
        assertTrue(request.url.encodedPath.endsWith("/v1/auth/mfa/verify"))
        assertEquals("Bearer pt", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun refresh_sends_cookie_and_updates_access() = runTest {
        // 1 回目: login 風に Set-Cookie で refresh_token を仕込む。
        // 2 回目: /v1/auth/refresh の応答で新 access を返す。Cookie が付いてくるかも併せて検証。
        var call = 0
        val engine = MockEngine { request ->
            call += 1
            when (call) {
                1 -> respond(
                    content = ByteReadChannel(
                        """{"access_token":"at_old","token_type":"Bearer","expires_in":900}""",
                    ),
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ContentType to listOf("application/json"),
                        HttpHeaders.SetCookie to listOf(
                            "refresh_token=rt_persisted; Path=/v1/auth; HttpOnly; Max-Age=2592000",
                        ),
                    ),
                )
                else -> respond(
                    content = ByteReadChannel(
                        """{"access_token":"at_new","token_type":"Bearer","expires_in":900}""",
                    ),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val storage = FakeTokenStorage()
        val repo = repository(engine, storage)

        val loginResult = repo.login(identifier = "u", password = "p")
        assertIs<NetworkResult.Success<LoginResult>>(loginResult)

        val refreshResult = repo.refresh()
        assertIs<NetworkResult.Success<Unit>>(refreshResult)
        assertEquals("at_new", storage.access)

        // /v1/auth/refresh リクエストに refresh_token cookie が積まれていること。
        val refreshRequest = engine.requestHistory[1]
        assertTrue(refreshRequest.url.encodedPath.endsWith("/v1/auth/refresh"))
        val cookieHeader = refreshRequest.headers[HttpHeaders.Cookie]
        assertNotNull(cookieHeader, "Cookie header must be present on /v1/auth/refresh")
        assertTrue(
            cookieHeader.contains("refresh_token=rt_persisted"),
            "expected refresh_token cookie, got: $cookieHeader",
        )
    }

    @Test
    fun logout_clears_storage_even_after_server_success() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.NoContent) }
        val storage = FakeTokenStorage().apply {
            access = "at"
            expiresAt = 1L
        }
        val repo = repository(engine, storage)

        val result = repo.logout()
        assertIs<NetworkResult.Success<Unit>>(result)
        assertEquals(1, storage.clearCalls)
        assertNull(storage.access)
    }

    @Test
    fun isAuthenticated_reflects_access_token_presence() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.OK) }
        val storage = FakeTokenStorage()
        val repo = repository(engine, storage)

        assertFalse(repo.isAuthenticated())

        storage.access = "at"
        assertTrue(repo.isAuthenticated())
    }
}
