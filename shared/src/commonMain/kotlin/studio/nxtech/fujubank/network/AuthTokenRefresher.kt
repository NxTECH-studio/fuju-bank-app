package studio.nxtech.fujubank.network

/**
 * Ktor Auth プラグインの `refreshTokens` ブロックから呼び出す関数インターフェース。
 * `AuthApi` を network レイヤーから直接参照しないためのフック点。
 *
 * AuthCore は refresh_token を HttpOnly cookie で配送するため、refresh では
 * 引数を取らず（cookie が自動で乗る）、新しい access token のみを返す。
 *
 * 失敗時の方針（実装は `authModule` 側に閉じ込めている）:
 * - API エラー応答（MFA_REQUIRED / TOKEN_REVOKED / refresh cookie 失効など）は
 *   refresh_family が revoke された可能性が高いため、TokenStorage と SessionStore を
 *   即クリアして UI を Unauthenticated に倒し、null を返す。
 * - 通信エラー（オフライン / DNS / タイムアウト）では clear せず null のみ返し、
 *   Ktor 側に再試行の余地を残す。
 */
fun interface AuthTokenRefresher {
    suspend fun refresh(): String?
}
