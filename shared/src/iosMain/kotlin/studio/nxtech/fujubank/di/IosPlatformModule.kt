package studio.nxtech.fujubank.di

import io.ktor.client.plugins.cookies.CookiesStorage
import org.koin.dsl.module
import studio.nxtech.fujubank.BuildKonfig
import studio.nxtech.fujubank.auth.PersistentCookiesStorageFactory
import studio.nxtech.fujubank.auth.TokenStorage
import studio.nxtech.fujubank.auth.TokenStorageFactory
import studio.nxtech.fujubank.network.AuthTokenRefresher
import studio.nxtech.fujubank.network.HttpClientConfig
import studio.nxtech.fujubank.network.createHttpClient

// shared の Koin グラフが external として扱う HttpClient / TokenStorageFactory /
// PersistentCookiesStorageFactory を iOS 側で供給する。baseUrl は BuildKonfig 経由で
// Debug/Release を切り替える。
val iosPlatformModule = module {
    single { TokenStorageFactory() }
    single { PersistentCookiesStorageFactory() }
    // bank client と AuthCore client で同じ CookiesStorage を共有して、
    // login で発行された refresh_token cookie を refresh / logout で使えるようにする。
    // 別 instance だと内部 mutex / 在メモリ状態が独立するため race の温床になる。
    single<CookiesStorage> { get<PersistentCookiesStorageFactory>().create() }
    single {
        createHttpClient(
            HttpClientConfig(
                baseUrl = BuildKonfig.BANK_API_BASE_URL,
                enableLogging = true,
                authTokenProvider = { get<TokenStorage>().loadAccess() },
                cookiesStorage = get(),
                tokenRefresher = getOrNull<AuthTokenRefresher>(),
            ),
        )
    }
    // AuthCore `/v1/auth/*` 用の Auth プラグイン無しクライアント。
    single(qualifier = AUTHCORE_CLIENT_QUALIFIER) {
        createHttpClient(
            HttpClientConfig(
                baseUrl = BuildKonfig.AUTHCORE_BASE_URL,
                enableLogging = true,
                authTokenProvider = { null },
                cookiesStorage = get(),
                tokenRefresher = null,
                installAuth = false,
            ),
        )
    }
}
