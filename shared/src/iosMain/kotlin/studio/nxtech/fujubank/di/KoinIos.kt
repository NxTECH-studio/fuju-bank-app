package studio.nxtech.fujubank.di

import org.koin.mp.KoinPlatform
import studio.nxtech.fujubank.BuildKonfig
import studio.nxtech.fujubank.data.remote.api.UserApi
import studio.nxtech.fujubank.data.repository.AuthRepository
import studio.nxtech.fujubank.data.repository.UserRepository
import studio.nxtech.fujubank.session.SessionStore

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
