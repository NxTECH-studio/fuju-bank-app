package studio.nxtech.fujubank.di

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
import studio.nxtech.fujubank.data.repository.AuthRepository
import studio.nxtech.fujubank.session.SessionState
import studio.nxtech.fujubank.session.SessionStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AuthTokenRefresherTest {

    private class FakeTokenStorage(initialAccess: String? = null) : TokenStorage {
        var access: String? = initialAccess
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

    private fun httpClient(engine: MockEngine): HttpClient =
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
            install(HttpCookies)
            defaultRequest {
                url("https://authcore.example.test")
            }
        }

    private fun authRepository(engine: MockEngine, storage: TokenStorage): AuthRepository =
        AuthRepository(
            authApi = AuthApi(
                client = httpClient(engine),
                authCoreBaseUrl = "https://authcore.example.test",
            ),
            tokenStorage = storage,
        )

    @Test
    fun refresh_success_returns_new_access_and_keeps_session_authenticated() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """{"access_token":"at_new","token_type":"Bearer","expires_in":900}""",
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val storage = FakeTokenStorage(initialAccess = "at_old")
        val sessionStore = SessionStore().apply { setAuthenticated(userId = "usr_1", bankUserId = "1") }
        val refresher = createAuthTokenRefresher(
            authRepository = authRepository(engine, storage),
            tokenStorage = storage,
            sessionStore = sessionStore,
        )

        val token = refresher.refresh()

        assertEquals("at_new", token)
        assertEquals(0, storage.clearCalls)
        val auth = assertIs<SessionState.Authenticated>(sessionStore.current)
        assertEquals("usr_1", auth.userId)
    }

    @Test
    fun refresh_api_failure_clears_token_storage_and_session() = runTest {
        // refresh_family が revoke されたケース。HTTP 401 + TOKEN_REVOKED が返る。
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """{"error":"TOKEN_REVOKED","message":"refresh family revoked"}""",
                ),
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val storage = FakeTokenStorage(initialAccess = "at_old")
        val sessionStore = SessionStore().apply { setAuthenticated(userId = "usr_1", bankUserId = "1") }
        val refresher = createAuthTokenRefresher(
            authRepository = authRepository(engine, storage),
            tokenStorage = storage,
            sessionStore = sessionStore,
        )

        val token = refresher.refresh()

        assertNull(token)
        assertEquals(1, storage.clearCalls)
        assertNull(storage.access)
        assertEquals(SessionState.Unauthenticated, sessionStore.current)
    }

    @Test
    fun refresh_network_failure_keeps_session_and_storage_intact() = runTest {
        // 通信レイヤーで例外が起きた = NetworkFailure 経路。
        // ResponseException 以外の Throwable は runCatchingNetwork で NetworkFailure に落ちる。
        val engine = MockEngine {
            throw RuntimeException("offline")
        }
        val storage = FakeTokenStorage(initialAccess = "at_old")
        val sessionStore = SessionStore().apply { setAuthenticated(userId = "usr_1", bankUserId = "1") }
        val refresher = createAuthTokenRefresher(
            authRepository = authRepository(engine, storage),
            tokenStorage = storage,
            sessionStore = sessionStore,
        )

        val token = refresher.refresh()

        assertNull(token)
        assertEquals(0, storage.clearCalls)
        assertEquals("at_old", storage.access)
        val auth = assertIs<SessionState.Authenticated>(sessionStore.current)
        assertEquals("usr_1", auth.userId)
    }

    @Test
    fun refresh_invalid_refresh_token_clears_storage_and_session() = runTest {
        // INVALID_REFRESH_TOKEN（refresh cookie 自体が壊れている）でも
        // TOKEN_REVOKED と同様に Failure 扱いで clear が走る。
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """{"error":"TOKEN_INVALID","message":"refresh token invalid"}""",
                ),
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val storage = FakeTokenStorage(initialAccess = "at_old")
        val sessionStore = SessionStore().apply { setAuthenticated(userId = "usr_1", bankUserId = "1") }
        val refresher = createAuthTokenRefresher(
            authRepository = authRepository(engine, storage),
            tokenStorage = storage,
            sessionStore = sessionStore,
        )

        val token = refresher.refresh()
        assertNull(token)
        assertEquals(1, storage.clearCalls)
        assertNull(storage.access)
        assertEquals(SessionState.Unauthenticated, sessionStore.current)
    }

    @Test
    fun refresh_clears_token_storage_before_session_state_unauthenticated() = runTest {
        // tokenStorage.clear() が sessionStore.clear() より先に走ること。
        // UI が Unauthenticated を観測した時点で残存トークンが無いことを保証する。
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """{"error":"TOKEN_REVOKED","message":"x"}""",
                ),
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        // clear() が呼ばれた瞬間の sessionStore.current を記録する FakeTokenStorage。
        val sessionStore = SessionStore().apply { setAuthenticated(userId = "usr_1", bankUserId = "1") }
        val sessionStateAtClear = mutableListOf<SessionState>()
        val storage = object : TokenStorage {
            var access: String? = "at_old"
            var clearCalls: Int = 0

            override suspend fun loadAccess(): String? = access
            override suspend fun loadExpiresAt(): Long? = null
            override suspend fun saveAccess(token: String, expiresAt: Long?) {
                access = token
            }
            override suspend fun clear() {
                clearCalls += 1
                access = null
                // clear() が呼ばれた瞬間に sessionStore がまだ Authenticated であることを期待。
                sessionStateAtClear += sessionStore.current
            }
        }
        val refresher = createAuthTokenRefresher(
            authRepository = authRepository(engine, storage),
            tokenStorage = storage,
            sessionStore = sessionStore,
        )

        refresher.refresh()

        assertEquals(1, storage.clearCalls)
        assertEquals(
            listOf<SessionState>(SessionState.Authenticated(userId = "usr_1", bankUserId = "1")),
            sessionStateAtClear,
            "tokenStorage.clear() は sessionStore.clear() より先に呼ばれるべき",
        )
        assertEquals(SessionState.Unauthenticated, sessionStore.current)
    }
}
