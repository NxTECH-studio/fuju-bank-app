package studio.nxtech.fujubank.di

import org.koin.dsl.module
import studio.nxtech.fujubank.data.remote.api.UserApi
import studio.nxtech.fujubank.data.remote.api.UserMeApi
import studio.nxtech.fujubank.data.repository.UserRepository

val userModule = module {
    single { UserApi(get()) }
    single { UserMeApi(get()) }
    single { UserRepository(get(), get()) }
}
