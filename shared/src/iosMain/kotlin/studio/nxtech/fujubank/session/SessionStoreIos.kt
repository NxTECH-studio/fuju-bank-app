package studio.nxtech.fujubank.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
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
    @Suppress("unused") private val job: Job,
) {
    /** 観測を停止する。scope を cancel すれば子の collect も自動でキャンセルされる。 */
    fun close() {
        scope.cancel()
    }
}

/**
 * SessionStore の `state` を Swift クロージャに転送する。
 * 初期値も即時 1 回 emit される（StateFlow の仕様）。
 */
fun observeSession(store: SessionStore, onChange: (SessionState) -> Unit): FlowToken =
    observeFlow(store.state, onChange)

/**
 * SessionStore の `bootstrapped` を Swift クロージャに転送する。
 *
 * Splash 画面で「bootstrap が完了したか」を監視する用途。`SessionState` 観測と同じく、
 * 初期値（false）も subscribe 直後に 1 回 emit される。`true` を受け取ったら呼び出し側で
 * close すれば良い。
 */
fun observeBootstrapped(store: SessionStore, onChange: (Boolean) -> Unit): FlowToken =
    observeFlow(store.bootstrapped) { value -> onChange(value) }

/**
 * 任意の StateFlow を Swift クロージャに繋ぐ汎用版。SessionStore 以外（signup 等）の
 * iOS ブリッジからも再利用するため `internal` で公開する。
 */
internal fun <T> observeFlow(flow: StateFlow<T>, onChange: (T) -> Unit): FlowToken {
    val scope = CoroutineScope(Dispatchers.Main)
    val job = scope.launch {
        flow.collect { value -> onChange(value) }
    }
    return FlowToken(scope, job)
}
