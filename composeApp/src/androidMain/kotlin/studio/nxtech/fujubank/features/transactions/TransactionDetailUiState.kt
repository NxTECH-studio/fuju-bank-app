package studio.nxtech.fujubank.features.transactions

import studio.nxtech.fujubank.domain.model.Transaction

/**
 * 取引詳細画面の表示状態。Figma `702:6440` 準拠。
 *
 * 現状はリスト画面で取得済みの [Transaction] をそのまま表示するだけのため
 * Loading / Error 状態は持たない（[Loaded] のみ）。将来 API で詳細取得する場合に拡張する。
 */
sealed interface TransactionDetailUiState {
    data class Loaded(val transaction: Transaction) : TransactionDetailUiState
}
