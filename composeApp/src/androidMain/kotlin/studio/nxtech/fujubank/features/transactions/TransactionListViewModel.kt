package studio.nxtech.fujubank.features.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.repository.UserRepository
import studio.nxtech.fujubank.session.SessionState
import studio.nxtech.fujubank.session.SessionStore

/**
 * 取引履歴画面の状態とアクションを束ねる ViewModel。
 *
 * - 起動時に [SessionStore] から自分の `userId` を取得して取引一覧を fetch する。
 * - `refresh()` で再取得（pull-to-refresh 用）。
 *
 * MVP では pagination を持たず、サーバー側のデフォルト件数を全件表示する。
 */
class TransactionListViewModel(
    private val userRepository: UserRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow<TransactionListUiState>(TransactionListUiState.Loading)
    val state: StateFlow<TransactionListUiState> = _state.asStateFlow()

    // 進行中の fetch Job。新しい load() で前回をキャンセルして後勝ち上書きを防ぐ。
    private var loadJob: Job? = null

    init {
        load(initial = true)
    }

    fun refresh() {
        load(initial = false)
    }

    private fun load(initial: Boolean) {
        loadJob?.cancel()
        if (!initial) {
            _state.update { current ->
                if (current is TransactionListUiState.Loaded) current.copy(refreshing = true) else current
            }
        }
        loadJob = viewModelScope.launch {
            val sessionUserId = (sessionStore.current as? SessionState.Authenticated)?.userId
            // ダミーモードでは Repository が userId を無視してフェイクデータを返すため、
            // セッション未確立でも UI 確認のために空文字でフォールスルーさせる。
            if (sessionUserId == null && !userRepository.useDummyData) {
                _state.value = TransactionListUiState.Error(message = "セッションが切れました")
                return@launch
            }
            val userId = sessionUserId ?: ""
            when (val result = userRepository.transactions(userId)) {
                is NetworkResult.Success -> _state.value = TransactionListUiState.Loaded(
                    items = result.value.sortedByDescending { it.occurredAt },
                )
                is NetworkResult.Failure -> _state.value = TransactionListUiState.Error(
                    message = "取引履歴を取得できませんでした",
                )
                is NetworkResult.NetworkFailure -> _state.value = TransactionListUiState.Error(
                    message = "通信エラーが発生しました",
                )
            }
        }
    }
}
