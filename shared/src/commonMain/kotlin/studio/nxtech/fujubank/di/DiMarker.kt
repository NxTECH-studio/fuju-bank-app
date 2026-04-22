package studio.nxtech.fujubank.di

import org.koin.core.qualifier.named

internal object DiMarker

// プロセス全体で 1 つ共有される CoroutineScope (SupervisorJob + Dispatchers.Default)。
val APP_SCOPE_QUALIFIER = named("appScope")

// ActionCable の WebSocket エンドポイント URL。
val CABLE_URL_QUALIFIER = named("cableUrl")
