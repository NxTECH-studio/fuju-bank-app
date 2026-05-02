package studio.nxtech.fujubank.transactions

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.repository.UserRepository
import studio.nxtech.fujubank.domain.model.Transaction
import studio.nxtech.fujubank.session.SessionState
import studio.nxtech.fujubank.session.SessionStore

/**
 * Swift から呼び出す取引履歴取得の結果型。
 *
 * - [Loaded]: 取得成功（時系列降順済み）。
 * - [Failure]: API エラー（401 など）。`message` は表示用日本語。
 * - [NetworkFailure]: 通信失敗。
 * - [Unauthenticated]: SessionStore が未認証状態。
 */
sealed class TransactionsLoadOutcome {
    data class Loaded(val transactions: List<Transaction>) : TransactionsLoadOutcome()
    data class Failure(val message: String) : TransactionsLoadOutcome()
    data class NetworkFailure(val message: String) : TransactionsLoadOutcome()
    object Unauthenticated : TransactionsLoadOutcome()
}

/**
 * Swift 側 `TransactionListViewModel` から `fetchMyTransactions { outcome in ... }` の形で
 * 呼ぶためのファサード。`ProfileFlowIos` と同じパターン。
 */
private val transactionsScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

fun fetchMyTransactions(
    userRepository: UserRepository,
    sessionStore: SessionStore,
    onResult: (TransactionsLoadOutcome) -> Unit,
): Job = transactionsScope.launch {
    val userId = (sessionStore.current as? SessionState.Authenticated)?.userId
    val outcome = if (userId == null) {
        TransactionsLoadOutcome.Unauthenticated
    } else {
        when (val result = userRepository.transactions(userId)) {
            is NetworkResult.Success -> TransactionsLoadOutcome.Loaded(
                transactions = result.value.sortedByDescending { it.occurredAt },
            )
            is NetworkResult.Failure -> TransactionsLoadOutcome.Failure(
                message = "取引履歴を取得できませんでした",
            )
            is NetworkResult.NetworkFailure -> TransactionsLoadOutcome.NetworkFailure(
                message = "通信エラーが発生しました",
            )
        }
    }
    ensureActive()
    onResult(outcome)
}
