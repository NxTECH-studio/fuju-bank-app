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
    sessionModule,
    signupModule,
    accountModule,
    ledgerModule,
    realtimeModule(cableUrl),
    artifactModule,
)

/**
 * プラットフォーム側（Android / iOS）から呼び出す Koin 起動関数。
 *
 * プロセス内で 1 度だけ呼び出すこと。2 度目以降は Koin が
 * `KoinApplicationAlreadyStartedException` を投げるため、Application / MainActivity
 * のライフサイクルに合わせて単一の起点から呼ぶ。テストでは `Module.verify` を使い
 * 本関数は使わない。
 *
 * @param cableUrl ActionCable の WebSocket エンドポイント。`ws://` / `wss://` のみ許可。
 * @param appDeclaration `androidContext(...)` など platform 固有の追加設定。
 */
fun initKoin(
    cableUrl: String,
    appDeclaration: KoinAppDeclaration = {},
): KoinApplication {
    require(cableUrl.startsWith("ws://") || cableUrl.startsWith("wss://")) {
        "cableUrl must use ws:// or wss:// scheme, but was: $cableUrl"
    }
    return startKoin {
        appDeclaration()
        modules(sharedModules(cableUrl))
    }
}
