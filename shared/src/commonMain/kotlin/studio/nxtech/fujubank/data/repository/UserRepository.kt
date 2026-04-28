package studio.nxtech.fujubank.data.repository

import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.remote.api.UserApi
import studio.nxtech.fujubank.data.remote.api.UserMeApi
import studio.nxtech.fujubank.data.remote.dto.CreateUserRequest
import studio.nxtech.fujubank.data.remote.dto.TransactionDto
import studio.nxtech.fujubank.data.remote.dto.UserResponse
import studio.nxtech.fujubank.data.remote.map
import studio.nxtech.fujubank.domain.model.Transaction
import studio.nxtech.fujubank.domain.model.TransactionKind
import studio.nxtech.fujubank.domain.model.User
import kotlin.time.Instant

class UserRepository(
    private val userApi: UserApi,
    private val userMeApi: UserMeApi,
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
    suspend fun transactions(userId: String): NetworkResult<List<Transaction>> =
        userApi.transactions(userId).map { response ->
            response.transactions.map { it.toDomain(myUserId = userId) }
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
