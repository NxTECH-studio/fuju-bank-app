package studio.nxtech.fujubank.di

import org.koin.mp.KoinPlatform
import studio.nxtech.fujubank.BuildKonfig
import studio.nxtech.fujubank.account.AccountProfileProvider
import studio.nxtech.fujubank.account.NotificationSettingsPreferences
import studio.nxtech.fujubank.data.remote.api.UserApi
import studio.nxtech.fujubank.data.repository.AuthRepository
import studio.nxtech.fujubank.data.repository.ProfileRepository
import studio.nxtech.fujubank.data.repository.UserRepository
import studio.nxtech.fujubank.session.SessionStore
import studio.nxtech.fujubank.signup.SignupCompletionSignal
import studio.nxtech.fujubank.signup.SignupWelcomePreferences

/**
 * Swift 側から呼び出す Koin 起動関数。Obj-C 経由で `KoinIosKt.doInitKoin()` として公開される。
 *
 * プロセス内で 1 度だけ呼び出すこと。
 */
fun doInitKoin() {
    initKoin(cableUrl = BuildKonfig.CABLE_URL) {
        modules(iosPlatformModule)
    }
}

// Swift 側から Koin グラフ上のオブジェクトを取得するためのファサード群。
// Koin を Swift から直接触ると型付けが面倒なため、ここで取り出しを肩代わりする。

fun userApi(): UserApi = KoinPlatform.getKoin().get()

fun authRepository(): AuthRepository = KoinPlatform.getKoin().get()

fun userRepository(): UserRepository = KoinPlatform.getKoin().get()

fun sessionStore(): SessionStore = KoinPlatform.getKoin().get()

fun signupCompletionSignal(): SignupCompletionSignal = KoinPlatform.getKoin().get()

fun signupWelcomePreferences(): SignupWelcomePreferences = KoinPlatform.getKoin().get()

fun profileRepository(): ProfileRepository = KoinPlatform.getKoin().get()

fun accountProfileProvider(): AccountProfileProvider = KoinPlatform.getKoin().get()

fun notificationSettingsPreferences(): NotificationSettingsPreferences = KoinPlatform.getKoin().get()
