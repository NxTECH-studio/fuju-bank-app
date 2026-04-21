package studio.nxtech.fujubank.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import io.ktor.client.HttpClientConfig as KtorClientConfig

data class HttpClientConfig(
    val baseUrl: String,
    val enableLogging: Boolean,
    val authTokenProvider: suspend () -> String?,
)

expect fun createHttpClient(config: HttpClientConfig): HttpClient

internal fun KtorClientConfig<*>.applyCommon(config: HttpClientConfig) {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            },
        )
    }
    install(Logging) {
        level = if (config.enableLogging) LogLevel.BODY else LogLevel.HEADERS
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 30_000
    }
    install(Auth) {
        bearer {
            loadTokens {
                config.authTokenProvider()?.let { token -> BearerTokens(token, "") }
            }
        }
    }
    defaultRequest {
        url(config.baseUrl)
        headers.append(HttpHeaders.Accept, "application/json")
    }
}
