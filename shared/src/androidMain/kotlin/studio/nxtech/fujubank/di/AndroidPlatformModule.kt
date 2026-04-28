package studio.nxtech.fujubank.di

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import studio.nxtech.fujubank.BuildKonfig
import studio.nxtech.fujubank.auth.PersistentCookiesStorageFactory
import studio.nxtech.fujubank.auth.TokenStorage
import studio.nxtech.fujubank.auth.TokenStorageFactory
import studio.nxtech.fujubank.network.AuthTokenRefresher
import studio.nxtech.fujubank.network.HttpClientConfig
import studio.nxtech.fujubank.network.createHttpClient

// shared の Koin グラフが external として扱う HttpClient / TokenStorageFactory /
// PersistentCookiesStorageFactory を Android 側で供給する。baseUrl は BuildKonfig 経由で
// Debug/Release を切り替える。
val androidPlatformModule = module {
    single { TokenStorageFactory(androidContext()) }
    single { PersistentCookiesStorageFactory(androidContext()) }
    single {
        createHttpClient(
            HttpClientConfig(
                baseUrl = BuildKonfig.BANK_API_BASE_URL,
                enableLogging = true,
                authTokenProvider = { get<TokenStorage>().loadAccess() },
                cookiesStorage = get<PersistentCookiesStorageFactory>().create(),
                tokenRefresher = getOrNull<AuthTokenRefresher>(),
            ),
        )
    }
}
