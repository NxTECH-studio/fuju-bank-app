package studio.nxtech.fujubank.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn
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

    fun creditEvents(userId: String): SharedFlow<CreditEvent> = cache.getOrPut(userId) {
        client.subscribe(userId)
            .mapNotNull { it.toDomain() }
            .shareIn(appScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 0)
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
