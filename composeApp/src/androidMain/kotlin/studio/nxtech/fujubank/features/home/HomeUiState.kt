package studio.nxtech.fujubank.features.home

import studio.nxtech.fujubank.domain.model.UserProfile

/**
 * ホーム画面の表示状態。
 *
 * - [Loading]: 初回 fetch 中。
 * - [Loaded]: プロフィール取得済み。`revealed` は残高マスク解除フラグ。
 *   `refreshing` は pull-to-refresh で再取得中の表示用。
 * - [Error]: 通信失敗 or サーバーエラー。`message` は日本語化済み。
 */
sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Loaded(
        val profile: UserProfile,
        val revealed: Boolean = false,
        val refreshing: Boolean = false,
    ) : HomeUiState

    data class Error(val message: String) : HomeUiState
}
