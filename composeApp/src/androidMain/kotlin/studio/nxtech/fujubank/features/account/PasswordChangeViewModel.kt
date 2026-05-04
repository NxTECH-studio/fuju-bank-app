package studio.nxtech.fujubank.features.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * パスワード変更画面（Figma `799:13327`）の状態とアクションを束ねる ViewModel。
 *
 * バックエンド API は未提供のため、[submit] は疑似遅延ののち成功扱いとする。
 * 実 API が提供された後は、`submit` 内のロジックを `commonMain` の UseCase 呼び出しに
 * 差し替える想定（クライアントの公開 API を変えずに済むよう、入出力を 1 箇所にまとめる）。
 *
 * 入力値はセキュリティ観点で `rememberSaveable`（Bundle 永続化）を避け、
 * ViewModel の `StateFlow` 内に保持する。プロセス再生成時には消える方針。
 */
class PasswordChangeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PasswordChangeUiState())
    val uiState: StateFlow<PasswordChangeUiState> = _uiState.asStateFlow()

    fun onCurrentChange(value: String) {
        _uiState.update { it.copy(current = value, errorMessage = null) }
    }

    fun onNewChange(value: String) {
        _uiState.update { it.copy(newPassword = value, errorMessage = null) }
    }

    fun onConfirmChange(value: String) {
        _uiState.update { it.copy(confirm = value, errorMessage = null) }
    }

    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            // バックエンド連携の疑似遅延。実 API 接続時はここを差し替える。
            delay(SUBMIT_DELAY_MS)
            _uiState.update { it.copy(isSubmitting = false, isSubmitted = true) }
        }
    }

    /**
     * 成功イベントを Screen 側が消費し終わったら呼ぶ。
     * ViewModel は Activity スコープに残るため、再訪時に `isSubmitted` が true のままだと
     * `LaunchedEffect` が再発火して即座に戻ってしまう。消費後に false に戻して再入力を可能にする。
     */
    fun consumeSubmitted() {
        _uiState.update {
            PasswordChangeUiState()
        }
    }

    private companion object {
        const val SUBMIT_DELAY_MS = 800L
    }
}

/**
 * パスワード変更画面の UI 状態。
 *
 * - [current] / [newPassword] / [confirm]: 3 入力欄
 * - [isSubmitting]: 送信中フラグ。ボタン disabled に使う
 * - [errorMessage]: ローカル整合性エラーやサーバーエラーの表示用（現状は表示領域のみ確保）
 * - [isSubmitted]: 送信成功で `true`。Screen 側で `LaunchedEffect` 監視し戻る
 */
data class PasswordChangeUiState(
    val current: String = "",
    val newPassword: String = "",
    val confirm: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val isSubmitted: Boolean = false,
) {
    /**
     * 保存ボタンを押せる条件:
     * - 3 欄すべて非空
     * - 新パスワードが現在のパスワードと異なる
     * - 新パスワードと確認用が一致
     * - 送信中でない
     */
    val canSubmit: Boolean
        get() = !isSubmitting &&
            current.isNotEmpty() &&
            newPassword.isNotEmpty() &&
            confirm.isNotEmpty() &&
            newPassword != current &&
            newPassword == confirm
}
