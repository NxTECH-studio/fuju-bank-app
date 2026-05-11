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

    /**
     * 認証完了状態。bank 側 user 行と AuthCore ULID の両方が手元にある。
     *
     * - [userId]: AuthCore の ULID (= bank の `external_user_id`、26 文字 Crockford Base32)。
     *   `POST /ledger/transfer` の `from_user_id` / `to_user_id` や AuthCore 横断 API に渡す。
     * - [bankUserId]: bank 内部の user 主キーを文字列化した値。`GET /users/:id/transactions`
     *   のように bank 内部識別子 (`:id` = bank PK) を URL に embed するエンドポイントに渡す。
     *
     * 用途で使い分けるため、誤って `userId` を bank 内部経路に流すと 404 / 識別子流出を招く。
     * 取引履歴系は [bankUserId]、送金 / AuthCore 系は [userId] と覚える。
     */
    data class Authenticated(
        val userId: String,
        val bankUserId: String,
    ) : SessionState()
}
