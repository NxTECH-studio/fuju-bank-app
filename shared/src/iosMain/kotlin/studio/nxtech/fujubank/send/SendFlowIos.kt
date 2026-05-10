package studio.nxtech.fujubank.send

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import studio.nxtech.fujubank.data.remote.ApiErrorCode
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.repository.LedgerRepository
import studio.nxtech.fujubank.data.repository.TransferResult
import studio.nxtech.fujubank.data.repository.UserRepository
import studio.nxtech.fujubank.domain.model.UserSearchResult

/**
 * Swift から呼び出す表示名検索の結果型。
 *
 * - [Loaded]: 取得成功（自分自身は Repository で除外済み）。
 * - [Failure]: API エラー（401 / 429 等）。`message` は表示用日本語。
 * - [NetworkFailure]: 通信失敗。
 */
sealed class UserSearchOutcome {
    data class Loaded(val results: List<UserSearchResult>) : UserSearchOutcome()
    data class Failure(val message: String) : UserSearchOutcome()
    data class NetworkFailure(val message: String) : UserSearchOutcome()
}

/**
 * Swift から呼び出す送金実行の結果型。
 *
 * - [Success]: 送金成功。`newBalance` は新残高、`transactionId` は新規取引 ID。
 * - [MfaRequired]: MFA 検証が必要。`retryKey` を保持して再 transfer に渡す。
 * - [Failure]: API エラー。`message` は表示用日本語、`reset` は Step 1 に戻すべきか。
 * - [NetworkFailure]: 通信失敗。`message` は表示用日本語。
 */
sealed class TransferOutcome {
    data class Success(val transactionId: String, val newBalance: Long) : TransferOutcome()
    data class MfaRequired(val retryKey: String) : TransferOutcome()
    data class Failure(val message: String, val reset: Boolean) : TransferOutcome()
    data class NetworkFailure(val message: String) : TransferOutcome()
}

private val sendScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

/**
 * Swift 側 `SendRecipientView` から表示名検索を kick するためのファサード。
 * 結果は `Dispatchers.Main` で `onResult` コールバックに返す。
 */
fun searchRecipients(
    userRepository: UserRepository,
    query: String,
    onResult: (UserSearchOutcome) -> Unit,
): Job = sendScope.launch {
    val outcome = when (val result = userRepository.searchByDisplayName(query)) {
        is NetworkResult.Success -> UserSearchOutcome.Loaded(results = result.value)
        is NetworkResult.Failure -> UserSearchOutcome.Failure(
            message = if (result.error.code == ApiErrorCode.RATE_LIMIT_EXCEEDED) {
                "検索が混み合っています。しばらく待ってください"
            } else {
                "検索に失敗しました"
            },
        )
        is NetworkResult.NetworkFailure -> UserSearchOutcome.NetworkFailure(
            message = "通信エラーが発生しました",
        )
    }
    ensureActive()
    onResult(outcome)
}

/**
 * Swift 側 `SendAmountView` から送金実行を kick するためのファサード。
 * MFA 経路では呼び出し側で `retryKey` を保持しておき、verify 完了後に同じ key で再呼出する。
 */
fun executeTransfer(
    ledgerRepository: LedgerRepository,
    fromUserId: String,
    toUserId: String,
    amount: Long,
    retryKey: String?,
    onResult: (TransferOutcome) -> Unit,
): Job = sendScope.launch {
    val outcome = when (val result = ledgerRepository.transfer(
        from = fromUserId,
        to = toUserId,
        amount = amount,
        retryKey = retryKey,
    )) {
        is TransferResult.Success -> TransferOutcome.Success(
            transactionId = result.transactionId,
            newBalance = result.newBalance,
        )
        is TransferResult.MfaRequired -> TransferOutcome.MfaRequired(retryKey = result.retryKey)
        is TransferResult.Failure -> TransferOutcome.Failure(
            message = mapFailureMessage(result.error.code),
            reset = result.error.code == ApiErrorCode.NOT_FOUND,
        )
    }
    ensureActive()
    onResult(outcome)
}

private fun mapFailureMessage(code: ApiErrorCode): String = when (code) {
    ApiErrorCode.INSUFFICIENT_BALANCE -> "残高が不足しています"
    ApiErrorCode.VALIDATION_FAILED -> "入力内容に誤りがあります"
    ApiErrorCode.NOT_FOUND -> "送金先が見つかりませんでした。再度検索してください"
    ApiErrorCode.UNAUTHENTICATED, ApiErrorCode.TOKEN_EXPIRED, ApiErrorCode.TOKEN_INVALID,
    ApiErrorCode.TOKEN_REVOKED,
    -> "セッションが無効です。もう一度ログインしてください"
    ApiErrorCode.RATE_LIMIT_EXCEEDED -> "アクセスが集中しています。しばらく待ってください"
    ApiErrorCode.AUTHCORE_UNAVAILABLE -> "認証サービスが応答していません。後ほどお試しください"
    else -> "送金に失敗しました"
}
