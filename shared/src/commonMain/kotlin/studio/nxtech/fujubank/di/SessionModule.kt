package studio.nxtech.fujubank.di

import kotlin.time.Clock
import org.koin.dsl.module
import studio.nxtech.fujubank.session.SessionResetCoordinator
import studio.nxtech.fujubank.session.SessionStore
import studio.nxtech.fujubank.session.TokenExpiryWatcher

val sessionModule = module {
    single { SessionStore() }
    // AccountProfileProvider は accountModule 提供。sharedModules() の登録順では先に
    // sessionModule が並ぶが、Koin は登録順ではなく解決時点で graph を見るため問題無し。
    single { SessionResetCoordinator(get(), get()) }
    single {
        TokenExpiryWatcher(
            authRepository = get(),
            tokenStorage = get(),
            sessionStore = get(),
            nowMillis = { Clock.System.now().toEpochMilliseconds() },
        )
    }
}
