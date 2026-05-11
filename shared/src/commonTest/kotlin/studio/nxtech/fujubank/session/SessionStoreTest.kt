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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import studio.nxtech.fujubank.auth.TokenStorage
import studio.nxtech.fujubank.data.remote.api.AuthApi
import studio.nxtech.fujubank.data.remote.api.UserApi
import studio.nxtech.fujubank.data.remote.api.UserMeApi
import studio.nxtech.fujubank.data.remote.api.UserSearchApi
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

        // SessionStore.userId は AuthCore の ULID (= bank の external_user_id) を源泉とするため、
        // テストでも ULID 体裁 (Crockford Base32 26 文字) で渡す。
        store.setAuthenticated("01HZX1A2B3C4D5E6F7G8H9JKM1")
        val auth = assertIs<SessionState.Authenticated>(store.current)
        assertEquals("01HZX1A2B3C4D5E6F7G8H9JKM1", auth.userId)

        store.clear()
        assertEquals(SessionState.Unauthenticated, store.current)
    }

    @Test
    fun bootstrap_with_existing_access_calls_getMe_and_authenticates() = runTest {
        // access あり → getMe が呼ばれる。SessionStore は `UserResponse.sub` (= AuthCore ULID) を
        // userId 源泉とするため、フィクスチャに `sub` を含める。
        val engine = MockEngine { request ->
            assertEquals("/users/me", request.url.encodedPath)
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "id": 42,
                      "sub": "01HZX1A2B3C4D5E6F7G8H9JKM1",
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
        // 1 つの HttpClient を AuthApi / UserApi / UserMeApi で共有する。
        // これにより HttpCookies の CookiesStorage も実プロダクト同様に共有される。
        val client = httpClient(engine)
        val authRepo = AuthRepository(
            authApi = AuthApi(client = client, authCoreBaseUrl = "https://authcore.test"),
            tokenStorage = storage,
        )
        val userRepo = UserRepository(
            userApi = UserApi(client),
            userMeApi = UserMeApi(client),
            userSearchApi = UserSearchApi(client),
            sessionStore = SessionStore(),
        )

        val store = SessionStore()
        store.bootstrap(authRepo, userRepo)

        val auth = assertIs<SessionState.Authenticated>(store.current)
        assertEquals("01HZX1A2B3C4D5E6F7G8H9JKM1", auth.userId)
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
                          "id": 43,
                          "sub": "01HZX1A2B3C4D5E6F7G8H9JKM2",
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
        val client = httpClient(engine)
        val authRepo = AuthRepository(
            authApi = AuthApi(client = client, authCoreBaseUrl = "https://authcore.test"),
            tokenStorage = storage,
        )
        val userRepo = UserRepository(
            userApi = UserApi(client),
            userMeApi = UserMeApi(client),
            userSearchApi = UserSearchApi(client),
            sessionStore = SessionStore(),
        )

        val store = SessionStore()
        store.bootstrap(authRepo, userRepo)

        val auth = assertIs<SessionState.Authenticated>(store.current)
        assertEquals("01HZX1A2B3C4D5E6F7G8H9JKM2", auth.userId)
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
        val client = httpClient(engine)
        val authRepo = AuthRepository(
            authApi = AuthApi(client = client, authCoreBaseUrl = "https://authcore.test"),
            tokenStorage = storage,
        )
        val userRepo = UserRepository(
            userApi = UserApi(client),
            userMeApi = UserMeApi(client),
            userSearchApi = UserSearchApi(client),
            sessionStore = SessionStore(),
        )

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
        val client = httpClient(engine)
        val authRepo = AuthRepository(
            authApi = AuthApi(client = client, authCoreBaseUrl = "https://authcore.test"),
            tokenStorage = storage,
        )
        val userRepo = UserRepository(
            userApi = UserApi(client),
            userMeApi = UserMeApi(client),
            userSearchApi = UserSearchApi(client),
            sessionStore = SessionStore(),
        )

        val store = SessionStore()
        store.bootstrap(authRepo, userRepo)

        assertEquals(SessionState.Unauthenticated, store.current)
    }

    // ---------- 追加: state 遷移 ----------

    @Test
    fun state_can_loop_between_authenticated_and_unauthenticated() {
        // 一度ログイン → ログアウト → 再ログインのループが通ること。
        // userId は ULID 体裁。
        val store = SessionStore()
        store.setAuthenticated("01HZX1A2B3C4D5E6F7G8H9JKM1")
        assertIs<SessionState.Authenticated>(store.current)

        store.clear()
        assertEquals(SessionState.Unauthenticated, store.current)

        store.setAuthenticated("01HZX1A2B3C4D5E6F7G8H9JKM2")
        val auth2 = assertIs<SessionState.Authenticated>(store.current)
        assertEquals("01HZX1A2B3C4D5E6F7G8H9JKM2", auth2.userId)
    }

    @Test
    fun mfa_pending_can_be_cancelled_back_to_unauthenticated() {
        // MFA 入力画面でユーザがキャンセルしたとき、Unauthenticated に戻れること。
        val store = SessionStore()
        store.setMfaPending("pt_x")
        assertIs<SessionState.MfaPending>(store.current)

        store.clear()
        assertEquals(SessionState.Unauthenticated, store.current)
    }

    @Test
    fun setMfaPending_overwrites_previous_pre_token() {
        // 同じ識別子で再 login したときに古い preToken が残らないこと。
        val store = SessionStore()
        store.setMfaPending("pt_old")
        store.setMfaPending("pt_new")
        val mfa = assertIs<SessionState.MfaPending>(store.current)
        assertEquals("pt_new", mfa.preToken)
    }

    // ---------- 追加: state Flow の観測 ----------

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun state_flow_emits_each_transition_in_order() = runTest {
        // 状態遷移が StateFlow に順序通り反映されること。UI が collectAsState() で
        // 受けるのと同じ経路。
        // backgroundScope + UnconfinedTestDispatcher なら collect は emission 直後に
        // 走るので yield() なしで同期できる。runTest 終了時に backgroundScope は
        // 自動でキャンセルされるため job 管理も不要。
        val store = SessionStore()
        val emitted = mutableListOf<SessionState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            store.state.collect { emitted += it }
        }

        store.setMfaPending("pt")
        store.setAuthenticated("01HZX1A2B3C4D5E6F7G8H9JKM1")
        store.clear()

        assertEquals(
            listOf(
                SessionState.Unauthenticated,
                SessionState.MfaPending("pt"),
                SessionState.Authenticated("01HZX1A2B3C4D5E6F7G8H9JKM1"),
                SessionState.Unauthenticated,
            ),
            emitted,
        )
    }

    // ---------- 追加: bootstrap 二重起動防止 ----------

    @Test
    fun bootstrap_called_twice_does_not_re_request() = runTest {
        // 二度目の bootstrap は即 return する。getMe を 2 回叩かないこと。
        var getMeCalls = 0
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/users/me") getMeCalls += 1
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "id": 7,
                      "sub": "01HZX1A2B3C4D5E6F7G8H9JKM3",
                      "balance_fuju": 0,
                      "created_at": "2026-04-21T12:34:56Z"
                    }
                    """.trimIndent(),
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val storage = FakeTokenStorage(initialAccess = "at_old")
        val client = httpClient(engine)
        val authRepo = AuthRepository(
            authApi = AuthApi(client = client, authCoreBaseUrl = "https://authcore.test"),
            tokenStorage = storage,
        )
        val userRepo = UserRepository(
            userApi = UserApi(client),
            userMeApi = UserMeApi(client),
            userSearchApi = UserSearchApi(client),
            sessionStore = SessionStore(),
        )

        val store = SessionStore()
        store.bootstrap(authRepo, userRepo)
        store.bootstrap(authRepo, userRepo)

        assertEquals(1, getMeCalls)
        assertEquals(true, store.bootstrapped.value)
    }

    @Test
    fun bootstrap_called_concurrently_does_not_re_request() = runTest {
        // 並行起動でも getMe が 1 回しか走らないこと。
        // SessionStore.bootstrapMutex.withLock { if (bootstrapStarted) return } の
        // 並行保証（race-free な二重起動防止）を直接検証する。
        var getMeCalls = 0
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/users/me") getMeCalls += 1
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "id": 9,
                      "sub": "01HZX1A2B3C4D5E6F7G8H9JKM4",
                      "balance_fuju": 0,
                      "created_at": "2026-04-21T12:34:56Z"
                    }
                    """.trimIndent(),
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val storage = FakeTokenStorage(initialAccess = "at_old")
        val client = httpClient(engine)
        val authRepo = AuthRepository(
            authApi = AuthApi(client = client, authCoreBaseUrl = "https://authcore.test"),
            tokenStorage = storage,
        )
        val userRepo = UserRepository(
            userApi = UserApi(client),
            userMeApi = UserMeApi(client),
            userSearchApi = UserSearchApi(client),
            sessionStore = SessionStore(),
        )

        val store = SessionStore()
        val jobs = listOf(
            launch { store.bootstrap(authRepo, userRepo) },
            launch { store.bootstrap(authRepo, userRepo) },
        )
        jobs.joinAll()

        assertEquals(1, getMeCalls)
        assertEquals(true, store.bootstrapped.value)
    }

    @Test
    fun bootstrap_marks_bootstrapped_true_even_on_failure() = runTest {
        // 失敗ルートでも bootstrapped は true に遷移する（Splash 解除のため）。
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """{"error":{"code":"TOKEN_REVOKED","message":"x"}}""",
                ),
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val storage = FakeTokenStorage(initialAccess = null)
        val client = httpClient(engine)
        val authRepo = AuthRepository(
            authApi = AuthApi(client = client, authCoreBaseUrl = "https://authcore.test"),
            tokenStorage = storage,
        )
        val userRepo = UserRepository(
            userApi = UserApi(client),
            userMeApi = UserMeApi(client),
            userSearchApi = UserSearchApi(client),
            sessionStore = SessionStore(),
        )

        val store = SessionStore()
        assertEquals(false, store.bootstrapped.value)
        store.bootstrap(authRepo, userRepo)
        assertEquals(true, store.bootstrapped.value)
        assertEquals(SessionState.Unauthenticated, store.current)
    }
}
