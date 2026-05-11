package studio.nxtech.fujubank.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import studio.nxtech.fujubank.data.remote.api.UserChannelClient
import studio.nxtech.fujubank.data.remote.dto.toDomain
import studio.nxtech.fujubank.domain.model.CreditEvent

// 複数画面（HUD / 通知 / ダッシュボード）で同じ credit イベントを購読するため、
// appScope に紐付けて shareIn する。WhileSubscribed は最後の collector が離れてから
// 5 秒で WebSocket を切断する（短い画面遷移では張り直さない）。
class RealtimeRepository(
    private val client: UserChannelClient,
    private val appScope: CoroutineScope,
) {
    private val cache = mutableMapOf<String, SharedFlow<CreditEvent>>()
    private val cacheMutex = Mutex()

    // 複数スレッドから同時に呼ばれても同一 userId の SharedFlow が重複しないよう
    // Mutex で保護する。結果として WebSocket も 1 本に抑えられる。
    suspend fun creditEvents(userId: String): SharedFlow<CreditEvent> = cacheMutex.withLock {
        cache.getOrPut(userId) {
            client.subscribe(userId)
                .mapNotNull { it.toDomain() }
                .shareIn(appScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 0)
        }
    }

    /**
     * userId 別 SharedFlow キャッシュエントリを破棄する。別アカウントへのログイン境界で呼び、
     * 前ユーザの SharedFlow 参照がプロセス内に長く居座らないようにする。
     *
     * 厳密には:
     * - **map から外すだけ** で、既存の collector が掴んでいる SharedFlow 自体は GC されず、
     *   `shareIn` の upstream coroutine も走り続ける。collector が全員離れて
     *   `WhileSubscribed(STOP_TIMEOUT_MILLIS)` を満たした時点で自然停止する。
     * - 次回 [creditEvents] 呼び出しは map から外れているため **新規購読** を作る。
     * - 同一 userId で再ログインした極端なケースでは、前回の SharedFlow が collector を
     *   保持している間は新旧の WebSocket が一時並走しうる点に注意（普段は別 userId なので無関係）。
     */
    suspend fun clearCache() = cacheMutex.withLock {
        cache.clear()
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
