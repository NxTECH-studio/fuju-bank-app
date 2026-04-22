package studio.nxtech.fujubank

import android.app.Application
import org.koin.android.ext.koin.androidContext
import studio.nxtech.fujubank.di.androidPlatformModule
import studio.nxtech.fujubank.di.initKoin

class FujuBankApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(cableUrl = CABLE_URL) {
            androidContext(this@FujuBankApp)
            modules(androidPlatformModule)
        }
    }

    private companion object {
        // TODO: remove after smoke test — ActionCable URL は設定経路の設計後に差し替える。
        const val CABLE_URL = "ws://10.0.2.2:3000/cable"
    }
}
