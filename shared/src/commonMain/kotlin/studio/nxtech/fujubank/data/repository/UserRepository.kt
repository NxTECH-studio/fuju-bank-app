package studio.nxtech.fujubank.data.repository

import kotlinx.coroutines.delay
import studio.nxtech.fujubank.BuildKonfig
import studio.nxtech.fujubank.data.remote.ApiError
import studio.nxtech.fujubank.data.remote.ApiErrorCode
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.remote.api.UserApi
import studio.nxtech.fujubank.data.remote.api.UserMeApi
import studio.nxtech.fujubank.data.remote.dto.CreateUserRequest
import studio.nxtech.fujubank.data.remote.dto.TransactionDto
import studio.nxtech.fujubank.data.remote.dto.UserResponse
import studio.nxtech.fujubank.data.remote.map
import studio.nxtech.fujubank.domain.model.Transaction
import studio.nxtech.fujubank.domain.model.TransactionDirection
import studio.nxtech.fujubank.domain.model.TransactionKind
import studio.nxtech.fujubank.domain.model.User
import kotlin.time.Instant

class UserRepository(
    private val userApi: UserApi,
    private val userMeApi: UserMeApi,
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

    // `userId` は API のパスパラメータであると同時に、`Transaction.counterpartyUserId`
    // を決定する際の「自分」としても使われる。サーバーは JWT 認証により自身の取引
    // しか返さない前提。
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
            response.transactions.map { it.toDomain(myUserId = userId) }
        }
    }
}

private fun UserResponse.toDomain(): User = User(
    id = id,
    balanceFuju = balanceFuju,
    createdAt = Instant.parse(createdAt),
)

private fun TransactionDto.toDomain(myUserId: String): Transaction = Transaction(
    id = id,
    kind = kind,
    direction = direction(myUserId),
    amount = amount,
    counterpartyUserId = counterpartyUserId(myUserId),
    artifactId = artifactId,
    occurredAt = Instant.parse(occurredAt),
)

// mint: 常に null（相手なし）。
// transfer: 自分が from なら to、自分が to なら from を返す。
private fun TransactionDto.counterpartyUserId(myUserId: String): String? = when (kind) {
    TransactionKind.MINT -> null
    TransactionKind.TRANSFER -> if (fromUserId == myUserId) toUserId else fromUserId
}

// mint: 常に Incoming 扱い (実体は新規発行)。
// transfer: 自分が from なら Outgoing、それ以外は Incoming。
private fun TransactionDto.direction(myUserId: String): TransactionDirection = when (kind) {
    TransactionKind.MINT -> TransactionDirection.Mint
    TransactionKind.TRANSFER -> if (fromUserId == myUserId) {
        TransactionDirection.Outgoing
    } else {
        TransactionDirection.Incoming
    }
}

// `useDummyProfile=true` 時に返すダミー取引履歴。3 種類の direction を網羅し、UI の
// 色分け / 符号 / 並びが一目で確認できるよう、新しい順に並べた状態で返す。
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
)
