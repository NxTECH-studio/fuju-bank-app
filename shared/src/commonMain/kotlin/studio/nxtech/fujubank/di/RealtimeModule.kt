package studio.nxtech.fujubank.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module
import studio.nxtech.fujubank.data.remote.api.UserChannelClient
import studio.nxtech.fujubank.data.repository.RealtimeRepository

// appScope はプロセス全体で 1 つ共有する SupervisorJob + Default ディスパッチャ。
// キャンセルはプラットフォーム側のライフサイクル (T5-2 / T5-3) に委ねる。
fun realtimeModule(cableUrl: String) = module {
    single(CABLE_URL_QUALIFIER) { cableUrl }
    single(APP_SCOPE_QUALIFIER) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
    single {
        UserChannelClient(
            client = get(),
            cableUrl = get(CABLE_URL_QUALIFIER),
            scope = get<CoroutineScope>(APP_SCOPE_QUALIFIER),
        )
    }
    single { RealtimeRepository(get(), get<CoroutineScope>(APP_SCOPE_QUALIFIER)) }
}
