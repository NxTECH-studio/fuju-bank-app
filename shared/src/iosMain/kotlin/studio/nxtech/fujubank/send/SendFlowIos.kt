package studio.nxtech.fujubank.send

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import studio.nxtech.fujubank.data.remote.ApiErrorCode
import studio.nxtech.fujubank.data.repository.LedgerRepository
import studio.nxtech.fujubank.data.repository.TransferResult
import studio.nxtech.fujubank.session.SessionState
import studio.nxtech.fujubank.session.SessionStore

/**
 * Swift から呼び出す送金実行の結果型。`TransactionsLoadOutcome` と同じ設計で、
 * shared 側でエラーメッセージを日本語化して Swift に渡す。
 *
 * - [Success]: 送金成功。`newBalance` は送金後の自分の残高（ホーム反映に使う）。
 * - [InsufficientBalance]: 残高不足。
 * - [RecipientNotFound]: 送り先 user が解決できない。
 * - [RateLimitExceeded]: レート制限超過。
 * - [MfaRequired]: MFA が要求された。`retryKey` は MFA 通過後に同じキーで再送する用。
 * - [Failure]: その他 API エラー（VALIDATION_FAILED など）。
 * - [NetworkFailure]: 通信失敗。
 * - [Unauthenticated]: SessionStore が未認証状態。
 */
sealed class SendOutcome {
    data class Success(val newBalance: Long, val transactionId: String) : SendOutcome()
    data object InsufficientBalance : SendOutcome()
    data object RecipientNotFound : SendOutcome()
    data object RateLimitExceeded : SendOutcome()
    data class MfaRequired(val retryKey: String) : SendOutcome()
    data class Failure(val message: String) : SendOutcome()
    data class NetworkFailure(val message: String) : SendOutcome()
    data object Unauthenticated : SendOutcome()
}

private val sendScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

/**
 * Swift 側 `SendViewModel` から `submitTransfer { outcome in ... }` の形で呼ぶためのファサード。
 * `TransactionsFlowIos` と同じパターン。
 *
 * - 自分の userId は SessionStore から解決する。ダミーモードでは空文字でフォールスルー。
 * - retryKey が指定されている場合は LedgerRepository に渡し、MFA 後の再送に対応する。
 */
fun submitTransfer(
    ledgerRepository: LedgerRepository,
    sessionStore: SessionStore,
    recipientExternalId: String,
    amount: Long,
    memo: String? = null,
    retryKey: String? = null,
    onResult: (SendOutcome) -> Unit,
): Job = sendScope.launch {
    val sessionUserId = (sessionStore.current as? SessionState.Authenticated)?.userId
    val fromUserId = sessionUserId ?: if (ledgerRepository.useDummyData) "" else null
    val outcome = if (fromUserId == null) {
        SendOutcome.Unauthenticated
    } else {
        when (val result = ledgerRepository.transfer(
            from = fromUserId,
            to = recipientExternalId,
            amount = amount,
            memo = memo,
            retryKey = retryKey,
        )) {
            is TransferResult.Success -> SendOutcome.Success(
                newBalance = result.newBalance,
                transactionId = result.transactionId,
            )
            is TransferResult.MfaRequired -> SendOutcome.MfaRequired(retryKey = result.retryKey)
            is TransferResult.Failure -> when (result.error.code) {
                ApiErrorCode.INSUFFICIENT_BALANCE -> SendOutcome.InsufficientBalance
                ApiErrorCode.RECIPIENT_NOT_FOUND, ApiErrorCode.NOT_FOUND -> SendOutcome.RecipientNotFound
                ApiErrorCode.RATE_LIMIT_EXCEEDED -> SendOutcome.RateLimitExceeded
                ApiErrorCode.UNKNOWN -> SendOutcome.NetworkFailure(message = "通信エラーが発生しました")
                else -> SendOutcome.Failure(message = "送金に失敗しました")
            }
        }
    }
    ensureActive()
    onResult(outcome)
}
