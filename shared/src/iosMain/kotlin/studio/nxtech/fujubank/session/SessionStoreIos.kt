package studio.nxtech.fujubank.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Swift 側から StateFlow を観測するための薄い Closeable ラッパー。
 *
 * Skie / kotlinx-coroutines の swift export を使わない構成なので、StateFlow を Swift から
 * 直接触ろうとすると `Kotlinx_coroutines_core...` といった内部記号に依存することになる。
 * その依存を避けるため iosMain 側で `(T) -> Unit` の Kotlin 関数を受けるシンプルな
 * subscribe API を提供する。
 *
 * 使い方:
 * ```swift
 * let token = SessionStoreObserverKt.observeSession(store) { state in
 *     // SwiftUI の @Published に転写するなど
 * }
 * // 画面破棄時に
 * token.close()
 * ```
 */
class FlowToken internal constructor(
    private val scope: CoroutineScope,
    private val job: Job,
) {
    fun close() {
        job.cancel()
        scope.cancel()
    }
}

/**
 * SessionStore の `state` を Swift クロージャに転送する。
 * 初期値も即時 1 回 emit される（StateFlow の仕様）。
 */
fun observeSession(store: SessionStore, onChange: (SessionState) -> Unit): FlowToken =
    observe(store.state, onChange)

/**
 * 任意の StateFlow を Swift クロージャに繋ぐ汎用版。今は SessionStore 観測専用だが、
 * A3 以降で他の Flow もブリッジする想定で内部に切り出しておく。
 */
private fun <T> observe(flow: StateFlow<T>, onChange: (T) -> Unit): FlowToken {
    val scope = CoroutineScope(Dispatchers.Main)
    val job = scope.launch {
        flow.collect { value -> onChange(value) }
    }
    return FlowToken(scope, job)
}
