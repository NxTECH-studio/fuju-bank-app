package studio.nxtech.fujubank.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.clearAuthTokens

/**
 * Ktor `Auth { bearer }` プラグインがメモリ内に保持している BearerTokens キャッシュを破棄する操作。
 *
 * ## なぜ必要か
 *
 * Ktor の `BearerAuthProvider` は `loadTokens` を最初の 1 度だけ呼んで結果を内部キャッシュし、
 * 以降のリクエストでは TokenStorage を再読しない。401 を受けた時の `refreshTokens` でしか
 * 再評価されない。
 *
 * このため、
 *
 * - ログイン直後（[studio.nxtech.fujubank.data.repository.AuthRepository.login] /
 *   [studio.nxtech.fujubank.data.repository.AuthRepository.verifyMfa] で
 *   `tokenStorage.saveAccess(...)` した直後）
 * - ログアウト直後（`tokenStorage.clear()` した直後）
 *
 * のいずれでもプラグインは **古い** Bearer をリクエストに付け続ける。別ユーザでログインした
 * 後に `/v1/user/profile` が前ユーザのデータを返す事象の根本原因。
 *
 * ## 設計
 *
 * - bank/AuthCore 共通の Auth プラグイン付きクライアント（default qualifier）に対して、
 *   その HttpClient の Auth プラグインを取り出して `BearerAuthProvider.clearToken()` を呼ぶ
 *   関数型インタフェース。Ktor 3.4 の組み込み拡張 [clearAuthTokens] を使う。
 * - `tokenStorage` の書き換えと**同じトランザクション**で必ず呼ぶ契約にする。
 *   `AuthRepository` がこれを ctor 依存として受け取り、saveAccess / clear と組で発火する。
 * - AUTHCORE_CLIENT_QUALIFIER のクライアントには Auth プラグインを入れていないため対象外。
 */
fun interface BearerCacheInvalidator {
    fun invalidate()
}

/**
 * 指定 [HttpClient] の Auth プラグインに登録された全 BearerAuthProvider のキャッシュを破棄する。
 *
 * Auth プラグイン自体が installed されていない（AUTHCORE_CLIENT_QUALIFIER 等）クライアントに
 * 対しては `authProviders` が空リストになるため no-op で安全に呼べる。
 */
fun HttpClient.clearBearerCache() {
    clearAuthTokens()
}
