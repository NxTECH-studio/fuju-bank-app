package studio.nxtech.fujubank.network

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging

actual fun createHttpClient(config: HttpClientConfig): HttpClient =
    HttpClient(OkHttp) {
        applyCommon(config)
        if (config.enableLogging) {
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        Log.d("Ktor", message)
                    }
                }
            }
        }
    }
