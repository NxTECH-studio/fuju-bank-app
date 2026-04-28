package studio.nxtech.fujubank.di

import org.koin.dsl.module
import studio.nxtech.fujubank.auth.TokenStorage
import studio.nxtech.fujubank.auth.TokenStorageFactory
import studio.nxtech.fujubank.data.remote.api.AuthApi
import studio.nxtech.fujubank.data.repository.AuthRepository

val authModule = module {
    single<TokenStorage> { get<TokenStorageFactory>().create() }
    single { AuthApi(get(), defaultAuthCoreBaseUrl()) }
    single { AuthRepository(get(), get()) }
}
