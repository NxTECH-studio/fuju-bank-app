package studio.nxtech.fujubank.di

import org.koin.dsl.module
import studio.nxtech.fujubank.data.remote.api.AuthCoreUserApi
import studio.nxtech.fujubank.data.remote.api.UserApi
import studio.nxtech.fujubank.data.remote.api.UserMeApi
import studio.nxtech.fujubank.data.repository.ProfileRepository
import studio.nxtech.fujubank.data.repository.UserRepository

val userModule = module {
    single { UserApi(get()) }
    single { UserMeApi(get()) }
    single { UserRepository(get(), get()) }
    // AuthCore のユーザー情報 API は AuthApi と同様、bank ベース URL の HttpClient に
    // 別ホスト URL を渡して使う構成。Auth plugin が access_token を自動付与する。
    single { AuthCoreUserApi(get(), defaultAuthCoreBaseUrl()) }
    single { ProfileRepository(get(), get()) }
}
