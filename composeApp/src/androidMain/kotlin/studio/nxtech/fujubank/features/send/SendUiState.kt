package studio.nxtech.fujubank.features.send

/**
 * 送金画面の表示状態。
 *
 * 入力中 / 確認ダイアログ表示中 / 送信中 / 結果表示 を 1 つの data class に集約し、
 * State の遷移を `copy()` で表現する。理由は:
 * - 入力値（[recipientExternalId] / [amountFuju]）は確認・送信中も保持したい
 * - 結果イベント（成功 Snackbar / エラーメッセージ）は表示後に明示的にクリアしたい
 *
 * @property recipientExternalId 受取人の外部 ID 入力値（trim 前の生）
 * @property amountFuju 金額入力値（数字のみ allow、空文字も可）
 * @property phase 進捗ステップ
 * @property errorMessage 直近の API エラーメッセージ（null = 未表示）
 * @property successEvent 直近の成功イベント（null = 未発生 or 表示済み）
 */
data class SendUiState(
    val recipientExternalId: String = "",
    val amountFuju: String = "",
    val phase: Phase = Phase.Editing,
    val errorMessage: String? = null,
    val successEvent: SendSuccessEvent? = null,
) {
    /** 数字のみ受理して Long に変換する。空文字 / 桁あふれは null。 */
    val parsedAmount: Long?
        get() = amountFuju.toLongOrNull()?.takeIf { it > 0 }

    /** submit 可能か（受取人が空でなく、金額が正の整数で、Editing 状態）。 */
    val canSubmit: Boolean
        get() = phase == Phase.Editing &&
            recipientExternalId.isNotBlank() &&
            parsedAmount != null

    enum class Phase {
        /** 入力中。フォーム編集とボタン押下が可能。 */
        Editing,

        /** 確認ダイアログ表示中。最終 submit 待ち。 */
        Confirming,

        /** API 通信中。フォーム / ボタンを無効化する（二重 submit 防止）。 */
        Submitting,
    }
}

/**
 * 送金成功時に UI 側へ 1 度だけ伝えるイベント。
 * 表示後は ViewModel の [SendUiState.successEvent] を null にクリアして再発火を防ぐ。
 */
data class SendSuccessEvent(
    val newBalance: Long,
    val transactionId: String,
)
