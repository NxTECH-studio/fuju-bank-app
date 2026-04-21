package studio.nxtech.fujubank.network

/**
 * Ktor Auth プラグインの `refreshTokens` ブロックから呼び出す関数インターフェース。
 * `AuthApi` を network レイヤーから直接参照しないためのフック点。
 *
 * MFA_REQUIRED など refresh では解決しないエラーの場合は null を返し、
 * Ktor 側の再試行ループに入らないようにする。
 */
fun interface AuthTokenRefresher {
    suspend fun refresh(refreshToken: String): RefreshedTokens?
}

data class RefreshedTokens(
    val accessToken: String,
    val refreshToken: String,
)
