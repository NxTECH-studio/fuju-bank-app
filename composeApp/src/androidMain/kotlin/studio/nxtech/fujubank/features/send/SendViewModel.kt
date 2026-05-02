package studio.nxtech.fujubank.features.send

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import studio.nxtech.fujubank.data.remote.ApiErrorCode
import studio.nxtech.fujubank.data.repository.LedgerRepository
import studio.nxtech.fujubank.data.repository.TransferResult
import studio.nxtech.fujubank.session.SessionState
import studio.nxtech.fujubank.session.SessionStore

/**
 * 送金画面の状態とアクションを束ねる ViewModel。
 *
 * - 入力 → 確認ダイアログ → submit のシンプルな 3 段ステップ。
 * - submit 中は [SendUiState.Phase.Submitting] にして二重 submit を抑止する。
 *   `Idempotency-Key` は [LedgerRepository] が UUID を内部生成するため、ここでは MFA
 *   再試行用の retryKey 保持だけ行う（MVP では MFA UI は未実装、retryKey は将来用に確保）。
 * - 成功時は [SendSuccessEvent] を立てて UI 側でホームへ戻す導線を起動する。
 *
 * TODO(MFA): TransferResult.MfaRequired の扱いは別タスクで MFA verify 画面と接続する。
 */
class SendViewModel(
    private val ledgerRepository: LedgerRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SendUiState())
    val state: StateFlow<SendUiState> = _state.asStateFlow()

    // 進行中の submit Job。dispose 時にキャンセル。
    private var submitJob: Job? = null

    fun onRecipientChanged(value: String) {
        // 改行を弾く。前後空白は submit 時に trim する。
        val sanitized = value.replace("\n", "").replace("\r", "")
        _state.update { it.copy(recipientExternalId = sanitized, errorMessage = null) }
    }

    fun onAmountChanged(value: String) {
        // 数字のみ受理。先頭ゼロは入力途中で許容する（submit 時に Long 変換で吸収）。
        val sanitized = value.filter { it.isDigit() }
        _state.update { it.copy(amountFuju = sanitized, errorMessage = null) }
    }

    fun requestConfirm() {
        _state.update { current ->
            if (!current.canSubmit) current else current.copy(phase = SendUiState.Phase.Confirming)
        }
    }

    fun cancelConfirm() {
        _state.update { current ->
            if (current.phase == SendUiState.Phase.Confirming) {
                current.copy(phase = SendUiState.Phase.Editing)
            } else {
                current
            }
        }
    }

    fun submit() {
        val current = _state.value
        if (current.phase != SendUiState.Phase.Confirming) return
        val amount = current.parsedAmount ?: return
        val recipient = current.recipientExternalId.trim()
        if (recipient.isEmpty()) return

        submitJob?.cancel()
        _state.update { it.copy(phase = SendUiState.Phase.Submitting, errorMessage = null) }
        submitJob = viewModelScope.launch {
            val sessionUserId = (sessionStore.current as? SessionState.Authenticated)?.userId
            // ダミーモードでは Repository 側で from を見ない実装のため、空文字でフォールスルー。
            val fromUserId = sessionUserId ?: if (ledgerRepository.useDummyData) "" else null
            if (fromUserId == null) {
                _state.update {
                    it.copy(
                        phase = SendUiState.Phase.Editing,
                        errorMessage = "セッションが切れました",
                    )
                }
                return@launch
            }
            val result = ledgerRepository.transfer(
                from = fromUserId,
                to = recipient,
                amount = amount,
            )
            handleResult(result)
        }
    }

    /** Snackbar / エラーバナーが表示し終わった後に呼ぶ。 */
    fun consumeError() {
        _state.update { it.copy(errorMessage = null) }
    }

    /** 成功イベントを navigate-back に消費した後に呼ぶ（再発火防止）。 */
    fun consumeSuccess() {
        _state.update { it.copy(successEvent = null) }
    }

    private fun handleResult(result: TransferResult) {
        when (result) {
            is TransferResult.Success -> _state.update {
                it.copy(
                    phase = SendUiState.Phase.Editing,
                    successEvent = SendSuccessEvent(
                        newBalance = result.newBalance,
                        transactionId = result.transactionId,
                    ),
                    // 成功後は入力欄をクリアしておく。連続送金時のミス防止。
                    recipientExternalId = "",
                    amountFuju = "",
                )
            }
            is TransferResult.MfaRequired -> _state.update {
                // MVP では MFA verify 画面が未実装のため、エラーメッセージで誘導するだけ。
                it.copy(
                    phase = SendUiState.Phase.Editing,
                    errorMessage = "二段階認証が必要です（別タスクで実装予定）",
                )
            }
            is TransferResult.Failure -> _state.update {
                it.copy(
                    phase = SendUiState.Phase.Editing,
                    errorMessage = errorMessageFor(result.error.code),
                )
            }
        }
    }

    private fun errorMessageFor(code: ApiErrorCode): String = when (code) {
        ApiErrorCode.INSUFFICIENT_BALANCE -> "残高が足りません"
        ApiErrorCode.RECIPIENT_NOT_FOUND, ApiErrorCode.NOT_FOUND -> "送り先が見つかりません"
        ApiErrorCode.RATE_LIMIT_EXCEEDED -> "リクエストが多すぎます。少し時間をおいてください"
        ApiErrorCode.VALIDATION_FAILED -> "入力内容に誤りがあります"
        ApiErrorCode.UNAUTHENTICATED, ApiErrorCode.TOKEN_EXPIRED, ApiErrorCode.TOKEN_INVALID,
        ApiErrorCode.TOKEN_REVOKED, ApiErrorCode.TOKEN_INACTIVE -> "セッションが切れました"
        ApiErrorCode.UNKNOWN -> "通信エラーが発生しました"
        else -> "送金に失敗しました"
    }
}
