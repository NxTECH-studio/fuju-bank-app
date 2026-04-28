package studio.nxtech.fujubank.di

import org.koin.mp.KoinPlatform
import studio.nxtech.fujubank.BuildKonfig
import studio.nxtech.fujubank.data.remote.api.UserApi

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

/**
 * Swift 側から Koin グラフ上の [UserApi] を取得するためのファサード。
 * Koin を Swift から直接触ると型付けが面倒なため、ここで取り出しを肩代わりする。
 */
fun userApi(): UserApi = KoinPlatform.getKoin().get()
