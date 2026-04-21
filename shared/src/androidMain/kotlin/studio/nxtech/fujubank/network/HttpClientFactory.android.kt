package studio.nxtech.fujubank.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

actual fun createHttpClient(config: HttpClientConfig): HttpClient =
    HttpClient(OkHttp) {
        applyCommon(config)
    }
