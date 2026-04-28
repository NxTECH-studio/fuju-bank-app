package studio.nxtech.fujubank.data.remote.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.remote.dto.LoginRequest
import studio.nxtech.fujubank.data.remote.dto.MfaVerifyRequest
import studio.nxtech.fujubank.data.remote.dto.PreTokenResponse
import studio.nxtech.fujubank.data.remote.dto.TokenResponse
import studio.nxtech.fujubank.data.remote.runCatchingNetwork

/**
 * AuthCore (`fuju-system-authentication`) の /v1/auth 配下エンドポイント呼び出し。
 *
 * - login は MFA 必須ユーザのとき `pre_token` 付きの別形態で 200 を返すため、
 *   呼び出し側で sealed branch を判別できるよう [LoginRawResponse] 経由で返す。
 * - refresh / logout / mfaVerify は `HttpCookies` plugin によって自動で
 *   refresh_token cookie が乗る前提（[HttpClient] 側で設定する）。
 */
class AuthApi(
    private val client: HttpClient,
    private val authCoreBaseUrl: String,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    suspend fun login(
        identifier: String,
        password: String,
    ): NetworkResult<LoginRawResponse> = runCatchingNetwork {
        val element: JsonElement = client.post("$authCoreBaseUrl/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(identifier = identifier, password = password))
        }.body()
        parseLoginResponse(element)
    }

    suspend fun mfaVerify(
        preToken: String,
        code: String? = null,
        recoveryCode: String? = null,
    ): NetworkResult<TokenResponse> = runCatchingNetwork {
        client.post("$authCoreBaseUrl/v1/auth/mfa/verify") {
            contentType(ContentType.Application.Json)
            headers { append(HttpHeaders.Authorization, "Bearer $preToken") }
            setBody(MfaVerifyRequest(code = code, recoveryCode = recoveryCode))
        }.body()
    }

    suspend fun refresh(): NetworkResult<TokenResponse> = runCatchingNetwork {
        client.post("$authCoreBaseUrl/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
        }.body()
    }

    suspend fun logout(): NetworkResult<Unit> = runCatchingNetwork {
        client.post("$authCoreBaseUrl/v1/auth/logout") {
            contentType(ContentType.Application.Json)
        }
        Unit
    }

    private fun parseLoginResponse(element: JsonElement): LoginRawResponse {
        val obj: JsonObject = element.jsonObject
        val mfaRequired = obj["mfa_required"]
        return if (mfaRequired != null) {
            LoginRawResponse.Mfa(json.decodeFromJsonElement(PreTokenResponse.serializer(), element))
        } else {
            LoginRawResponse.Token(json.decodeFromJsonElement(TokenResponse.serializer(), element))
        }
    }
}

/**
 * `/v1/auth/login` の 200 レスポンスを判別するための内部表現。
 * `mfa_required` キーの有無で 2 パターンに振り分ける。
 */
sealed class LoginRawResponse {
    data class Token(val response: TokenResponse) : LoginRawResponse()
    data class Mfa(val response: PreTokenResponse) : LoginRawResponse()
}
