package studio.nxtech.fujubank.di

import kotlin.time.Clock
import org.koin.dsl.module
import studio.nxtech.fujubank.data.repository.RealtimeRepository
import studio.nxtech.fujubank.session.SessionResetCoordinator
import studio.nxtech.fujubank.session.SessionStore
import studio.nxtech.fujubank.session.TokenExpiryWatcher

val sessionModule = module {
    single { SessionStore() }
    // AccountProfileProvider は accountModule、RealtimeRepository は realtimeModule から
    // 解決される。sharedModules() の登録順では sessionModule が先だが、Koin は登録順では
    // なく解決時点で graph を見るため問題無し。
    //
    // onLogoutBoundary は `RealtimeRepository.clearCache` を遅延解決して呼ぶ。`getKoin()`
    // を invoke 時に取り直すパターンで、factory 構築時に他モジュールの解決を引き起こさない。
    single {
        val koin = getKoin()
        SessionResetCoordinator(
            sessionStore = get(),
            accountProfileProvider = get(),
            onLogoutBoundary = {
                koin.get<RealtimeRepository>().clearCache()
            },
        )
    }
    single {
        TokenExpiryWatcher(
            authRepository = get(),
            tokenStorage = get(),
            sessionStore = get(),
            bearerCacheInvalidator = get(),
            nowMillis = { Clock.System.now().toEpochMilliseconds() },
        )
    }
}
