package studio.nxtech.fujubank.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import io.ktor.client.HttpClientConfig as KtorClientConfig

/**
 * shared 側 HttpClient を組み立てるための設定。
 *
 * - [authTokenProvider]: 現在の access_token を返す（無ければ null）。
 * - [cookiesStorage]: AuthCore の HttpOnly refresh_token cookie を永続化するための storage。
 *   Android は EncryptedSharedPreferences、iOS は Keychain で実装する。
 * - [tokenRefresher]: 401 時の refresh フック。cookie 経由で `/v1/auth/refresh` を叩いて
 *   新しい access_token を返す。null なら自動 refresh しない。
 */
data class HttpClientConfig(
    val baseUrl: String,
    val enableLogging: Boolean,
    val authTokenProvider: suspend () -> String?,
    val cookiesStorage: CookiesStorage,
    val tokenRefresher: AuthTokenRefresher? = null,
)

expect fun createHttpClient(config: HttpClientConfig): HttpClient

internal fun KtorClientConfig<*>.applyCommon(config: HttpClientConfig) {
    expectSuccess = true
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
        sanitizeHeader { header ->
            header.equals(HttpHeaders.Authorization, ignoreCase = true) ||
                header.equals(HttpHeaders.Cookie, ignoreCase = true) ||
                header.equals(HttpHeaders.SetCookie, ignoreCase = true) ||
                header.equals(HttpHeaders.ProxyAuthorization, ignoreCase = true)
        }
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 30_000
    }
    install(WebSockets)
    install(HttpCookies) {
        storage = config.cookiesStorage
    }
    install(Auth) {
        bearer {
            loadTokens {
                config.authTokenProvider()?.let { access ->
                    // refresh_token は HttpCookies plugin が管理するため空文字を渡す。
                    // Ktor の BearerTokens API は refresh_token フィールド必須だが、
                    // refreshTokens ブロックでも cookie 経由で refresh するので未使用。
                    BearerTokens(access, "")
                }
            }
            config.tokenRefresher?.let { refresher ->
                refreshTokens {
                    val newAccess = refresher.refresh() ?: return@refreshTokens null
                    BearerTokens(newAccess, "")
                }
            }
        }
    }
    defaultRequest {
        url(config.baseUrl)
        headers.append(HttpHeaders.Accept, "application/json")
    }
}
