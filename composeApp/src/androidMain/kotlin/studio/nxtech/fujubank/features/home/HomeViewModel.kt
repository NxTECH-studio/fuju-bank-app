package studio.nxtech.fujubank.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.repository.ProfileRepository
import studio.nxtech.fujubank.data.repository.UserRepository
import studio.nxtech.fujubank.domain.model.Transaction
import studio.nxtech.fujubank.domain.model.TransactionDirection
import studio.nxtech.fujubank.domain.model.UserProfile
import studio.nxtech.fujubank.features.home.components.RecentTransactionItem
import studio.nxtech.fujubank.features.transactions.SHORT_ID_LEN
import studio.nxtech.fujubank.session.SessionState
import studio.nxtech.fujubank.session.SessionStore
import studio.nxtech.fujubank.util.formatTransactionDateTimeSlash

/**
 * ホーム画面の状態とアクションを束ねる ViewModel。
 *
 * - 起動時に `ProfileRepository.getMyProfile()` と最近の取引取得を並列で叩く。
 *   片方が失敗してももう片方は活かす設計（profile 失敗 → 全画面エラー、
 *   transactions 失敗 → セクション内エラーのみ）。
 * - 残高は初期マスク。`toggleReveal()` で表示／非表示を切り替える。
 * - `refresh()` で全体再取得（pull-to-refresh 用）、`refreshRecent()` で最近の取引のみ再取得。
 *
 * TODO(A6): realtimeRepository.events を collect して残高ライブ更新する slot
 */
class HomeViewModel(
    private val profileRepository: ProfileRepository,
    private val userRepository: UserRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    // 進行中の fetch Job。新しい load() 開始前にキャンセルし、refresh 連打で
    // 古い結果が後勝ちで state を上書きしないようにする。
    private var loadJob: Job? = null
    // 最近の取引のみ再取得する Job。`refreshRecent()` で使う。
    private var recentJob: Job? = null

    init {
        load(initial = true)
    }

    fun toggleReveal() {
        _state.update { current ->
            if (current is HomeUiState.Loaded) current.copy(revealed = !current.revealed) else current
        }
    }

    fun refresh() {
        load(initial = false)
    }

    /** 最近の取引セクションだけ再取得する。エラー時の再試行ボタンから呼ばれる。 */
    fun refreshRecent() {
        recentJob?.cancel()
        _state.update { current ->
            if (current is HomeUiState.Loaded) {
                current.copy(recentTransactions = RecentTransactionsState.Loading)
            } else {
                current
            }
        }
        recentJob = viewModelScope.launch {
            val recent = fetchRecentTransactions()
            _state.update { current ->
                if (current is HomeUiState.Loaded) current.copy(recentTransactions = recent) else current
            }
        }
    }

    private fun load(initial: Boolean) {
        loadJob?.cancel()
        recentJob?.cancel()
        if (!initial) {
            _state.update { current ->
                when (current) {
                    is HomeUiState.Loaded -> current.copy(
                        refreshing = true,
                        recentTransactions = RecentTransactionsState.Loading,
                    )
                    else -> current
                }
            }
        }
        loadJob = viewModelScope.launch {
            // 片方が失敗してももう片方を待ってから state を確定させたいので、
            // coroutineScope で 2 本を並列 await する（recent 側は内部で例外を握るため
            // ここで scope 全体が落ちることはない）。
            val (profileResult, recentResult) = coroutineScope {
                val p = async { profileRepository.getMyProfile() }
                val t = async { fetchRecentTransactions() }
                p.await() to t.await()
            }
            applyResults(profileResult, recentResult)
        }
    }

    private fun applyResults(
        profileResult: NetworkResult<UserProfile>,
        recentResult: RecentTransactionsState,
    ) {
        when (profileResult) {
            is NetworkResult.Success -> _state.update { current ->
                when (current) {
                    is HomeUiState.Loaded -> current.copy(
                        profile = profileResult.value,
                        refreshing = false,
                        recentTransactions = recentResult,
                    )
                    else -> HomeUiState.Loaded(
                        profile = profileResult.value,
                        recentTransactions = recentResult,
                    )
                }
            }
            is NetworkResult.Failure -> _state.update {
                HomeUiState.Error(message = "プロフィールを取得できませんでした")
            }
            is NetworkResult.NetworkFailure -> _state.update {
                HomeUiState.Error(message = "通信エラーが発生しました")
            }
        }
    }

    /**
     * 最近の取引を取得し、表示用の `RecentTransactionsState` に変換する。
     *
     * 未認証 / 空の userId / API 失敗 / 通信失敗はすべて `Error` に集約してホーム本体は落とさない。
     * ダミーモードでは Repository が userId を無視するため、空文字でフォールスルーさせる。
     */
    private suspend fun fetchRecentTransactions(): RecentTransactionsState {
        val sessionUserId = (sessionStore.current as? SessionState.Authenticated)?.userId
        if (sessionUserId == null && !userRepository.useDummyData) {
            return RecentTransactionsState.Error(message = "最近の取引を取得できませんでした")
        }
        val userId = sessionUserId ?: ""
        return when (val result = userRepository.transactions(userId)) {
            is NetworkResult.Success -> {
                val items = result.value
                    .sortedByDescending { it.occurredAt }
                    .take(RECENT_LIMIT)
                    .map { it.toRecentItem() }
                RecentTransactionsState.Ready(items = items)
            }
            is NetworkResult.Failure,
            is NetworkResult.NetworkFailure,
            -> RecentTransactionsState.Error(message = "最近の取引を取得できませんでした")
        }
    }

    private companion object {
        const val RECENT_LIMIT = 3
    }
}

/**
 * ドメイン `Transaction` をホーム表示用 `RecentTransactionItem` に変換する。
 *
 * タイトルは `TransactionRow` の `TransactionRowVariant.from` と同じロジック
 * （Mint=「アーティファクト xxxxxx」/ Incoming=「xxxxxx からもらいました」/ Outgoing=「xxxxxx に送りました」）で組み立てる。
 * sign / 金額色は `RecentTransactionItem.direction` から派生させるため、ここでは持たせない。
 */
private fun Transaction.toRecentItem(): RecentTransactionItem {
    val title = when (direction) {
        TransactionDirection.Mint -> artifactId
            ?.let { "アーティファクト ${it.takeLast(SHORT_ID_LEN)}" }
            ?: "発行"
        TransactionDirection.Incoming -> counterpartyUserId
            ?.let { "${it.takeLast(SHORT_ID_LEN)} からもらいました" }
            ?: "入金"
        TransactionDirection.Outgoing -> counterpartyUserId
            ?.let { "${it.takeLast(SHORT_ID_LEN)} に送りました" }
            ?: "送金"
    }
    return RecentTransactionItem(
        title = title,
        amount = amount,
        direction = direction,
        timestamp = formatTransactionDateTimeSlash(occurredAt),
    )
}
