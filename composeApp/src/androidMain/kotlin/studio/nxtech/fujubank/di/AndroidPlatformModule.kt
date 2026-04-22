package studio.nxtech.fujubank.di

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import studio.nxtech.fujubank.auth.TokenStorage
import studio.nxtech.fujubank.auth.TokenStorageFactory
import studio.nxtech.fujubank.network.HttpClientConfig
import studio.nxtech.fujubank.network.createHttpClient

// shared の Koin グラフが external として扱う HttpClient / TokenStorageFactory を
// Android 側で供給する。baseUrl は Android エミュレータから localhost を叩く暫定値。
// TODO: remove after smoke test — baseUrl の設定経路は別タスクで設計する。
private const val BANK_API_BASE_URL = "http://10.0.2.2:3000"

val androidPlatformModule = module {
    single { TokenStorageFactory(androidContext()) }
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
