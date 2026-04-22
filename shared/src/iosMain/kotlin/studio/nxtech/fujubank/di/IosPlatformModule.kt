package studio.nxtech.fujubank.di

import org.koin.dsl.module
import studio.nxtech.fujubank.auth.TokenStorage
import studio.nxtech.fujubank.auth.TokenStorageFactory
import studio.nxtech.fujubank.network.HttpClientConfig
import studio.nxtech.fujubank.network.createHttpClient

// shared の Koin グラフが external として扱う HttpClient / TokenStorageFactory を
// iOS 側で供給する。baseUrl は iOS シミュレータから Mac 上の localhost を叩く暫定値。
// TODO: remove after smoke test — baseUrl の設定経路は別タスクで設計する。
private const val BANK_API_BASE_URL = "http://localhost:3000"

val iosPlatformModule = module {
    single { TokenStorageFactory() }
    single {
        createHttpClient(
            HttpClientConfig(
                baseUrl = BANK_API_BASE_URL,
                enableLogging = true,
                authTokenProvider = { get<TokenStorage>().getAccessToken() },
                refreshTokenProvider = { get<TokenStorage>().getRefreshToken() },
            ),
        )
    }
}
