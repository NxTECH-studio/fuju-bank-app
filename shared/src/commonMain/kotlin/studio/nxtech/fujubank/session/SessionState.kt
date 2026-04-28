package studio.nxtech.fujubank.session

/**
 * アプリ全体のセッション状態。
 *
 * UI（iOS / Android）はこの値を観測してログイン画面 / MFA 画面 / ホームを出し分ける。
 * - [Unauthenticated]: 未ログイン。LoginView を表示する。
 * - [MfaPending]: 1 段階目の認証が通り MFA 入力待ち。MfaVerifyView を表示する。
 * - [Authenticated]: アクセストークン取得済み（bank 側 user 行も provision 済み）。
 */
sealed class SessionState {
    object Unauthenticated : SessionState()

    data class MfaPending(val preToken: String) : SessionState()

    data class Authenticated(val userId: String) : SessionState()
}
