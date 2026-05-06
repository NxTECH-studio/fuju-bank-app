package studio.nxtech.fujubank.di

import org.koin.core.qualifier.named

// プロセス全体で 1 つ共有される CoroutineScope (SupervisorJob + Dispatchers.Default)。
val APP_SCOPE_QUALIFIER = named("appScope")

// ActionCable の WebSocket エンドポイント URL。
val CABLE_URL_QUALIFIER = named("cableUrl")

// AuthCore `/v1/auth/*`（login / refresh / logout / mfaVerify）専用の HttpClient。
// Ktor `Auth { bearer }` プラグインを外して登録することで、refresh が 401 を返した
// ときの refreshTokens 再帰デッドロックを避ける。bank API / AuthCore `/v1/user/profile`
// 用の bearer 認証クライアントとは別物。
val AUTHCORE_CLIENT_QUALIFIER = named("authCoreClient")
