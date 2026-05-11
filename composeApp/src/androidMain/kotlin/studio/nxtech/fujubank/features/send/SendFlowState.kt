package studio.nxtech.fujubank.features.send

import androidx.compose.runtime.Immutable
import studio.nxtech.fujubank.domain.model.UserSearchResult

/**
 * 送金フロー全体の UI 状態。
 *
 * Step 1（送金先選択）と Step 2（金額入力）を 1 つの ViewModel + 1 つの sealed state で
 * 表現することで、戻る / キャンセル / MFA 経由の再送金を retryKey とともに簡潔に扱う。
 *
 * - [step] は現在表示している画面 (`Step.Recipient` / `Step.Amount`)。
 *   sub modal （bottom sheet / AlertDialog）の表示は [confirmCandidate] / [showAmountConfirm]
 *   フラグで上に重ねる。
 * - [recipient] は Step 1 で確定した宛先。Step 2 へ遷移したあとは null にしない。
 * - [submission] は実際に `LedgerRepository.transfer` を呼んだあとの結果。
 */
@Immutable
data class SendFlowState(
    val step: Step = Step.Recipient,
    val query: String = "",
    // ViewModel が現在の検索クエリで kick した検索の状態。
    val searchState: SearchState = SearchState.Idle,
    // bottom sheet で表示中の確認候補。null の場合 sheet は閉じる。
    val confirmCandidate: UserSearchResult? = null,
    val recipient: UserSearchResult? = null,
    val balance: Long = 0L,
    // 金額入力ボックスの現在値。0 は「未入力」相当として扱い、CTA を非活性にする。
    val amount: Long = 0L,
    // Step 2 の AlertDialog 表示中フラグ。
    val showAmountConfirm: Boolean = false,
    val submission: Submission = Submission.Idle,
    val error: String? = null,
) {
    enum class Step { Recipient, Amount }

    @Immutable
    sealed class SearchState {
        /** 入力 2 文字未満 / 検索未開始。 */
        data object Idle : SearchState()

        /** 「2 文字以上で検索してください」のヒントを出す状態。 */
        data object NeedsMoreChars : SearchState()

        /**
         * 英数字以外を含む / 32 文字超など、サーバ側 public_id 仕様 (`/\A[a-zA-Z0-9]+\z/` 2..32) を
         * 満たさないクエリ。検索 API は発火させず、UI 側でヒントを出して入力修正を促す。
         */
        data object InvalidChars : SearchState()

        data object Loading : SearchState()
        data class Ready(val results: List<UserSearchResult>) : SearchState()
        data class Error(val message: String) : SearchState()
    }

    @Immutable
    sealed class Submission {
        data object Idle : Submission()
        data object Submitting : Submission()
        data class MfaRequired(val retryKey: String) : Submission()
        data class Success(val transactionId: String, val newBalance: Long) : Submission()
    }
}
