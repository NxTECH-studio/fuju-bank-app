package studio.nxtech.fujubank.network

/**
 * Ktor Auth プラグインの `refreshTokens` ブロックから呼び出す関数インターフェース。
 * `AuthApi` を network レイヤーから直接参照しないためのフック点。
 *
 * AuthCore は refresh_token を HttpOnly cookie で配送するため、refresh では
 * 引数を取らず（cookie が自動で乗る）、新しい access token のみを返す。
 *
 * MFA_REQUIRED / TOKEN_REVOKED など refresh では解決しないエラーの場合は null を
 * 返し、Ktor 側の再試行ループに入らず SessionStore を Unauthenticated に倒す。
 */
fun interface AuthTokenRefresher {
    suspend fun refresh(): String?
}
