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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import studio.nxtech.fujubank.auth.TokenStorage
import studio.nxtech.fujubank.data.remote.ApiErrorCode
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.remote.api.AuthApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthRepositoryTest {

    private class FakeTokenStorage : TokenStorage {
        var access: String? = null
        var refresh: String? = null
        var subject: String? = null
        var clearCalls: Int = 0

        override suspend fun getAccessToken(): String? = access

        override suspend fun getRefreshToken(): String? = refresh

        override suspend fun getSubject(): String? = subject

        override suspend fun save(access: String, refresh: String, subject: String) {
            this.access = access
            this.refresh = refresh
            this.subject = subject
        }

        override suspend fun clear() {
            clearCalls += 1
            access = null
            refresh = null
            subject = null
        }
    }

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
            url("https://authcore.example.test")
        }
    }

    private fun repository(engine: MockEngine, storage: TokenStorage): AuthRepository =
        AuthRepository(
            authApi = AuthApi(client = httpClient(engine), authCoreBaseUrl = ""),
            tokenStorage = storage,
        )

    @Test
    fun login_success_saves_tokens_and_returns_success() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "access_token": "at_123",
                      "refresh_token": "rt_456",
                      "subject": "01HZZZZZZZZZZZZZZZZZZZZZZZ",
                      "expires_in": 3600
                    }
                    """.trimIndent(),
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val storage = FakeTokenStorage()
        val repo = repository(engine, storage)

        val result = repo.login(email = "user@example.test", password = "secret")

        assertIs<NetworkResult.Success<Unit>>(result)
        assertEquals("at_123", storage.access)
        assertEquals("rt_456", storage.refresh)
        assertEquals("01HZZZZZZZZZZZZZZZZZZZZZZZ", storage.subject)
    }

    @Test
    fun login_mfa_required_emits_event_and_does_not_save_tokens() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "error": {
                        "code": "MFA_REQUIRED",
                        "message": "mfa is required"
                      }
                    }
                    """.trimIndent(),
                ),
                status = HttpStatusCode.Forbidden,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val storage = FakeTokenStorage()
        val repo = repository(engine, storage)

        val eventAwaiter = async(start = CoroutineStart.UNDISPATCHED) {
            repo.mfaRequiredEvents.first()
        }

        val result = repo.login(email = "user@example.test", password = "secret")

        val failure = assertIs<NetworkResult.Failure>(result)
        assertEquals(ApiErrorCode.MFA_REQUIRED, failure.error.code)
        assertNull(storage.access)
        assertNull(storage.refresh)
        assertNull(storage.subject)

        withTimeout(1_000) { eventAwaiter.await() }
    }

    @Test
    fun login_validation_failure_returns_failure_without_event() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "error": {
                        "code": "VALIDATION_FAILED",
                        "message": "invalid email"
                      }
                    }
                    """.trimIndent(),
                ),
                status = HttpStatusCode.UnprocessableEntity,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val storage = FakeTokenStorage()
        val repo = repository(engine, storage)

        val result = repo.login(email = "bad", password = "x")

        val failure = assertIs<NetworkResult.Failure>(result)
        assertEquals(ApiErrorCode.VALIDATION_FAILED, failure.error.code)
        assertNull(storage.access)
    }

    @Test
    fun logout_clears_storage() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.OK) }
        val storage = FakeTokenStorage().apply {
            access = "at"
            refresh = "rt"
            subject = "sub"
        }
        val repo = repository(engine, storage)

        repo.logout()

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
