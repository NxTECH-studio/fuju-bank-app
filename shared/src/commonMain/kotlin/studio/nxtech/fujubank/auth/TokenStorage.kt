package studio.nxtech.fujubank.auth

/**
 * Access Token のみを保管する secure storage。
 *
 * AuthCore は refresh_token を **HttpOnly cookie** で配送する設計のため、
 * クライアントから refresh_token 文字列を直接扱うことはない（cookie は
 * `PersistentCookiesStorageFactory` が生成する [io.ktor.client.plugins.cookies.CookiesStorage]
 * 側に保管される）。
 *
 * ここでは access token と、トークンの絶対有効期限（epoch ミリ秒）だけを保持する。
 * `expiresAt` は将来 proactive refresh / 期限切れ判定で使う想定で、現状は保存のみ。
 */
interface TokenStorage {
    suspend fun loadAccess(): String?

    /**
     * トークンの絶対期限（epoch ミリ秒）。期限が分からない場合は null。
     */
    suspend fun loadExpiresAt(): Long?

    suspend fun saveAccess(token: String, expiresAt: Long?)

    suspend fun clear()
}
