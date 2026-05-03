import Foundation
import Shared

/// 取引詳細画面の表示状態。Figma `702:6440` 準拠。
///
/// 現状はリスト画面で取得済みの [Shared.Transaction] をそのまま表示するだけのため
/// Loading / Error 状態は持たない（[loaded] のみ）。将来 API で詳細取得する場合に
/// `TransactionDetailFlowIos.fetchTransactionDetail` を経由した loading / error 状態を
/// 追加する。
enum TransactionDetailUiState {
    case loaded(transaction: Shared.Transaction)
}

/// 取引詳細画面の状態を提供する ViewModel。
///
/// - リスト画面で選択された [Shared.Transaction] を引数で受け取り、そのまま
///   `TransactionDetailUiState.loaded` で公開する。
/// - リフレッシュ / 再取得は持たない（一覧画面側のリフレッシュで対応）。
///
/// Android 側の `TransactionDetailViewModel`（ViewModel<KMP> ではなく Android 専用 VM）と
/// 同じ役割を Swift で持つ。`@MainActor` で UI スレッドに固定する。
@MainActor
final class TransactionDetailViewModel: ObservableObject {
    @Published private(set) var state: TransactionDetailUiState

    init(transaction: Shared.Transaction) {
        self.state = .loaded(transaction: transaction)
    }
}
