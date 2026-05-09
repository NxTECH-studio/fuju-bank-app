package studio.nxtech.fujubank.session

import studio.nxtech.fujubank.auth.TokenStorage

/**
 * 「ローカルの認証 state を確実に Unauthenticated に倒す」共通操作。
 *
 * `tokenStorage.clear()` を [SessionStore.clear] より先に呼ぶことで、UI が
 * Unauthenticated を観測した瞬間に access token がストレージから消えていることを保証する。
 * 順序が逆だと `Unauthenticated` を観測した UI 層が短い窓の間「ログアウトしているのに
 * 古い access が読める」状態になり、誤って再認証済み扱いの API call が走り得る。
 *
 * 呼び出し元:
 * - 401-driven refresh が API エラー（refresh_family revoke 等）で失敗した場合
 *   ([studio.nxtech.fujubank.di.createAuthTokenRefresher])。
 * - proactive な期限切れ refresh が API エラーで失敗した場合（[TokenExpiryWatcher]）。
 *
 * 通信エラー（オフライン / DNS / タイムアウト）では呼び出さない。一時的な失敗で
 * セッションを破棄すると圏外で毎回ログアウトされる UX を生むため、呼び出し側で
 * `NetworkResult.Failure` 限定のガードを入れる責務を持たせる。
 */
internal suspend fun invalidateSession(
    tokenStorage: TokenStorage,
    sessionStore: SessionStore,
) {
    tokenStorage.clear()
    sessionStore.clear()
}
