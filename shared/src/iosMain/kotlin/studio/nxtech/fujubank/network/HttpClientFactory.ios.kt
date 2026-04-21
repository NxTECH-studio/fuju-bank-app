package studio.nxtech.fujubank.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

actual fun createHttpClient(config: HttpClientConfig): HttpClient =
    HttpClient(Darwin) {
        applyCommon(config)
    }
