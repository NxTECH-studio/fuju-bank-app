package studio.nxtech.fujubank.di

import org.koin.dsl.module
import studio.nxtech.fujubank.data.remote.api.AuthCoreUserApi
import studio.nxtech.fujubank.data.remote.api.UserApi
import studio.nxtech.fujubank.data.remote.api.UserMeApi
import studio.nxtech.fujubank.data.remote.api.UserSearchApi
import studio.nxtech.fujubank.data.repository.ProfileRepository
import studio.nxtech.fujubank.data.repository.UserRepository

val userModule = module {
    single { UserApi(get()) }
    single { UserMeApi(get()) }
    single { UserSearchApi(get()) }
    // SessionStore は SessionModule で single 登録済み。送金先検索時に自分自身を除外する用途。
    single { UserRepository(get(), get(), get(), get()) }
    // AuthCore のユーザー情報 API は AuthApi と同様、bank ベース URL の HttpClient に
    // 別ホスト URL を渡して使う構成。Auth plugin が access_token を自動付与する。
    single { AuthCoreUserApi(get(), defaultAuthCoreBaseUrl()) }
    single { ProfileRepository(get(), get()) }
}
