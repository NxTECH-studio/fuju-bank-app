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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import studio.nxtech.fujubank.auth.TokenStorage
import studio.nxtech.fujubank.data.remote.ApiErrorCode
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.remote.api.AuthApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    private fun repository(
        engine: MockEngine,
        storage: TokenStorage,
        baseUrl: String = BASE_URL,
    ): AuthRepository = AuthRepository(
        authApi = AuthApi(
            client = httpClient(engine),
            authCoreBaseUrl = baseUrl,
        ),
        tokenStorage = storage,
    )

    companion object {
        private const val BASE_URL = "https://authcore.example.test"
    }

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
                authCoreBaseUrl = BASE_URL,
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
        // AuthCore は本番でフラット形式 `{"error":"CODE","message":"..."}` を返すため
        // bank API のネスト形式とは別経路。両形式を受け入れることを確認する。
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """{"error":"INVALID_CREDENTIALS","message":"invalid credentials"}""",
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
        assertEquals("invalid credentials", failure.error.message)
        assertNull(storage.access)
    }

    @Test
    fun login_bank_style_nested_error_envelope_also_works() = runTest {
        // bank API スタイルのネスト形式 `{"error":{"code":"...","message":"..."}}` も
        // 引き続き受け入れる。
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
        assertEquals("bad creds", failure.error.message)
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
        assertNull(storage.expiresAt)
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

    // ---------- 追加: ログイン失敗系のエラーコード網羅 ----------

    @Test
    fun login_account_locked_returns_failure_without_saving_access() = runTest {
        // AuthCore §3.3: ACCOUNT_LOCKED は 429 で返る想定（連続失敗でロック）。
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """{"error":"ACCOUNT_LOCKED","message":"too many attempts"}""",
                ),
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val storage = FakeTokenStorage()
        val repo = repository(engine, storage)

        val result = repo.login(identifier = "u", password = "p")
        val failure = assertIs<NetworkResult.Failure>(result)
        assertEquals(ApiErrorCode.ACCOUNT_LOCKED, failure.error.code)
        assertNull(storage.access)
    }

    @Test
    fun login_rate_limit_exceeded_returns_failure() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """{"error":"RATE_LIMIT_EXCEEDED","message":"slow down"}""",
                ),
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val storage = FakeTokenStorage()
        val repo = repository(engine, storage)

        val result = repo.login(identifier = "u", password = "p")
        val failure = assertIs<NetworkResult.Failure>(result)
        assertEquals(ApiErrorCode.RATE_LIMIT_EXCEEDED, failure.error.code)
    }

    @Test
    fun login_unknown_error_code_falls_back_to_unknown() = runTest {
        // 未知のサーバ側コード文字列が来ても UNKNOWN にフォールバックして
        // 呼び出し側を落とさない。
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """{"error":"FUTURE_FAILURE_MODE","message":"never seen"}""",
                ),
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val storage = FakeTokenStorage()
        val repo = repository(engine, storage)

        val result = repo.login(identifier = "u", password = "p")
        val failure = assertIs<NetworkResult.Failure>(result)
        assertEquals(ApiErrorCode.UNKNOWN, failure.error.code)
        assertEquals(500, failure.error.httpStatus)
    }

    @Test
    fun login_network_failure_when_engine_throws() = runTest {
        // オフライン等の通信レイヤー例外。NetworkFailure に落ち、storage は変化しない。
        val engine = MockEngine {
            throw RuntimeException("offline")
        }
        val storage = FakeTokenStorage()
        val repo = repository(engine, storage)

        val result = repo.login(identifier = "u", password = "p")
        assertIs<NetworkResult.NetworkFailure>(result)
        assertNull(storage.access)
    }

    @Test
    fun login_propagates_cancellation_instead_of_swallowing() = runTest {
        // 協調キャンセルの再 throw を検証する。`runCatchingNetwork` は
        // CancellationException を NetworkFailure に握り潰さず再 throw する責務がある。
        // 将来 `runCatching { ... }` 等で素朴に書き換えてキャンセル協調が壊れたら
        // ここで気づけるようにする。
        val engine = MockEngine {
            throw CancellationException("simulated cancellation")
        }
        val storage = FakeTokenStorage()
        val repo = repository(engine, storage)

        assertFailsWith<CancellationException> {
            repo.login(identifier = "u", password = "p")
        }
        assertNull(storage.access)
    }

    // ---------- 追加: トークン期限の境界値 ----------

    @Test
    fun login_with_zero_expiresIn_records_now_as_expiresAt() = runTest {
        // expires_in = 0 の境界。`expiresAt = now + 0` が記録され、null フォールバック
        // しないことを保証する（now > 0 のとき）。
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """{"access_token":"at","token_type":"Bearer","expires_in":0}""",
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val storage = FakeTokenStorage()
        val repo = AuthRepository(
            authApi = AuthApi(
                client = httpClient(engine),
                authCoreBaseUrl = BASE_URL,
            ),
            tokenStorage = storage,
            nowMillis = { 5_000L },
        )

        repo.login(identifier = "u", password = "p")
        assertEquals(5_000L, storage.expiresAt)
    }

    @Test
    fun login_with_now_zero_keeps_expiresAt_null() = runTest {
        // nowMillis が 0 を返す（時刻が取得できない状況）では null にフォールバック。
        // 呼び出し側で「期限不明」として扱える。
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
                authCoreBaseUrl = BASE_URL,
            ),
            tokenStorage = storage,
            nowMillis = { 0L },
        )

        repo.login(identifier = "u", password = "p")
        assertNull(storage.expiresAt)
    }

    // ---------- 追加: refresh 異常系 ----------

    @Test
    fun refresh_failure_with_token_revoked_returns_failure_and_keeps_access() = runTest {
        // AuthRepository.refresh() は失敗時に access を上書き保存しない。
        // tokenStorage.clear() を呼ぶ責務は呼び出し側 (createAuthTokenRefresher) に閉じている。
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """{"error":"TOKEN_REVOKED","message":"family revoked"}""",
                ),
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val storage = FakeTokenStorage().apply { access = "at_old" }
        val repo = repository(engine, storage)

        val result = repo.refresh()
        val failure = assertIs<NetworkResult.Failure>(result)
        assertEquals(ApiErrorCode.TOKEN_REVOKED, failure.error.code)
        // refresh 単体では clear しない（呼び出し側責務）。
        assertEquals(0, storage.clearCalls)
        assertEquals("at_old", storage.access)
    }

    @Test
    fun refresh_network_failure_does_not_change_storage() = runTest {
        val engine = MockEngine { throw RuntimeException("offline") }
        val storage = FakeTokenStorage().apply { access = "at_old" }
        val repo = repository(engine, storage)

        val result = repo.refresh()
        assertIs<NetworkResult.NetworkFailure>(result)
        assertEquals("at_old", storage.access)
        assertEquals(0, storage.clearCalls)
    }

    @Test
    fun refresh_success_overwrites_access_and_records_expiresAt() = runTest {
        // refresh で新しい access が入ったとき expiresAt も更新されること。
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """{"access_token":"at_new","token_type":"Bearer","expires_in":60}""",
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val storage = FakeTokenStorage().apply {
            access = "at_old"
            expiresAt = 1L
        }
        val repo = AuthRepository(
            authApi = AuthApi(
                client = httpClient(engine),
                authCoreBaseUrl = BASE_URL,
            ),
            tokenStorage = storage,
            nowMillis = { 2_000L },
        )

        val result = repo.refresh()
        assertIs<NetworkResult.Success<Unit>>(result)
        assertEquals("at_new", storage.access)
        assertEquals(2_000L + 60L * 1_000L, storage.expiresAt)
    }

    // ---------- 追加: logout 異常系 ----------

    @Test
    fun logout_clears_storage_even_when_server_fails() = runTest {
        // サーバ側 5xx でも、ローカルの access はクリアされる。これにより
        // ユーザがログアウトしたつもりで access が残る事故を防ぐ。
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """{"error":"INTERNAL","message":"boom"}""",
                ),
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val storage = FakeTokenStorage().apply {
            access = "at"
            expiresAt = 1L
        }
        val repo = repository(engine, storage)

        val result = repo.logout()
        assertIs<NetworkResult.Failure>(result)
        // failure でもローカル clear は走る。
        assertEquals(1, storage.clearCalls)
        assertNull(storage.access)
        assertNull(storage.expiresAt)
    }

    @Test
    fun logout_clears_storage_even_when_engine_throws() = runTest {
        val engine = MockEngine { throw RuntimeException("offline") }
        val storage = FakeTokenStorage().apply {
            access = "at"
            expiresAt = 1L
        }
        val repo = repository(engine, storage)

        val result = repo.logout()
        assertIs<NetworkResult.NetworkFailure>(result)
        assertEquals(1, storage.clearCalls)
        assertNull(storage.access)
        assertNull(storage.expiresAt)
    }

    // ---------- 追加: MFA verify 失敗系 ----------

    @Test
    fun verifyMfa_invalid_code_returns_failure_and_does_not_save_access() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """{"error":"TOTP_CODE_INVALID","message":"wrong code"}""",
                ),
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val storage = FakeTokenStorage()
        val repo = repository(engine, storage)

        val result = repo.verifyMfa(preToken = "pt", code = "000000")
        val failure = assertIs<NetworkResult.Failure>(result)
        assertEquals(ApiErrorCode.TOTP_CODE_INVALID, failure.error.code)
        assertNull(storage.access)
    }

    @Test
    fun verifyMfa_pre_token_expired_returns_failure() = runTest {
        // pre_token の有効期限切れ。AuthCore は TOKEN_EXPIRED を返す。
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """{"error":"TOKEN_EXPIRED","message":"pre_token expired"}""",
                ),
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val storage = FakeTokenStorage()
        val repo = repository(engine, storage)

        val result = repo.verifyMfa(preToken = "pt", code = "123456")
        val failure = assertIs<NetworkResult.Failure>(result)
        assertEquals(ApiErrorCode.TOKEN_EXPIRED, failure.error.code)
        assertNull(storage.access)
    }

    @Test
    fun verifyMfa_with_recovery_code_sends_snake_case_field() = runTest {
        // recovery_code を渡したときのリクエストボディが snake_case (`recovery_code`) で
        // 送られること。`code` は null なら省略される（explicitNulls = false）。
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
        val repo = repository(engine, storage)

        val result = repo.verifyMfa(preToken = "pt", recoveryCode = "abcd-efgh")
        assertIs<NetworkResult.Success<Unit>>(result)
        assertEquals("at", storage.access)

        val request = engine.requestHistory.single()
        val bodyText = request.body.toByteArrayString()
        assertTrue(
            bodyText.contains(""""recovery_code":"abcd-efgh""""),
            "expected recovery_code field in body, got: $bodyText",
        )
        // null の code フィールドは送信されない。
        assertFalse(bodyText.contains(""""code""""), "unexpected `code` field in body: $bodyText")
    }

    @Test
    fun verifyMfa_records_expiresAt_with_explicit_clock() = runTest {
        // verifyMfa 成功時の expiresAt 計算も login と同じ式に従う。
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """{"access_token":"at","token_type":"Bearer","expires_in":120}""",
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val storage = FakeTokenStorage()
        val repo = AuthRepository(
            authApi = AuthApi(
                client = httpClient(engine),
                authCoreBaseUrl = BASE_URL,
            ),
            tokenStorage = storage,
            nowMillis = { 10_000L },
        )

        val result = repo.verifyMfa(preToken = "pt", code = "123456")
        assertIs<NetworkResult.Success<Unit>>(result)
        assertEquals(10_000L + 120L * 1_000L, storage.expiresAt)
    }

    // ---------- 追加: login Set-Cookie の取り扱い ----------

    @Test
    fun login_persists_refresh_cookie_for_subsequent_refresh() = runTest {
        // login で受け取った refresh_token cookie が同じ HttpClient の cookie storage に
        // 残り、後続の /v1/auth/refresh で送出されることを確認。
        var call = 0
        val engine = MockEngine { request ->
            call += 1
            when (call) {
                1 -> respond(
                    content = ByteReadChannel(
                        """{"access_token":"at_1","token_type":"Bearer","expires_in":900}""",
                    ),
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ContentType to listOf("application/json"),
                        // Path=/ にしておくと /v1/auth/refresh にも送出される（HttpCookies）。
                        HttpHeaders.SetCookie to listOf(
                            "refresh_token=rt_loose; Path=/; HttpOnly",
                        ),
                    ),
                )
                else -> respond(
                    content = ByteReadChannel(
                        """{"access_token":"at_2","token_type":"Bearer","expires_in":900}""",
                    ),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val storage = FakeTokenStorage()
        val repo = repository(engine, storage)

        repo.login(identifier = "u", password = "p")
        repo.refresh()

        val refreshRequest = engine.requestHistory[1]
        val cookieHeader = refreshRequest.headers[HttpHeaders.Cookie]
        assertNotNull(cookieHeader, "Cookie header expected on /v1/auth/refresh")
        assertTrue(
            cookieHeader.contains("refresh_token=rt_loose"),
            "expected refresh_token cookie, got: $cookieHeader",
        )
    }
}

// テスト便宜のためのリクエスト body 文字列化拡張。
// 想定外の OutgoingContent サブタイプが渡されたら空文字でごまかさず即座にテストを失敗させる。
// （SUT が body 構築方法を変えた場合のサイレントな黙殺を防ぐ。）
private suspend fun io.ktor.http.content.OutgoingContent.toByteArrayString(): String =
    when (this) {
        is io.ktor.http.content.TextContent -> text
        is io.ktor.http.content.ByteArrayContent -> bytes().decodeToString()
        else -> error("unexpected OutgoingContent type: $this")
    }
