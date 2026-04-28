package studio.nxtech.fujubank.data.repository

/**
 * `AuthRepository.login()` の結果型。
 *
 * AuthCore は `POST /v1/auth/login` の成功レスポンスとして
 *  - access_token（200 + Set-Cookie: refresh_token）
 *  - pre_token + mfa_required: true（MFA 必須ユーザの場合）
 * のいずれかを返す。呼び出し側はこの sealed type を分岐して
 * 直接ホーム遷移するか MFA 入力画面に進むかを決める。
 */
sealed class LoginResult {
    data class Authenticated(
        val accessToken: String,
        val expiresIn: Long,
    ) : LoginResult()

    data class NeedsMfa(
        val preToken: String,
        val expiresIn: Long,
    ) : LoginResult()
}
