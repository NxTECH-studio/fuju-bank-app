package studio.nxtech.fujubank.data.repository

import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.remote.api.UserApi
import studio.nxtech.fujubank.data.remote.dto.CreateUserRequest
import studio.nxtech.fujubank.data.remote.dto.TransactionDto
import studio.nxtech.fujubank.data.remote.dto.UserResponse
import studio.nxtech.fujubank.data.remote.map
import studio.nxtech.fujubank.domain.model.Transaction
import studio.nxtech.fujubank.domain.model.TransactionKind
import studio.nxtech.fujubank.domain.model.User
import kotlin.time.Instant
import studio.nxtech.fujubank.data.remote.dto.TransactionKind as TransactionKindDto

class UserRepository(private val userApi: UserApi) {

    suspend fun create(subject: String): NetworkResult<User> =
        userApi.create(CreateUserRequest(subject = subject)).map { it.toDomain() }

    suspend fun get(userId: String): NetworkResult<User> =
        userApi.get(userId).map { it.toDomain() }

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
    kind = kind.toDomain(),
    amount = amount,
    counterpartyUserId = counterpartyUserId(myUserId),
    artifactId = artifactId,
    occurredAt = Instant.parse(occurredAt),
)

private fun TransactionKindDto.toDomain(): TransactionKind = when (this) {
    TransactionKindDto.MINT -> TransactionKind.MINT
    TransactionKindDto.TRANSFER -> TransactionKind.TRANSFER
}

// mint: 常に null（相手なし）。
// transfer: 自分が from なら to、自分が to なら from を返す。
private fun TransactionDto.counterpartyUserId(myUserId: String): String? = when (kind) {
    TransactionKindDto.MINT -> null
    TransactionKindDto.TRANSFER -> if (fromUserId == myUserId) toUserId else fromUserId
}
