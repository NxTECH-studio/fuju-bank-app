package studio.nxtech.fujubank.data.repository

import kotlinx.coroutines.delay
import studio.nxtech.fujubank.BuildKonfig
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
    // [UserRepository] と同じ規約で `BuildKonfig.USE_DUMMY_PROFILE` をデフォルトに採用し、
    // 通信を伴わない UI 確認モードを切替える。テスト・本番では false を渡して無効化する。
    val useDummyData: Boolean = BuildKonfig.USE_DUMMY_PROFILE,
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
        if (useDummyData) {
            return dummyTransfer(amount = amount, idempotencyKey = idempotencyKey)
        }
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

    /**
     * UI 確認用フェイク送金。`useDummyData=true` のときだけ呼ばれる。
     *
     * - 通信が走らないので loading 状態を観察できるよう少しだけ待つ。
     * - 失敗パターンの UI 確認用に、特定の入力で擬似的にエラーを返すロジックを入れる。
     *   - amount が極端に大きい場合 (>9_999_999) → `INSUFFICIENT_BALANCE`
     *   - amount が 0 以下 → `VALIDATION_FAILED`
     */
    private suspend fun dummyTransfer(amount: Long, idempotencyKey: String): TransferResult {
        delay(300)
        return when {
            amount <= 0L -> TransferResult.Failure(
                ApiError(
                    code = ApiErrorCode.VALIDATION_FAILED,
                    message = "amount must be positive",
                    httpStatus = 400,
                ),
            )
            amount > DUMMY_MAX_BALANCE -> TransferResult.Failure(
                ApiError(
                    code = ApiErrorCode.INSUFFICIENT_BALANCE,
                    message = "balance is not enough (dummy)",
                    httpStatus = 422,
                ),
            )
            else -> TransferResult.Success(
                // ダミーの「送金後残高」。ホーム画面のダミー残高 (1_234_567) から amount を
                // 引いた値を返し、UI 上の整合を雰囲気で揃える。負にはならない範囲。
                newBalance = (DUMMY_BALANCE_BEFORE - amount).coerceAtLeast(0L),
                // Idempotency-Key の先頭 8 文字を使ってダミー txn ID を生成する。実装上
                // 同じキーで再 submit すれば同じ ID が返り、UI の重複表示確認にも使える。
                transactionId = "txn_dummy_send_${idempotencyKey.take(8)}",
            )
        }
    }

    private companion object {
        // ProfileRepository.DUMMY_PROFILE.balanceFuju と同じ値。差分計算で残高表示の整合を取る。
        const val DUMMY_BALANCE_BEFORE: Long = 1_234_567L

        // ダミーモードで「残高超過扱い」にする閾値。9_999_999 fuju 超を不足扱い。
        const val DUMMY_MAX_BALANCE: Long = 9_999_999L
    }
}

sealed class TransferResult {
    data class Success(val newBalance: Long, val transactionId: String) : TransferResult()
    data class MfaRequired(val retryKey: String) : TransferResult()
    data class Failure(val error: ApiError) : TransferResult()
}
