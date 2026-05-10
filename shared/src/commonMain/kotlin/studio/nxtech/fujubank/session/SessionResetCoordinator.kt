package studio.nxtech.fujubank.session

import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import studio.nxtech.fujubank.account.AccountProfileProvider

/**
 * `SessionStore.state` を観測して、認証状態の遷移エッジに応じて Provider 系 singleton の
 * キャッシュを更新する調停役。
 *
 * - `Authenticated → Unauthenticated`（ログアウト）: [AccountProfileProvider.reset] で
 *   in-memory キャッシュを破棄。次に同じ Provider を購読した側に前ユーザの値を見せない。
 * - `* → Authenticated`（ログイン / サインアップ完了 / bootstrap 復元）:
 *   [AccountProfileProvider.refresh] で AuthCore + bank プロフィールを再取得し、
 *   AccountHub 画面が開いた時に最新値を返せるようにする。失敗時は前回値を据え置く。
 *
 * 設計上の前提:
 * - VM 自体は `RootScaffold` / `RootTabView` の unmount に伴って自然破棄されるため、
 *   ここでは VM 内 state には触れず Provider 系 singleton のみ更新する。
 * - 起動経路は Android `App.kt` の `LaunchedEffect(Unit)` / iOS `iOSApp.init` などから
 *   1 度だけ [start] を呼ぶ前提だが、二重呼び出しに備えて [started] フラグで idempotent にする。
 *   呼び出し元はいずれも Main スレッドだが、将来 background 起点が増えても二重 collect が
 *   起きないよう [Volatile] を付与している（コストは無視できる）。
 * - StateFlow の collect は `SessionStore.scope` 上で起動する。アプリプロセスが生きている間
 *   有効な scope なので Coordinator 自身が scope を所有する必要は無い（[scope] 引数で
 *   テストから差し替えられるようにはしてある）。
 * - 「アプリ起動直後の `Unauthenticated` を logout と誤認しない」「初期 emit を refresh に
 *   流用しない」ため、直前の状態を保持し、状態が **変化したエッジ** でのみハンドラを発火する。
 *   ただし bootstrap 復元の `Unauthenticated → Authenticated` は変化エッジなので refresh が走る。
 */
class SessionResetCoordinator(
    private val sessionStore: SessionStore,
    private val accountProfileProvider: AccountProfileProvider,
    private val scope: CoroutineScope = sessionStore.scope,
) {
    @Volatile
    private var started: Boolean = false

    /**
     * 観測を開始する。プロセス毎に 1 度だけ呼べば良いが、複数回呼ばれた場合は no-op。
     *
     * 戻り値は内部で起動した [Job]（テスト用途）。プロダクションコードで cancel する想定は無い。
     *
     * **初回 emit の扱い**: `previous` を `null` で初期化することで、StateFlow が collect 直後に
     * 流す現在値が `Authenticated` であった場合（bootstrap が Coordinator.start より先に完了して
     * いた場合など）にも refresh が確実に走るようにする。`var previous = sessionStore.current` を
     * 使うと、初期値と初回 emit が同値になり transition が検出されないギャップが生じる。
     */
    fun start(): Job? {
        if (started) return null
        started = true
        return scope.launch {
            var previous: SessionState? = null
            sessionStore.state.collect { next ->
                val prev = previous
                when {
                    // 初回 emit: 既に Authenticated なら refresh を 1 度だけ走らせる。
                    prev == null && next is SessionState.Authenticated -> {
                        accountProfileProvider.refresh()
                    }
                    // ログアウト遷移: キャッシュ破棄。
                    prev is SessionState.Authenticated && next is SessionState.Unauthenticated -> {
                        accountProfileProvider.reset()
                    }
                    // ログイン / サインアップ完了 / 再認証など: refresh で再取得。
                    // refresh 内で失敗しても前回値を据え置く設計のため例外は伝播しない。
                    prev != null && prev !is SessionState.Authenticated &&
                        next is SessionState.Authenticated -> {
                        accountProfileProvider.refresh()
                    }
                }
                previous = next
            }
        }
    }
}
