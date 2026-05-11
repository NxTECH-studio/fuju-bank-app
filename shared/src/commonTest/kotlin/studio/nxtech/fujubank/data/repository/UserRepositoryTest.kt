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
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.remote.api.UserApi
import studio.nxtech.fujubank.data.remote.api.UserMeApi
import studio.nxtech.fujubank.data.remote.api.UserSearchApi
import studio.nxtech.fujubank.domain.model.Transaction
import studio.nxtech.fujubank.domain.model.TransactionDirection
import studio.nxtech.fujubank.domain.model.TransactionKind
import studio.nxtech.fujubank.domain.model.User
import studio.nxtech.fujubank.domain.model.UserSearchResult
import studio.nxtech.fujubank.session.SessionStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant

class UserRepositoryTest {

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
    fun create_maps_user_response_to_domain() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "id": 7,
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
        val repository = UserRepository(
            userApi = UserApi(httpClient(engine)),
            userMeApi = UserMeApi(httpClient(engine)),
            userSearchApi = UserSearchApi(httpClient(engine)),
            sessionStore = SessionStore(),
            useDummyData = false,
        )

        val result = repository.create(subject = "01HZY8X2B7K3J4M5N6P7Q8R9ST")

        val success = assertIs<NetworkResult.Success<User>>(result)
        assertEquals("7", success.value.id)
        assertEquals(0L, success.value.balanceFuju)
        assertEquals(Instant.parse("2026-04-21T12:34:56Z"), success.value.createdAt)
    }

    @Test
    fun get_maps_user_response_to_domain() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "id": 7,
                      "sub": "01HZY8X2B7K3J4M5N6P7Q8R9ST",
                      "balance_fuju": 1000,
                      "created_at": "2026-04-21T12:34:56Z"
                    }
                    """.trimIndent(),
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val repository = UserRepository(
            userApi = UserApi(httpClient(engine)),
            userMeApi = UserMeApi(httpClient(engine)),
            userSearchApi = UserSearchApi(httpClient(engine)),
            sessionStore = SessionStore(),
            useDummyData = false,
        )

        val result = repository.get("7")

        val success = assertIs<NetworkResult.Success<User>>(result)
        assertEquals(1_000L, success.value.balanceFuju)
        assertEquals(Instant.parse("2026-04-21T12:34:56Z"), success.value.createdAt)
    }

    @Test
    fun transactions_maps_mint_to_domain_with_null_counterparty() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "data": [
                        {
                          "entry_id": 200,
                          "transaction_id": "txn_mint",
                          "transaction_kind": "mint",
                          "direction": "credit",
                          "amount": 500,
                          "artifact_id": "art_1",
                          "counterparty_user_id": null,
                          "memo": null,
                          "metadata": null,
                          "occurred_at": "2026-04-21T00:00:00Z",
                          "created_at": "2026-04-21T00:00:01Z"
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val repository = UserRepository(
            userApi = UserApi(httpClient(engine)),
            userMeApi = UserMeApi(httpClient(engine)),
            userSearchApi = UserSearchApi(httpClient(engine)),
            sessionStore = SessionStore(),
            useDummyData = false,
        )

        val result = repository.transactions("usr_me")

        val success = assertIs<NetworkResult.Success<List<Transaction>>>(result)
        val txn = success.value.single()
        assertEquals("txn_mint", txn.id)
        assertEquals(TransactionKind.MINT, txn.kind)
        assertEquals(TransactionDirection.Mint, txn.direction)
        assertEquals(500L, txn.amount)
        assertNull(txn.counterpartyUserId)
        assertEquals("art_1", txn.artifactId)
        assertEquals(Instant.parse("2026-04-21T00:00:00Z"), txn.occurredAt)
    }

    @Test
    fun transactions_maps_outgoing_transfer_counterparty_to_recipient() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "data": [
                        {
                          "entry_id": 201,
                          "transaction_id": "txn_out",
                          "transaction_kind": "transfer",
                          "direction": "debit",
                          "amount": 200,
                          "artifact_id": null,
                          "counterparty_user_id": "usr_other",
                          "memo": null,
                          "metadata": null,
                          "occurred_at": "2026-04-21T01:00:00Z",
                          "created_at": "2026-04-21T01:00:01Z"
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val repository = UserRepository(
            userApi = UserApi(httpClient(engine)),
            userMeApi = UserMeApi(httpClient(engine)),
            userSearchApi = UserSearchApi(httpClient(engine)),
            sessionStore = SessionStore(),
            useDummyData = false,
        )

        val result = repository.transactions("usr_me")

        val success = assertIs<NetworkResult.Success<List<Transaction>>>(result)
        val txn = success.value.single()
        assertEquals(TransactionKind.TRANSFER, txn.kind)
        assertEquals(TransactionDirection.Outgoing, txn.direction)
        assertEquals("usr_other", txn.counterpartyUserId)
        assertNull(txn.artifactId)
    }

    @Test
    fun transactions_maps_incoming_transfer_counterparty_to_sender() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "data": [
                        {
                          "entry_id": 202,
                          "transaction_id": "txn_in",
                          "transaction_kind": "transfer",
                          "direction": "credit",
                          "amount": 300,
                          "artifact_id": null,
                          "counterparty_user_id": "usr_other",
                          "memo": null,
                          "metadata": null,
                          "occurred_at": "2026-04-21T02:00:00Z",
                          "created_at": "2026-04-21T02:00:01Z"
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val repository = UserRepository(
            userApi = UserApi(httpClient(engine)),
            userMeApi = UserMeApi(httpClient(engine)),
            userSearchApi = UserSearchApi(httpClient(engine)),
            sessionStore = SessionStore(),
            useDummyData = false,
        )

        val result = repository.transactions("usr_me")

        val success = assertIs<NetworkResult.Success<List<Transaction>>>(result)
        val txn = success.value.single()
        assertEquals(TransactionKind.TRANSFER, txn.kind)
        assertEquals(TransactionDirection.Incoming, txn.direction)
        assertEquals("usr_other", txn.counterpartyUserId)
    }

    @Test
    fun transactions_empty_list_maps_to_empty_domain_list() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel("""{"data":[]}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val repository = UserRepository(
            userApi = UserApi(httpClient(engine)),
            userMeApi = UserMeApi(httpClient(engine)),
            userSearchApi = UserSearchApi(httpClient(engine)),
            sessionStore = SessionStore(),
            useDummyData = false,
        )

        val result = repository.transactions("usr_me")

        val success = assertIs<NetworkResult.Success<List<Transaction>>>(result)
        assertEquals(0, success.value.size)
    }

    @Test
    fun provisionMe_maps_user_response_to_domain() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/users/me", request.url.encodedPath)
            assertEquals("POST", request.method.value)
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "id": 11,
                      "sub": "01HZY8X2B7K3J4M5N6P7Q8R9ST",
                      "name": null,
                      "public_key": null,
                      "balance_fuju": 0,
                      "created_at": "2026-04-21T12:34:56Z"
                    }
                    """.trimIndent(),
                ),
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val repository = UserRepository(
            userApi = UserApi(httpClient(engine)),
            userMeApi = UserMeApi(httpClient(engine)),
            userSearchApi = UserSearchApi(httpClient(engine)),
            sessionStore = SessionStore(),
            useDummyData = false,
        )

        val result = repository.provisionMe()

        val success = assertIs<NetworkResult.Success<User>>(result)
        assertEquals("11", success.value.id)
        assertEquals(0L, success.value.balanceFuju)
    }

    @Test
    fun getMe_maps_user_response_to_domain() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/users/me", request.url.encodedPath)
            assertEquals("GET", request.method.value)
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "id": 12,
                      "sub": "01HZY8X2B7K3J4M5N6P7Q8R9ST",
                      "balance_fuju": 5000,
                      "created_at": "2026-04-21T12:34:56Z"
                    }
                    """.trimIndent(),
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val repository = UserRepository(
            userApi = UserApi(httpClient(engine)),
            userMeApi = UserMeApi(httpClient(engine)),
            userSearchApi = UserSearchApi(httpClient(engine)),
            sessionStore = SessionStore(),
            useDummyData = false,
        )

        val result = repository.getMe()

        val success = assertIs<NetworkResult.Success<User>>(result)
        assertEquals("12", success.value.id)
        assertEquals(5_000L, success.value.balanceFuju)
    }

    @Test
    fun searchByPublicId_maps_response_and_passes_query() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/users/search", request.url.encodedPath)
            assertEquals("yuki", request.url.parameters["q"])
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "users": [
                        {
                          "id": "01HZX1A2B3C4D5E6F7G8H9JKMN",
                          "public_id": "yuki_a1b2",
                          "icon_url": "https://example.test/yuki.png"
                        },
                        {
                          "id": "01HZX1A2B3C4D5E6F7G8H9JKMP",
                          "public_id": "yuki_c3d4",
                          "icon_url": null
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val repository = UserRepository(
            userApi = UserApi(httpClient(engine)),
            userMeApi = UserMeApi(httpClient(engine)),
            userSearchApi = UserSearchApi(httpClient(engine)),
            sessionStore = SessionStore(),
            useDummyData = false,
        )

        val result = repository.searchByPublicId("yuki")

        val success = assertIs<NetworkResult.Success<List<UserSearchResult>>>(result)
        assertEquals(2, success.value.size)
        assertEquals("01HZX1A2B3C4D5E6F7G8H9JKMN", success.value[0].id)
        assertEquals("yuki_a1b2", success.value[0].publicId)
        assertEquals("https://example.test/yuki.png", success.value[0].iconUrl)
        assertEquals("01HZX1A2B3C4D5E6F7G8H9JKMP", success.value[1].id)
        assertEquals("yuki_c3d4", success.value[1].publicId)
        assertEquals(null, success.value[1].iconUrl)
    }

    @Test
    fun searchByPublicId_does_not_hit_api_for_invalid_query() = runTest {
        // Repository 側の defense in depth ガード: サーバ側 public_id 仕様
        // (`/\A[a-zA-Z0-9]+\z/` `2..32`) を満たさないクエリは API を発火させず空リストを返す。
        // ViewModel の入力ガードをバイパスする経路（テスト・他 feature 流用）でも安全な挙動。
        var apiCallCount = 0
        val engine = MockEngine {
            apiCallCount += 1
            respond(
                content = ByteReadChannel("""{"users":[{"id":"01HZX1A2B3C4D5E6F7G8H9JKMN","public_id":"x"}]}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val repository = UserRepository(
            userApi = UserApi(httpClient(engine)),
            userMeApi = UserMeApi(httpClient(engine)),
            userSearchApi = UserSearchApi(httpClient(engine)),
            sessionStore = SessionStore(),
            useDummyData = false,
        )

        // 1 文字 (TooShort) / 日本語 (Invalid) / 33 文字超 (Invalid) すべて API 発火しない。
        for (badQuery in listOf("a", "あい", "a".repeat(33), "", "ab-cd")) {
            val result = repository.searchByPublicId(badQuery)
            val success = assertIs<NetworkResult.Success<List<UserSearchResult>>>(result)
            assertEquals(0, success.value.size)
        }
        assertEquals(0, apiCallCount)
    }

    @Test
    fun searchByPublicId_returns_empty_list_for_zero_hit() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel("""{"users":[]}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val repository = UserRepository(
            userApi = UserApi(httpClient(engine)),
            userMeApi = UserMeApi(httpClient(engine)),
            userSearchApi = UserSearchApi(httpClient(engine)),
            sessionStore = SessionStore(),
            useDummyData = false,
        )

        val result = repository.searchByPublicId("nobody")

        val success = assertIs<NetworkResult.Success<List<UserSearchResult>>>(result)
        assertEquals(0, success.value.size)
    }

    @Test
    fun searchByPublicId_excludes_self_when_authenticated() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "users": [
                        { "id": "01HZY8X2B7K3J4M5N6P7Q8R9ST", "public_id": "me_xxxx" },
                        { "id": "01HZY8X2B7K3J4M5N6P7Q8R9SV", "public_id": "other_yyyy" }
                      ]
                    }
                    """.trimIndent(),
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val sessionStore = SessionStore().apply {
            setAuthenticated(userId = "01HZY8X2B7K3J4M5N6P7Q8R9ST", bankUserId = "7")
        }
        val repository = UserRepository(
            userApi = UserApi(httpClient(engine)),
            userMeApi = UserMeApi(httpClient(engine)),
            userSearchApi = UserSearchApi(httpClient(engine)),
            sessionStore = sessionStore,
            useDummyData = false,
        )

        val result = repository.searchByPublicId("any")

        val success = assertIs<NetworkResult.Success<List<UserSearchResult>>>(result)
        assertEquals(1, success.value.size)
        assertEquals("01HZY8X2B7K3J4M5N6P7Q8R9SV", success.value.single().id)
    }

    @Test
    fun searchByPublicId_returns_failure_on_unauthorized() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """{"error":"UNAUTHENTICATED","message":"login required"}""",
                ),
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val repository = UserRepository(
            userApi = UserApi(httpClient(engine)),
            userMeApi = UserMeApi(httpClient(engine)),
            userSearchApi = UserSearchApi(httpClient(engine)),
            sessionStore = SessionStore(),
            useDummyData = false,
        )

        val result = repository.searchByPublicId("any")

        val failure = assertIs<NetworkResult.Failure>(result)
        assertEquals(401, failure.error.httpStatus)
    }

    @Test
    fun searchByPublicId_filters_out_invalid_public_id_entries() = runTest {
        // サーバ侵害や DTO 想定外応答で `public_id` に許可外文字（URL スキーム / 制御文字 / 64 字超）
        // が混入した場合は QR / Code128 にエンコードする経路で第三者リダイレクトの誘発を招きうる。
        // Repository 側の allowlist で黙って弾く挙動を保証する。
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "users": [
                        { "id": "01HZX1A2B3C4D5E6F7G8H9JKM1", "public_id": "valid_one" },
                        { "id": "01HZX1A2B3C4D5E6F7G8H9JKM2", "public_id": "https://evil.example/" },
                        { "id": "01HZX1A2B3C4D5E6F7G8H9JKM3", "public_id": "valid_two" },
                        { "id": "01HZX1A2B3C4D5E6F7G8H9JKM4", "public_id": "" }
                      ]
                    }
                    """.trimIndent(),
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val repository = UserRepository(
            userApi = UserApi(httpClient(engine)),
            userMeApi = UserMeApi(httpClient(engine)),
            userSearchApi = UserSearchApi(httpClient(engine)),
            sessionStore = SessionStore(),
            useDummyData = false,
        )

        val result = repository.searchByPublicId("any")

        val success = assertIs<NetworkResult.Success<List<UserSearchResult>>>(result)
        assertEquals(2, success.value.size)
        assertEquals(listOf("valid_one", "valid_two"), success.value.map { it.publicId })
    }

    @Test
    fun searchByPublicId_returns_failure_on_rate_limit() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """{"error":{"code":"RATE_LIMIT_EXCEEDED","message":"too many"}}""",
                ),
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val repository = UserRepository(
            userApi = UserApi(httpClient(engine)),
            userMeApi = UserMeApi(httpClient(engine)),
            userSearchApi = UserSearchApi(httpClient(engine)),
            sessionStore = SessionStore(),
            useDummyData = false,
        )

        val result = repository.searchByPublicId("any")

        val failure = assertIs<NetworkResult.Failure>(result)
        assertEquals(429, failure.error.httpStatus)
    }
}
