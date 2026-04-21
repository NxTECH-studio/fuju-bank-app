package studio.nxtech.fujubank.data.remote.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.remote.dto.LoginRequest
import studio.nxtech.fujubank.data.remote.dto.RefreshRequest
import studio.nxtech.fujubank.data.remote.dto.TokenResponse
import studio.nxtech.fujubank.data.remote.runCatchingNetwork

class AuthApi(
    private val client: HttpClient,
    private val authCoreBaseUrl: String,
) {
    suspend fun login(email: String, password: String): NetworkResult<TokenResponse> =
        runCatchingNetwork {
            client.post("$authCoreBaseUrl/sessions") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(email = email, password = password))
            }.body()
        }

    suspend fun refresh(refreshToken: String): NetworkResult<TokenResponse> =
        runCatchingNetwork {
            client.post("$authCoreBaseUrl/sessions/refresh") {
                contentType(ContentType.Application.Json)
                setBody(RefreshRequest(refreshToken = refreshToken))
            }.body()
        }
}
