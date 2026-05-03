package studio.nxtech.fujubank.features.transactions

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import studio.nxtech.fujubank.domain.model.Transaction

/**
 * 取引詳細画面の状態を提供する ViewModel。
 *
 * - リスト画面で選択された [Transaction] を引数で受け取り、そのまま [TransactionDetailUiState.Loaded] で公開する
 * - リフレッシュ / 再取得は持たない（一覧画面側のリフレッシュで対応）
 *
 * 将来、サーバーから詳細メタデータ（感情データ等）を別途取得する場合はこの VM に load を追加する。
 */
class TransactionDetailViewModel(
    transaction: Transaction,
) : ViewModel() {

    private val _state = MutableStateFlow<TransactionDetailUiState>(
        TransactionDetailUiState.Loaded(transaction = transaction),
    )
    val state: StateFlow<TransactionDetailUiState> = _state.asStateFlow()
}
