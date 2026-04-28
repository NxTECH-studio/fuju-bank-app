package studio.nxtech.fujubank.session

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import studio.nxtech.fujubank.auth.TokenStorage
import studio.nxtech.fujubank.data.remote.api.AuthApi
import studio.nxtech.fujubank.data.remote.api.UserApi
import studio.nxtech.fujubank.data.remote.api.UserMeApi
import studio.nxtech.fujubank.data.repository.AuthRepository
import studio.nxtech.fujubank.data.repository.UserRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SessionStoreTest {

    private class FakeTokenStorage(initialAccess: String? = null) : TokenStorage {
        var access: String? = initialAccess
        var expiresAt: Long? = null

        override suspend fun loadAccess(): String? = access

        override suspend fun loadExpiresAt(): Long? = expiresAt

        override suspend fun saveAccess(token: String, expiresAt: Long?) {
            this.access = token
            this.expiresAt = expiresAt
        }

        override suspend fun clear() {
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
            if (withCookies) install(HttpCookies)
            defaultRequest {
                url("https://example.test")
            }
        }

    @Test
    fun setters_drive_state_transitions() {
        val store = SessionStore()
        assertEquals(SessionState.Unauthenticated, store.current)

        store.setMfaPending("pt_123")
        val mfa = assertIs<SessionState.MfaPending>(store.current)
        assertEquals("pt_123", mfa.preToken)

        store.setAuthenticated("usr_1")
        val auth = assertIs<SessionState.Authenticated>(store.current)
        assertEquals("usr_1", auth.userId)

        store.clear()
        assertEquals(SessionState.Unauthenticated, store.current)
    }

    @Test
    fun bootstrap_with_existing_access_calls_getMe_and_authenticates() = runTest {
        // access あり → getMe が呼ばれる。
        val engine = MockEngine { request ->
            assertEquals("/users/me", request.url.encodedPath)
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "id": "usr_existing",
                      "sub": "s",
                      "balance_fuju": 100,
                      "created_at": "2026-04-21T12:34:56Z"
                    }
                    """.trimIndent(),
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val storage = FakeTokenStorage(initialAccess = "at_old")
        val authRepo = AuthRepository(
            authApi = AuthApi(client = httpClient(engine), authCoreBaseUrl = "https://authcore.test"),
            tokenStorage = storage,
        )
        val userRepo = UserRepository(UserApi(httpClient(engine)), UserMeApi(httpClient(engine)))

        val store = SessionStore()
        store.bootstrap(authRepo, userRepo)

        val auth = assertIs<SessionState.Authenticated>(store.current)
        assertEquals("usr_existing", auth.userId)
    }

    @Test
    fun bootstrap_without_access_tries_refresh_then_getMe() = runTest {
        // 1 回目: refresh → 200, 2 回目: getMe → 200。
        var call = 0
        val engine = MockEngine { request ->
            call += 1
            when (call) {
                1 -> {
                    // refresh
                    respond(
                        content = ByteReadChannel(
                            """{"access_token":"at_refreshed","token_type":"Bearer","expires_in":900}""",
                        ),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                else -> respond(
                    content = ByteReadChannel(
                        """
                        {
                          "id": "usr_refreshed",
                          "sub": "s",
                          "balance_fuju": 0,
                          "created_at": "2026-04-21T12:34:56Z"
                        }
                        """.trimIndent(),
                    ),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val storage = FakeTokenStorage(initialAccess = null)
        val authRepo = AuthRepository(
            authApi = AuthApi(client = httpClient(engine), authCoreBaseUrl = "https://authcore.test"),
            tokenStorage = storage,
        )
        val userRepo = UserRepository(UserApi(httpClient(engine)), UserMeApi(httpClient(engine)))

        val store = SessionStore()
        store.bootstrap(authRepo, userRepo)

        val auth = assertIs<SessionState.Authenticated>(store.current)
        assertEquals("usr_refreshed", auth.userId)
        assertEquals("at_refreshed", storage.access)
    }

    @Test
    fun bootstrap_without_access_and_refresh_failure_stays_unauthenticated() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """{"error":{"code":"TOKEN_REVOKED","message":"revoked"}}""",
                ),
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val storage = FakeTokenStorage(initialAccess = null)
        val authRepo = AuthRepository(
            authApi = AuthApi(client = httpClient(engine), authCoreBaseUrl = "https://authcore.test"),
            tokenStorage = storage,
        )
        val userRepo = UserRepository(UserApi(httpClient(engine)), UserMeApi(httpClient(engine)))

        val store = SessionStore()
        store.bootstrap(authRepo, userRepo)

        assertEquals(SessionState.Unauthenticated, store.current)
    }

    @Test
    fun bootstrap_getMe_failure_clears_to_unauthenticated() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """{"error":{"code":"UNAUTHENTICATED","message":"expired"}}""",
                ),
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val storage = FakeTokenStorage(initialAccess = "at_old")
        val authRepo = AuthRepository(
            authApi = AuthApi(client = httpClient(engine), authCoreBaseUrl = "https://authcore.test"),
            tokenStorage = storage,
        )
        val userRepo = UserRepository(UserApi(httpClient(engine)), UserMeApi(httpClient(engine)))

        val store = SessionStore()
        store.bootstrap(authRepo, userRepo)

        assertEquals(SessionState.Unauthenticated, store.current)
    }
}
