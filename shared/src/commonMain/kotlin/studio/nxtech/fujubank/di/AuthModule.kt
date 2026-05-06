package studio.nxtech.fujubank.di

import org.koin.dsl.module
import studio.nxtech.fujubank.auth.TokenStorage
import studio.nxtech.fujubank.auth.TokenStorageFactory
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.remote.api.AuthApi
import studio.nxtech.fujubank.data.repository.AuthRepository
import studio.nxtech.fujubank.network.AuthTokenRefresher

val authModule = module {
    single<TokenStorage> { get<TokenStorageFactory>().create() }
    // AuthApi は Auth プラグイン無しの専用 HttpClient を使う。同じクライアントだと
    // refresh が 401 を返したときに refreshTokens ブロックが再帰起動して deadlock する。
    single { AuthApi(get(qualifier = AUTHCORE_CLIENT_QUALIFIER), defaultAuthCoreBaseUrl()) }
    single { AuthRepository(get(), get()) }
    // Ktor Auth plugin の refreshTokens フック実体。AuthApi.refresh() は cookie 経由で
    // refresh するため引数不要。新 access_token を返すか、refresh 不能なら null。
    single<AuthTokenRefresher> {
        AuthTokenRefresher {
            when (val result = get<AuthRepository>().refresh()) {
                is NetworkResult.Success -> get<TokenStorage>().loadAccess()
                is NetworkResult.Failure, is NetworkResult.NetworkFailure -> null
            }
        }
    }
}
