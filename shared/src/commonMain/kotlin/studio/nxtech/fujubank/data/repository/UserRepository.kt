package studio.nxtech.fujubank.data.repository

import kotlinx.coroutines.delay
import studio.nxtech.fujubank.BuildKonfig
import studio.nxtech.fujubank.data.remote.ApiError
import studio.nxtech.fujubank.data.remote.ApiErrorCode
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.remote.api.UserApi
import studio.nxtech.fujubank.data.remote.api.UserMeApi
import studio.nxtech.fujubank.data.remote.api.UserSearchApi
import studio.nxtech.fujubank.data.remote.dto.CreateUserRequest
import studio.nxtech.fujubank.data.remote.dto.TransactionDirectionWire
import studio.nxtech.fujubank.data.remote.dto.TransactionDto
import studio.nxtech.fujubank.data.remote.dto.UserResponse
import studio.nxtech.fujubank.data.remote.dto.UserSearchResultDto
import studio.nxtech.fujubank.data.remote.map
import studio.nxtech.fujubank.domain.model.Transaction
import studio.nxtech.fujubank.domain.model.TransactionDirection
import studio.nxtech.fujubank.domain.model.TransactionKind
import studio.nxtech.fujubank.domain.model.User
import studio.nxtech.fujubank.domain.model.UserSearchResult
import studio.nxtech.fujubank.session.SessionState
import studio.nxtech.fujubank.session.SessionStore
import kotlin.time.Instant

class UserRepository(
    private val userApi: UserApi,
    private val userMeApi: UserMeApi,
    private val userSearchApi: UserSearchApi,
    private val sessionStore: SessionStore,
    // テストや本番では false を強制する。デフォルトは BuildKonfig 側のフラグに従う。
    val useDummyData: Boolean = BuildKonfig.USE_DUMMY_PROFILE,
) {

    suspend fun create(subject: String): NetworkResult<User> =
        userApi.create(CreateUserRequest(subject = subject)).map { it.toDomain() }

    suspend fun get(userId: String): NetworkResult<User> =
        userApi.get(userId).map { it.toDomain() }

    /**
     * bank 側 user 行を lazy provision する（`POST /users/me`）。
     *
     * ログイン直後に呼び出す想定。サーバーは Bearer access_token の sub を external_user_id
     * として扱い、未登録なら新規作成、既存なら no-op で最新状態を返す。
     */
    suspend fun provisionMe(
        name: String? = null,
        publicKey: String? = null,
    ): NetworkResult<User> =
        userMeApi.upsertMe(name = name, publicKey = publicKey).map { it.toDomain() }

    /**
     * 自分の最新状態を取得する（`GET /users/me`）。残高表示やセッション復元に使う。
     */
    suspend fun getMe(): NetworkResult<User> =
        userMeApi.getMe().map { it.toDomain() }

    // server (`/users/:id/transactions`) は JWT 認証により自身の取引のみを返し、
    // direction (credit/debit) と counterparty_user_id を確定済みで返してくる。
    // そのため client 側で `myUserId` 比較による direction 推定は行わない。
    suspend fun transactions(userId: String): NetworkResult<List<Transaction>> {
        if (useDummyData) {
            // 通信を伴わない UI 確認用フェイクデータ。loading 状態を観察できるよう少しだけ待つ。
            delay(300)
            return NetworkResult.Success(dummyTransactions())
        }
        // 防御的ガード: release ビルドで万が一 userId が空文字 / 空白で渡ってきた場合に
        // `/users//transactions` のような不正パスで API を叩かないよう ApiError として弾く。
        // 上位 (ViewModel / facade) でも SessionStore でガードしているが二重に守る。
        if (userId.isBlank()) {
            return NetworkResult.Failure(
                ApiError(
                    code = ApiErrorCode.UNAUTHENTICATED,
                    message = "userId is blank",
                    httpStatus = 0,
                ),
            )
        }
        return userApi.transactions(userId).map { response ->
            response.data.map { it.toDomain() }
        }
    }

    /**
     * 公開ID (public_id) の前方一致で送金先候補を検索する。
     *
     * - クエリ最低 2 文字バリデーションは ViewModel 側で行う前提（Repository は通過させる）。
     * - サーバ側でも自分自身を除外する契約だが、UI 側の安全網として SessionStore の現在
     *   `userId` と一致する候補を Repository でも弾く。
     * - 取得した `public_id` は UI で `@{publicId}` として表示される他、将来 QR / Code128 に
     *   エンコードされる可能性があるため、[isValidPublicId] の allowlist
     *   （`[A-Za-z0-9_-]{1,64}`）で形式検証し、想定外文字を含むエントリは黙って弾く。
     *   サーバが侵害された場合や DTO 想定外応答に備えた多層防御。
     * - エラーは [NetworkResult] のままパススルーする（429 / 401 等は呼び出し側で UI に
     *   反映する）。
     */
    suspend fun searchByPublicId(query: String): NetworkResult<List<UserSearchResult>> {
        val myUserId = (sessionStore.current as? SessionState.Authenticated)?.userId
        return userSearchApi.searchByPublicId(query).map { dtos ->
            dtos.asSequence()
                .filter { isValidPublicId(it.publicId) }
                .map { it.toDomain() }
                .filter { myUserId == null || it.id != myUserId }
                .toList()
        }
    }
}

