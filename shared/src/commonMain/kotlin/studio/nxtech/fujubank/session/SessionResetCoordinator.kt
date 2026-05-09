package studio.nxtech.fujubank.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import studio.nxtech.fujubank.account.AccountProfileProvider

/**
 * `SessionStore.state` を観測し、`Authenticated → Unauthenticated` 遷移時に
 * Koin singleton で保持されているプロセス内キャッシュ（プロフィール等）を破棄する調停役。
 *
 * 設計上の前提:
 * - VM 自体は `RootScaffold` / `RootTabView` の unmount に伴って自然破棄されるため、
 *   ここでは VM 内 state には触れず Provider 系 singleton のみ reset する。
 * - 起動経路は Android `App.kt` の `LaunchedEffect(Unit)` / iOS `iOSApp.init` などから
 *   1 度だけ [start] を呼ぶ前提だが、二重呼び出しに備えて [started] フラグで idempotent にする。
 *   呼び出し元はいずれも Main スレッド（`LaunchedEffect` / Swift の `init`）からのため、
 *   フラグの読み書きはデータ競合せず単純な `Boolean` で十分。
 * - StateFlow の collect は `SessionStore.scope` 上で起動する。アプリプロセスが生きている間
 *   有効な scope なので Coordinator 自身が scope を所有する必要は無い（[scope] 引数で
 *   テストから差し替えられるようにはしてある）。
 * - 「アプリ起動直後の `Unauthenticated` を logout と誤認しない」ために
 *   直前の状態を保持し、`Authenticated → Unauthenticated` の遷移エッジでのみ reset を発火する。
 */
class SessionResetCoordinator(
    private val sessionStore: SessionStore,
    private val accountProfileProvider: AccountProfileProvider,
    private val scope: CoroutineScope = sessionStore.scope,
) {
    private var started: Boolean = false

    /**
     * 観測を開始する。プロセス毎に 1 度だけ呼べば良いが、複数回呼ばれた場合は no-op。
     *
     * 戻り値は内部で起動した [Job]（テスト用途）。プロダクションコードで cancel する想定は無い。
     */
    fun start(): Job? {
        if (started) return null
        started = true
        return scope.launch {
            var previous: SessionState = sessionStore.current
            sessionStore.state.collect { next ->
                if (previous is SessionState.Authenticated && next is SessionState.Unauthenticated) {
                    accountProfileProvider.reset()
                }
                previous = next
            }
        }
    }
}
