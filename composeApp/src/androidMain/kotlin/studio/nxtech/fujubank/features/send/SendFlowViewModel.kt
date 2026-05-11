package studio.nxtech.fujubank.features.send

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import studio.nxtech.fujubank.data.remote.ApiErrorCode
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.repository.LedgerRepository
import studio.nxtech.fujubank.data.repository.ProfileRepository
import studio.nxtech.fujubank.data.repository.TransferResult
import studio.nxtech.fujubank.data.repository.UserRepository
import studio.nxtech.fujubank.domain.model.UserSearchResult
import studio.nxtech.fujubank.session.SessionState
import studio.nxtech.fujubank.session.SessionStore

/**
 * 送金フロー（Step 1: 送金先選択 → Step 2: 金額入力 → 実行）の状態とアクションを集約する ViewModel。
 *
 * ライフサイクル: 送金フロー destination 群（`SendRecipient` / `SendAmount`）の間で **同一 VM を
 * 引き回す** 設計。`RootScaffold` 側で同じ key で `viewModel(...)` を呼び、Step 切替で再生成しない。
 */
class SendFlowViewModel(
    private val userRepository: UserRepository,
    private val ledgerRepository: LedgerRepository,
    private val profileRepository: ProfileRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SendFlowState())
    val state: StateFlow<SendFlowState> = _state.asStateFlow()

    // 検索クエリ用の Flow。debounce してから API を叩く。
    private val queryFlow = MutableStateFlow("")

    init {
        // 初期残高をホームと同じ ProfileRepository から取得する。失敗時は 0 のまま、
        // CTA は「残高超過」として扱われ disable される（実 API 側でも検証されるので二重防御）。
        loadBalance()
        observeQuery()
    }

    private fun loadBalance() {
        viewModelScope.launch {
            when (val result = profileRepository.getMyProfile()) {
                is NetworkResult.Success -> _state.update { it.copy(balance = result.value.balanceFuju) }
                is NetworkResult.Failure, is NetworkResult.NetworkFailure -> Unit
            }
        }
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun observeQuery() {
        viewModelScope.launch {
            // `collectLatest` で前検索のレスポンス待ちを打ち切る。`runSearch` 内で
            // `userRepository.searchByPublicId` が suspend 中に新クエリが来た場合、collect だと
            // 前検索完了まで次の値を読み始めないため UI 体感が遅れる。collectLatest なら
            // 新値到着時点で前 collector を cancel して即新検索に切り替えられる。
            queryFlow
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collectLatest { q -> runSearch(q) }
        }
    }

    private suspend fun runSearch(query: String) {
        // クエリ分類は `shared/commonMain` 側に集約 (Android / iOS 共通)。サーバ側 public_id 仕様
        // (`/\A[a-zA-Z0-9]+\z/` 2..32) との同期は `classifySendSearchQuery` で 1 元管理する。
        when (classifySendSearchQuery(query)) {
            SendSearchQueryClassification.EMPTY -> {
                _state.update { it.copy(searchState = SendFlowState.SearchState.Idle) }
                return
            }
            SendSearchQueryClassification.TOO_SHORT -> {
                _state.update { it.copy(searchState = SendFlowState.SearchState.NeedsMoreChars) }
                return
            }
            SendSearchQueryClassification.INVALID -> {
                _state.update { it.copy(searchState = SendFlowState.SearchState.InvalidChars) }
                return
            }
            SendSearchQueryClassification.VALID -> Unit
        }
        val trimmed = query.trim()
        _state.update { it.copy(searchState = SendFlowState.SearchState.Loading) }
        when (val result = userRepository.searchByPublicId(trimmed)) {
            is NetworkResult.Success -> _state.update {
                it.copy(searchState = SendFlowState.SearchState.Ready(result.value))
            }
            is NetworkResult.Failure -> _state.update {
                val msg = if (result.error.code == ApiErrorCode.RATE_LIMIT_EXCEEDED) {
                    "検索が混み合っています。しばらく待ってください"
                } else {
                    "検索に失敗しました"
                }
                it.copy(searchState = SendFlowState.SearchState.Error(msg))
            }
            is NetworkResult.NetworkFailure -> _state.update {
                it.copy(searchState = SendFlowState.SearchState.Error("通信エラーが発生しました"))
            }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        queryFlow.value = query
    }

    fun onCandidateTap(candidate: UserSearchResult) {
        _state.update { it.copy(confirmCandidate = candidate) }
    }

    fun onCandidateConfirmCancel() {
        _state.update { it.copy(confirmCandidate = null) }
    }

    /** Step 1 の bottom sheet で「決定」を押したあと、Step 2 に進む。 */
    fun onCandidateConfirm() {
        val candidate = _state.value.confirmCandidate ?: return
        _state.update {
            it.copy(
                confirmCandidate = null,
                recipient = candidate,
                step = SendFlowState.Step.Amount,
                amount = 0L,
                memo = "",
                error = null,
                submission = SendFlowState.Submission.Idle,
            )
        }
    }

    /** Step 2 から戻る場合（Step 1 に戻り、recipient と amount をリセットする）。 */
    fun onAmountBack() {
        _state.update {
            it.copy(
                step = SendFlowState.Step.Recipient,
                recipient = null,
                amount = 0L,
                memo = "",
                error = null,
                submission = SendFlowState.Submission.Idle,
            )
        }
    }

    /**
     * memo の入力変更ハンドラ。80 文字を超える入力はクライアント側で**超過分を切り捨て**、
     * サーバ側 VALIDATION_FAILED の再現を避ける。
     *
     * 文字数は `String.length`（UTF-16 code unit）でカウントする。サロゲートペア（絵文字）は
     * 2 としてカウントされる Kotlin 側既定動作を許容し、サーバ側仕様 (80 文字上限) と同じ
     * 単位で揃える前提。
     */
    fun onMemoChange(value: String) {
        val truncated = if (value.length > MEMO_MAX_LENGTH) value.take(MEMO_MAX_LENGTH) else value
        _state.update { it.copy(memo = truncated) }
    }

    /**
     * 金額入力。OS 標準の数字キーボード IME 経由で `OutlinedTextField` から呼ばれる。
     * 巨大値は内部で打ち切らない（CTA 側で「残高超過」として disable する）。Long 範囲外は
     * UI 側で `toLongOrNull()` が null になり 0 扱いになるため、ここまで届かない前提。
     */
    fun onAmountChange(value: Long) {
        _state.update { it.copy(amount = value, error = null) }
    }

    fun showAmountConfirm() {
        val s = _state.value
        if (s.recipient == null || s.amount <= 0L || s.amount > s.balance) return
        _state.update { it.copy(showAmountConfirm = true) }
    }

    fun dismissAmountConfirm() {
        _state.update { it.copy(showAmountConfirm = false) }
    }

    /** AlertDialog の「送金する」CTA から呼ばれる。 */
    fun submit() {
        val snapshot = _state.value
        // 送金中 / 送金成功直後の二重 submit を防御。`Submission.Success` の経路は通常 UI 側で
        // ホーム遷移してから ViewModel が破棄されるため到達しないが、再入のレース対策として
        // 明示ガードを残す（MfaRequired は再試行可能なのでガード対象外）。
        when (snapshot.submission) {
            SendFlowState.Submission.Submitting,
            is SendFlowState.Submission.Success,
            -> return
            SendFlowState.Submission.Idle,
            is SendFlowState.Submission.MfaRequired,
            -> Unit
        }
        val recipient = snapshot.recipient ?: return
        val from = (sessionStore.current as? SessionState.Authenticated)?.userId ?: run {
            _state.update {
                it.copy(
                    showAmountConfirm = false,
                    error = "セッションが無効です。もう一度ログインしてください",
                )
            }
            return
        }
        // MFA / NetworkFailure の再試行を考慮し、前回の retryKey があれば引き継ぐ。
        val retryKey = (snapshot.submission as? SendFlowState.Submission.MfaRequired)?.retryKey
        _state.update {
            it.copy(
                showAmountConfirm = false,
                submission = SendFlowState.Submission.Submitting,
                error = null,
            )
        }
        // 空文字 / 空白のみの memo は null に正規化してサーバへ送る。
        // 一部のサーバ実装で空文字が persist される懸念を避けるため。
        val memo = snapshot.memo.takeIf { it.isNotBlank() }
        viewModelScope.launch {
            val result = ledgerRepository.transfer(
                from = from,
                to = recipient.id,
                amount = snapshot.amount,
                memo = memo,
                retryKey = retryKey,
            )
            applyTransferResult(result)
        }
    }

    private fun applyTransferResult(result: TransferResult) {
        when (result) {
            is TransferResult.Success -> _state.update {
                it.copy(
                    submission = SendFlowState.Submission.Success(
                        transactionId = result.transactionId,
                        newBalance = result.newBalance,
                    ),
                    balance = result.newBalance,
                )
            }
            is TransferResult.MfaRequired -> _state.update {
                // 送金時 MFA 検証 API はサーバ側で別 PR が必要 (server-bank-23)。
                // クライアントは現状フォールバックとして「未対応エラー」を表示し、retryKey を保持する。
                it.copy(
                    submission = SendFlowState.Submission.MfaRequired(retryKey = result.retryKey),
                    error = "送金時の二段階認証は現在準備中です。後ほど再度お試しください",
                )
            }
            is TransferResult.Failure -> {
                val message = mapFailureMessage(result.error.code)
                if (result.error.code == ApiErrorCode.NOT_FOUND) {
                    // 宛先が消えた等。Step 1 に戻して再検索を促す。
                    _state.update {
                        it.copy(
                            step = SendFlowState.Step.Recipient,
                            recipient = null,
                            amount = 0L,
                            submission = SendFlowState.Submission.Idle,
                            error = message,
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            submission = SendFlowState.Submission.Idle,
                            error = message,
                        )
                    }
                }
            }
        }
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

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        const val MEMO_MAX_LENGTH = 80
    }
}
