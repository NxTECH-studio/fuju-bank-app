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
import studio.nxtech.fujubank.data.repository.AuthRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class TokenExpiryWatcherTest {

    /** TokenStorage の挙動を細かく検証するための fake。clear 回数も数える。 */
    private class FakeTokenStorage(
        initialAccess: String? = null,
        initialExpiresAt: Long? = null,
    ) : TokenStorage {
        var access: String? = initialAccess
        var expiresAt: Long? = initialExpiresAt
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
                url(BASE_URL)
            }
        }

    private fun watcher(
        engine: MockEngine,
        storage: TokenStorage,
        sessionStore: SessionStore,
        nowMillis: () -> Long = { 0L },
        threshold: Long = TokenExpiryWatcher.DEFAULT_REFRESH_THRESHOLD_MS,
    ): TokenExpiryWatcher = TokenExpiryWatcher(
        authRepository = AuthRepository(
            authApi = AuthApi(
                client = httpClient(engine),
                authCoreBaseUrl = BASE_URL,
            ),
            tokenStorage = storage,
            nowMillis = nowMillis,
        ),
        tokenStorage = storage,
        sessionStore = sessionStore,
        nowMillis = nowMillis,
        refreshThresholdMillis = threshold,
    )

    companion object {
        private const val BASE_URL = "https://authcore.example.test"
    }

    @Test
    fun checkNow_doesNothing_when_unauthenticated() = runTest {
        val engine = MockEngine { error("refresh should not be called when Unauthenticated") }
        val storage = FakeTokenStorage(initialAccess = "at_old", initialExpiresAt = 100L)
        val store = SessionStore() // 初期値は Unauthenticated。
        val watcher = watcher(engine, storage, store, nowMillis = { 1_000L })

        watcher.checkNow()

        // refresh が走らないので requestHistory は空、access も書き換わらない。
        assertEquals(0, engine.requestHistory.size)
        assertEquals("at_old", storage.access)
        assertEquals(0, storage.clearCalls)
    }

    @Test
    fun checkNow_doesNothing_when_expiresAt_is_null() = runTest {
        val engine = MockEngine { error("refresh should not be called when expiresAt is null") }
        val storage = FakeTokenStorage(initialAccess = "at_legacy", initialExpiresAt = null)
        val store = SessionStore().apply { setAuthenticated("u1") }
        val watcher = watcher(engine, storage, store, nowMillis = { 1_000L })

        watcher.checkNow()

        assertEquals(0, engine.requestHistory.size)
        assertEquals("at_legacy", storage.access)
        assertEquals(0, storage.clearCalls)
    }

    @Test
    fun checkNow_doesNothing_when_expiresAt_is_far_in_future() = runTest {
        val engine = MockEngine { error("refresh should not be called when expiresAt is far") }
        // now=1000, expiresAt=10000, threshold=60_000 → 1000 < 10000 - 60000 = -50000 は false。
        // ただし 1000 < (10000 - 60000) は 1000 < -50000 で false なので「閾値内」と誤判定する。
        // → expiresAt を threshold より十分大きく取る必要がある。expiresAt=200_000 にする。
        val storage = FakeTokenStorage(initialAccess = "at_fresh", initialExpiresAt = 200_000L)
        val store = SessionStore().apply { setAuthenticated("u1") }
        val watcher = watcher(engine, storage, store, nowMillis = { 1_000L })

        watcher.checkNow()

        assertEquals(0, engine.requestHistory.size)
        assertEquals("at_fresh", storage.access)
    }

    @Test
    fun checkNow_refreshes_when_within_threshold() = runTest {
        // now=1000, expiresAt=30_000, threshold=60_000 → 1000 >= -30000 → 閾値内。
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """{"access_token":"at_new","token_type":"Bearer","expires_in":900}""",
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val storage = FakeTokenStorage(initialAccess = "at_old", initialExpiresAt = 30_000L)
        val store = SessionStore().apply { setAuthenticated("u1") }
        val watcher = watcher(engine, storage, store, nowMillis = { 1_000L })

        watcher.checkNow()

        // refresh が走り、新 access が保存されている。
        assertEquals(1, engine.requestHistory.size)
        assertEquals("at_new", storage.access)
        assertNotEquals(0L, storage.expiresAt) // 新 expiresAt が設定された。
        assertEquals(0, storage.clearCalls)
    }

    @Test
    fun checkNow_invalidates_session_when_refresh_returns_api_error() = runTest {
        // refresh_family が revoke されたケース。401 + AuthCore のフラットエラー envelope。
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """{"error":"TOKEN_REVOKED","message":"refresh family revoked"}""",
                ),
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val storage = FakeTokenStorage(initialAccess = "at_old", initialExpiresAt = 30_000L)
        val store = SessionStore().apply { setAuthenticated("u1") }
        val watcher = watcher(engine, storage, store, nowMillis = { 1_000L })

        watcher.checkNow()

        // tokenStorage.clear() と sessionStore.clear() の両方が呼ばれて Unauthenticated。
        assertEquals(1, storage.clearCalls)
        assertNull(storage.access)
        assertEquals(SessionState.Unauthenticated, store.current)
    }

    @Test
    fun checkNow_does_not_clear_on_network_failure() = runTest {
        // 通信エラー（圏外 / DNS / タイムアウト）。MockEngine の例外で `NetworkResult.NetworkFailure`
        // 経路を踏ませる。圏外復帰時に毎回ログアウトされる UX を避けるため clear しないこと。
        val engine = MockEngine { error("simulated network failure") }
        val storage = FakeTokenStorage(initialAccess = "at_old", initialExpiresAt = 30_000L)
        val store = SessionStore().apply { setAuthenticated("u1") }
        val watcher = watcher(engine, storage, store, nowMillis = { 1_000L })

        watcher.checkNow()

        // 一時障害扱いで session も storage も維持。
        assertEquals(0, storage.clearCalls)
        assertEquals("at_old", storage.access)
        assertEquals(SessionState.Authenticated("u1"), store.current)
    }
}
