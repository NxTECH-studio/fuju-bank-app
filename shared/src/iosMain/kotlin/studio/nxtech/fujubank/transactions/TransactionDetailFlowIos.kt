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
 * Swift から呼び出す取引詳細取得の結果型。
 *
 * Android 側の [studio.nxtech.fujubank.features.transactions.TransactionDetailViewModel]
 * は一覧画面で取得済みの [Transaction] をそのまま流し込むだけで、サーバーへの単発取得 API は
 * 持たない。iOS 側もこの方針に揃え、`TransactionDetailViewModel(transaction:)` のように
 * 既に手元にある [Transaction] を渡す経路を主にする。
 *
 * このファサードはディープリンクや状態復元等で「取引 ID しかない状態」から詳細を表示したい
 * 将来用途のために、`UserRepository.transactions` を再フェッチして該当 ID の Transaction を
 * 探す薄いヘルパとして用意する。MVP では UI からは呼ばれない想定だが、Swift 側から型として
 * 参照できるように export しておく。
 *
 * - [Loaded]: 取得成功。`transaction` は該当 ID の [Transaction]。
 * - [NotFound]: 一覧取得には成功したが該当 ID の取引が見つからなかった。
 * - [Failure]: API エラー（401 など）。
 * - [NetworkFailure]: 通信失敗。
 * - [Unauthenticated]: SessionStore が未認証状態。
 */
sealed class TransactionDetailOutcome {
    data class Loaded(val transaction: Transaction) : TransactionDetailOutcome()
    data class NotFound(val transactionId: String) : TransactionDetailOutcome()
    data class Failure(val message: String) : TransactionDetailOutcome()
    data class NetworkFailure(val message: String) : TransactionDetailOutcome()
    object Unauthenticated : TransactionDetailOutcome()
}

private val transactionDetailScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

/**
 * Swift 側 `TransactionDetailViewModel` から `fetchTransactionDetail(id:) { outcome in ... }`
 * の形で呼ぶためのファサード。`TransactionsFlowIos.fetchMyTransactions` と同じパターンで
 * 一覧 API を叩き、結果から `transactionId` に一致する 1 件を返す。
 *
 * 戻り値の [Job] を Swift 側で保持し、再フェッチ時に `cancel(cause: nil)` することで
 * 古い結果が後勝ちで `state` を上書きするのを防ぐ。
 */
fun fetchTransactionDetail(
    transactionId: String,
    userRepository: UserRepository,
    sessionStore: SessionStore,
    onResult: (TransactionDetailOutcome) -> Unit,
): Job = transactionDetailScope.launch {
    val sessionUserId = (sessionStore.current as? SessionState.Authenticated)?.userId
    val userId = sessionUserId ?: if (userRepository.useDummyData) "" else null
    val outcome = if (userId == null) {
        TransactionDetailOutcome.Unauthenticated
    } else {
        when (val result = userRepository.transactions(userId)) {
            is NetworkResult.Success -> result.value.firstOrNull { it.id == transactionId }
                ?.let { TransactionDetailOutcome.Loaded(transaction = it) }
                ?: TransactionDetailOutcome.NotFound(transactionId = transactionId)
            is NetworkResult.Failure -> TransactionDetailOutcome.Failure(
                message = "取引詳細を取得できませんでした",
            )
            is NetworkResult.NetworkFailure -> TransactionDetailOutcome.NetworkFailure(
                message = "通信エラーが発生しました",
            )
        }
    }
    ensureActive()
    onResult(outcome)
}
