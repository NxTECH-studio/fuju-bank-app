package studio.nxtech.fujubank

import android.app.Application
import org.koin.android.ext.koin.androidContext
import studio.nxtech.fujubank.di.androidPlatformModule
import studio.nxtech.fujubank.di.defaultCableUrl
import studio.nxtech.fujubank.di.initKoin

class FujuBankApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(cableUrl = defaultCableUrl()) {
            androidContext(this@FujuBankApp)
            modules(androidPlatformModule)
        }
    }
}