private fun UserSearchResultDto.toDomain(): UserSearchResult = UserSearchResult(
    id = id.toString(),
    publicId = publicId,
    iconUrl = iconUrl,
)

private fun UserResponse.toDomain(): User = User(
    id = id.toString(),
    balanceFuju = balanceFuju,
    createdAt = Instant.parse(createdAt),
)

private fun TransactionDto.toDomain(): Transaction = Transaction(
    id = id,
    kind = kind,
    direction = resolveDirection(kind, direction),
    amount = amount,
    counterpartyUserId = counterpartyUserId,
    artifactId = artifactId,
    occurredAt = Instant.parse(occurredAt),
)

// mint は常に Mint 扱い（現 MVP では burn = mint+debit が発生しない契約）。
// transfer は server の credit/debit をそのまま Incoming/Outgoing にマップする。
private fun resolveDirection(
    kind: TransactionKind,
    wire: TransactionDirectionWire,
): TransactionDirection = when (kind) {
    TransactionKind.MINT -> TransactionDirection.Mint
    TransactionKind.TRANSFER -> when (wire) {
        TransactionDirectionWire.CREDIT -> TransactionDirection.Incoming
        TransactionDirectionWire.DEBIT -> TransactionDirection.Outgoing
    }
}

// `useDummyProfile=true` 時に返すダミー取引履歴。3 種類の direction を網羅し、UI の
// 色分け / 符号 / 並びが一目で確認できるよう、新しい順に並べた状態で返す。
// スクロール挙動を確認するため、画面に収まりきらない件数（25 件）を用意している。
private fun dummyTransactions(): List<Transaction> = listOf(
    Transaction(
        id = "txn_dummy_001",
        kind = TransactionKind.TRANSFER,
        direction = TransactionDirection.Incoming,
        amount = 3_230L,
        counterpartyUserId = "usr_tomato_001",
        artifactId = null,
        occurredAt = Instant.parse("2025-12-13T03:24:00Z"),
    ),
    Transaction(
        id = "txn_dummy_002",
        kind = TransactionKind.TRANSFER,
        direction = TransactionDirection.Outgoing,
        amount = 12_020L,
        counterpartyUserId = "usr_nishi_001",
        artifactId = null,
        occurredAt = Instant.parse("2025-12-12T05:42:00Z"),
    ),
    Transaction(
        id = "txn_dummy_003",
        kind = TransactionKind.TRANSFER,
        direction = TransactionDirection.Outgoing,
        amount = 3_230L,
        counterpartyUserId = "usr_tomato_001",
        artifactId = null,
        occurredAt = Instant.parse("2025-12-11T01:10:00Z"),
    ),
    Transaction(
        id = "txn_dummy_004",
        kind = TransactionKind.MINT,
        direction = TransactionDirection.Mint,
        amount = 50_000L,
        counterpartyUserId = null,
        artifactId = "art_welcome_bonus",
        occurredAt = Instant.parse("2025-12-10T08:00:00Z"),
    ),
    Transaction(
        id = "txn_dummy_005",
        kind = TransactionKind.TRANSFER,
        direction = TransactionDirection.Incoming,
        amount = 800L,
        counterpartyUserId = "usr_kabu_001",
        artifactId = null,
        occurredAt = Instant.parse("2025-12-09T12:00:00Z"),
    ),
    Transaction(
        id = "txn_dummy_006",
        kind = TransactionKind.TRANSFER,
        direction = TransactionDirection.Outgoing,
        amount = 1_500L,
        counterpartyUserId = "usr_nishi_001",
        artifactId = null,
        occurredAt = Instant.parse("2025-12-08T22:18:00Z"),
    ),
    Transaction(
        id = "txn_dummy_007",
        kind = TransactionKind.TRANSFER,
        direction = TransactionDirection.Incoming,
        amount = 4_500L,
        counterpartyUserId = "usr_sakura_001",
        artifactId = null,
        occurredAt = Instant.parse("2025-12-07T18:42:00Z"),
    ),
    Transaction(
        id = "txn_dummy_008",
        kind = TransactionKind.TRANSFER,
        direction = TransactionDirection.Outgoing,
        amount = 980L,
        counterpartyUserId = "usr_kabu_001",
        artifactId = null,
        occurredAt = Instant.parse("2025-12-07T09:15:00Z"),
    ),
    Transaction(
        id = "txn_dummy_009",
        kind = TransactionKind.MINT,
        direction = TransactionDirection.Mint,
        amount = 2_000L,
        counterpartyUserId = null,
        artifactId = "art_daily_bonus_07",
        occurredAt = Instant.parse("2025-12-06T08:00:00Z"),
    ),
    Transaction(
        id = "txn_dummy_010",
        kind = TransactionKind.TRANSFER,
        direction = TransactionDirection.Incoming,
        amount = 6_700L,
        counterpartyUserId = "usr_tomato_001",
        artifactId = null,
        occurredAt = Instant.parse("2025-12-05T20:33:00Z"),
    ),
    Transaction(
        id = "txn_dummy_011",
        kind = TransactionKind.TRANSFER,
        direction = TransactionDirection.Outgoing,
        amount = 8_400L,
        counterpartyUserId = "usr_aoba_001",
        artifactId = null,
        occurredAt = Instant.parse("2025-12-05T11:08:00Z"),
    ),
    Transaction(
        id = "txn_dummy_012",
        kind = TransactionKind.TRANSFER,
        direction = TransactionDirection.Incoming,
        amount = 1_200L,
        counterpartyUserId = "usr_nishi_001",
        artifactId = null,
        occurredAt = Instant.parse("2025-12-04T19:51:00Z"),
    ),
    Transaction(
        id = "txn_dummy_013",
        kind = TransactionKind.TRANSFER,
        direction = TransactionDirection.Outgoing,
        amount = 22_500L,
        counterpartyUserId = "usr_sakura_001",
        artifactId = null,
        occurredAt = Instant.parse("2025-12-04T13:27:00Z"),
    ),
    Transaction(
        id = "txn_dummy_014",
        kind = TransactionKind.MINT,
        direction = TransactionDirection.Mint,
        amount = 5_000L,
        counterpartyUserId = null,
        artifactId = "art_streak_7d",
        occurredAt = Instant.parse("2025-12-03T08:00:00Z"),
    ),
    Transaction(
        id = "txn_dummy_015",
        kind = TransactionKind.TRANSFER,
        direction = TransactionDirection.Incoming,
        amount = 3_330L,
        counterpartyUserId = "usr_kabu_001",
        artifactId = null,
        occurredAt = Instant.parse("2025-12-02T22:04:00Z"),
    ),
    Transaction(
        id = "txn_dummy_016",
        kind = TransactionKind.TRANSFER,
        direction = TransactionDirection.Outgoing,
        amount = 410L,
        counterpartyUserId = "usr_tomato_001",
        artifactId = null,
        occurredAt = Instant.parse("2025-12-02T07:38:00Z"),
    ),
    Transaction(
        id = "txn_dummy_017",
        kind = TransactionKind.TRANSFER,
        direction = TransactionDirection.Outgoing,
        amount = 6_180L,
        counterpartyUserId = "usr_nishi_001",
        artifactId = null,
        occurredAt = Instant.parse("2025-12-01T17:22:00Z"),
    ),
    Transaction(
        id = "txn_dummy_018",
        kind = TransactionKind.TRANSFER,
        direction = TransactionDirection.Incoming,
        amount = 9_000L,
        counterpartyUserId = "usr_aoba_001",
        artifactId = null,
        occurredAt = Instant.parse("2025-12-01T09:11:00Z"),
    ),
    Transaction(
        id = "txn_dummy_019",
        kind = TransactionKind.MINT,
        direction = TransactionDirection.Mint,
        amount = 1_500L,
        counterpartyUserId = null,
        artifactId = "art_daily_bonus_05",
        occurredAt = Instant.parse("2025-11-30T08:00:00Z"),
    ),
    Transaction(
        id = "txn_dummy_020",
        kind = TransactionKind.TRANSFER,
        direction = TransactionDirection.Incoming,
        amount = 2_750L,
        counterpartyUserId = "usr_sakura_001",
        artifactId = null,
        occurredAt = Instant.parse("2025-11-29T21:46:00Z"),
    ),
    Transaction(
        id = "txn_dummy_021",
        kind = TransactionKind.TRANSFER,
        direction = TransactionDirection.Outgoing,
        amount = 14_900L,
        counterpartyUserId = "usr_kabu_001",
        artifactId = null,
        occurredAt = Instant.parse("2025-11-29T12:18:00Z"),
    ),
    Transaction(
        id = "txn_dummy_022",
        kind = TransactionKind.TRANSFER,
        direction = TransactionDirection.Outgoing,
        amount = 700L,
        counterpartyUserId = "usr_tomato_001",
        artifactId = null,
        occurredAt = Instant.parse("2025-11-28T16:09:00Z"),
    ),
    Transaction(
        id = "txn_dummy_023",
        kind = TransactionKind.TRANSFER,
        direction = TransactionDirection.Incoming,
        amount = 5_550L,
        counterpartyUserId = "usr_nishi_001",
        artifactId = null,
        occurredAt = Instant.parse("2025-11-27T23:01:00Z"),
    ),
    Transaction(
        id = "txn_dummy_024",
        kind = TransactionKind.MINT,
        direction = TransactionDirection.Mint,
        amount = 3_000L,
        counterpartyUserId = null,
        artifactId = "art_signup_thanks",
        occurredAt = Instant.parse("2025-11-26T08:00:00Z"),
    ),
    Transaction(
        id = "txn_dummy_025",
        kind = TransactionKind.TRANSFER,
        direction = TransactionDirection.Outgoing,
        amount = 1_080L,
        counterpartyUserId = "usr_aoba_001",
        artifactId = null,
        occurredAt = Instant.parse("2025-11-25T14:54:00Z"),
    ),
)
