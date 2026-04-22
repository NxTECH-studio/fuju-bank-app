package studio.nxtech.fujubank.di

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration

// shared モジュールが提供する Koin モジュールの集合。
// realtimeModule は ActionCable URL を必要とするため、cableUrl を引数に取る。
fun sharedModules(cableUrl: String): List<Module> = listOf(
    authModule,
    userModule,
    ledgerModule,
    realtimeModule(cableUrl),
    artifactModule,
)

// プラットフォーム側（Android / iOS）から呼び出す Koin 起動関数。
// appDeclaration で androidContext(...) など追加の設定を渡せる。
fun initKoin(
    cableUrl: String,
    appDeclaration: KoinAppDeclaration = {},
): KoinApplication = startKoin {
    appDeclaration()
    modules(sharedModules(cableUrl))
}
