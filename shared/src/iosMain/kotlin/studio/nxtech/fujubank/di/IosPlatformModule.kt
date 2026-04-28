package studio.nxtech.fujubank.di

import org.koin.dsl.module
import studio.nxtech.fujubank.BuildKonfig
import studio.nxtech.fujubank.auth.TokenStorage
import studio.nxtech.fujubank.auth.TokenStorageFactory
import studio.nxtech.fujubank.network.HttpClientConfig
import studio.nxtech.fujubank.network.createHttpClient

// shared の Koin グラフが external として扱う HttpClient / TokenStorageFactory を
// iOS 側で供給する。baseUrl は BuildKonfig 経由で Debug/Release を切り替える。
val iosPlatformModule = module {
    single { TokenStorageFactory() }
    single {
        createHttpClient(
            HttpClientConfig(
                baseUrl = BuildKonfig.BANK_API_BASE_URL,
                enableLogging = true,
                authTokenProvider = { get<TokenStorage>().getAccessToken() },
                refreshTokenProvider = { get<TokenStorage>().getRefreshToken() },
            ),
        )
    }
}
