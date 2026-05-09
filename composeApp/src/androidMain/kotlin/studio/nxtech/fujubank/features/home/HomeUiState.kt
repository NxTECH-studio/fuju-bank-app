package studio.nxtech.fujubank.features.home

import studio.nxtech.fujubank.domain.model.UserProfile
import studio.nxtech.fujubank.features.home.components.RecentTransactionItem

/**
 * ホーム画面の表示状態。
 *
 * - [Loading]: 初回 fetch 中。
 * - [Loaded]: プロフィール取得済み。`revealed` は残高マスク解除フラグ。
 *   `refreshing` は pull-to-refresh で再取得中の表示用。
 *   `recentTransactions` は最近の取引セクションの内部状態（profile と独立に進む）。
 * - [Error]: 通信失敗 or サーバーエラー。`message` は日本語化済み。
 */
sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Loaded(
        val profile: UserProfile,
        val revealed: Boolean = false,
        val refreshing: Boolean = false,
        val recentTransactions: RecentTransactionsState = RecentTransactionsState.Loading,
    ) : HomeUiState

    data class Error(val message: String) : HomeUiState
}

/**
 * ホーム画面「最近の取引」セクションの内部状態。
 *
 * profile 取得とは独立に進行する。Recent 側の失敗は profile が成功していればホーム本体を
 * 落とさず、セクション内に「読み込めませんでした」+ 再試行ボタンを表示する。
 */
sealed interface RecentTransactionsState {
    data object Loading : RecentTransactionsState

    data class Ready(val items: List<RecentTransactionItem>) : RecentTransactionsState

    data class Error(val message: String) : RecentTransactionsState
}
