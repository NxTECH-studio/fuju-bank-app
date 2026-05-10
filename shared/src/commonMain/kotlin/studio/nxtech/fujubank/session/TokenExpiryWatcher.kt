package studio.nxtech.fujubank.session

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import studio.nxtech.fujubank.auth.TokenStorage
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.repository.AuthRepository
import studio.nxtech.fujubank.network.BearerCacheInvalidator

/**
 * access_token の `expiresAt` を on-demand でチェックし、閾値内なら proactive に
 * `AuthRepository.refresh()` を kick する monitor。
 *
 * 設計の前提:
 * - 監視方式は MVP で **on-demand のみ**（タイマー方式は採らない）。
 *   呼び出し元は Android `Lifecycle.Event.ON_RESUME` / iOS `ScenePhase = .active` の
 *   遷移で [checkNow] を呼ぶ。foreground 中の自動 refresh は設けず、開きっぱなしで
 *   切れた場合は既存の 401-driven refresh にフォールバックする。
 * - Authenticated 以外（Unauthenticated / MfaPending）では何もしない。
 * - refresh の競合制御は [AuthRepository.refresh] が内部 Mutex で直列化するため、
 *   ここでは「同じ Watcher への二重 [checkNow] 呼び出し」だけを [checkMutex] で抑える。
 *   tryLock で取れなければ既に動いている checkNow に任せて即 return。
 * - API エラーで refresh が失敗したら [invalidateSession] で TokenStorage と
 *   SessionStore を Unauthenticated に倒す。通信エラー（NetworkFailure）では何もしない
 *   ─ 圏外復帰時に毎回ログアウトされる UX を避けるため。
 *
 * テスト容易性のため [nowMillis] を inject 可能にしている。Production では
 * `Clock.System.now().toEpochMilliseconds()` を渡す（DI 側で組み立てる）。
 */
class TokenExpiryWatcher(
    private val authRepository: AuthRepository,
    private val tokenStorage: TokenStorage,
    private val sessionStore: SessionStore,
    // テスト互換のためデフォルト no-op。本番は sessionModule で実体を注入する。
    private val bearerCacheInvalidator: BearerCacheInvalidator = BearerCacheInvalidator { },
    // テスト容易性のため inject 可能。本番では DI 側で `Clock.System.now().toEpochMilliseconds()`
    // を渡す。`{ 0L }` を default にしているのは、TokenStorage に保存される expiresAt は
    // [AuthRepository.expiresAtFrom] が `nowMillis() <= 0L` で null を返す設計と整合させるため
    // （Watcher 単体テストで未注入のときは on-demand check が常に no-op になる）。
    private val nowMillis: () -> Long = { 0L },
    private val refreshThresholdMillis: Long = DEFAULT_REFRESH_THRESHOLD_MS,
) {
    private val checkMutex = Mutex()

    /**
     * 現在のセッションが Authenticated かつ access_token が `expiresAt - threshold` を
     * 過ぎていれば refresh を実行する。Authenticated 以外、`expiresAt == null`、または
     * 閾値内に達していない場合は no-op。
     *
     * 呼び出し元（UI 層）が ON_RESUME / .active で都度呼べば良い設計のため、ここでは
     * 戻り値を持たない。失敗時の SessionStore.clear() は Watcher 内で行う。
     */
    suspend fun checkNow() {
        // 二重起動を抑える。先行 checkNow が走っている間は同じ仕事を重ねない。
        if (!checkMutex.tryLock()) return
        try {
            if (sessionStore.current !is SessionState.Authenticated) return
            val expiresAt = tokenStorage.loadExpiresAt() ?: return
            val now = nowMillis()
            if (now < expiresAt - refreshThresholdMillis) return
            when (authRepository.refresh()) {
                is NetworkResult.Success -> Unit
                is NetworkResult.Failure -> invalidateSession(tokenStorage, sessionStore, bearerCacheInvalidator)
                // 通信エラー時は何もしない（次の checkNow / 401-driven refresh に委ねる）。
                is NetworkResult.NetworkFailure -> Unit
            }
        } finally {
            checkMutex.unlock()
        }
    }

    companion object {
        /**
         * 残りこの値以下になったら proactive に refresh を kick する。
         * AuthCore の `expires_in` は数分〜数十分想定で、安全マージンとして 60 秒に置く。
         */
        const val DEFAULT_REFRESH_THRESHOLD_MS: Long = 60_000L
    }
}
