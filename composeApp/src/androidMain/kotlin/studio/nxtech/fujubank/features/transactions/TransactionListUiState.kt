package studio.nxtech.fujubank.features.transactions

import studio.nxtech.fujubank.domain.model.Transaction

/**
 * 取引履歴画面の表示状態。
 *
 * - [Loading]: 初回 fetch 中。
 * - [Loaded]: 取得成功。`items` は時系列降順済み。`refreshing` は pull-to-refresh 中フラグ。
 * - [Error]: API or 通信エラー。`message` は表示用日本語。
 *
 * [HomeUiState] と同じパターンで `sealed interface` + `data object` を採用する。
 */
sealed interface TransactionListUiState {
    data object Loading : TransactionListUiState

    data class Loaded(
        val items: List<Transaction>,
        val refreshing: Boolean = false,
    ) : TransactionListUiState

    data class Error(val message: String) : TransactionListUiState
}
