package studio.nxtech.fujubank.data.repository

import studio.nxtech.fujubank.data.remote.ApiError
import studio.nxtech.fujubank.data.remote.ApiErrorCode
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.remote.api.LedgerApi
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class LedgerRepository(
    private val ledgerApi: LedgerApi,
    private val idempotencyKeyFactory: () -> String = { Uuid.random().toString() },
) {

    suspend fun transfer(
        from: String,
        to: String,
        amount: Long,
        memo: String? = null,
        retryKey: String? = null,
    ): TransferResult {
        // retryKey が null の場合は Repository 側で新規採番し、MFA 等で再試行する際に
        // UI から同じキーを渡し直せるようにする。
        val idempotencyKey = retryKey ?: idempotencyKeyFactory()
        return when (val result = ledgerApi.transfer(
            fromUserId = from,
            toUserId = to,
            amount = amount,
            memo = memo,
            idempotencyKey = idempotencyKey,
        )) {
            is NetworkResult.Success -> TransferResult.Success(
                newBalance = result.value.newBalance,
                transactionId = result.value.transactionId,
            )
            is NetworkResult.Failure -> if (result.error.code == ApiErrorCode.MFA_REQUIRED) {
                TransferResult.MfaRequired(retryKey = idempotencyKey)
            } else {
                TransferResult.Failure(result.error)
            }
            is NetworkResult.NetworkFailure -> TransferResult.Failure(
                ApiError(
                    code = ApiErrorCode.UNKNOWN,
                    message = result.cause.message ?: "",
                    httpStatus = 0,
                ),
            )
        }
    }
}

sealed class TransferResult {
    data class Success(val newBalance: Long, val transactionId: String) : TransferResult()
    data class MfaRequired(val retryKey: String) : TransferResult()
    data class Failure(val error: ApiError) : TransferResult()
}
