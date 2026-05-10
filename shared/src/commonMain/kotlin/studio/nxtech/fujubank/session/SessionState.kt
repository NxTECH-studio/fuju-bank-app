package studio.nxtech.fujubank.session

/**
 * アプリ全体のセッション状態。
 *
 * UI（iOS / Android）はこの値を観測してログイン画面 / MFA 画面 / ホームを出し分ける。
 * - [Unauthenticated]: 未ログイン。LoginView を表示する。
 * - [MfaPending]: 1 段階目の認証が通り MFA 入力待ち。MfaVerifyView を表示する。
 * - [MfaSetupRequired]: ログインに成功したが `mfa_enabled = false` のためセットアップが必要。
 *   MFA セットアップ画面群（QR 表示 → コード入力 → recovery codes）に誘導する。
 *   既に access_token は発行済みなので、再ログインせずに mfa/register を叩ける。
 * - [Authenticated]: アクセストークン取得済み（bank 側 user 行も provision 済み）。
 */
sealed class SessionState {
    object Unauthenticated : SessionState()

    data class MfaPending(val preToken: String) : SessionState()

    object MfaSetupRequired : SessionState()

    data class Authenticated(val userId: String) : SessionState()
}
