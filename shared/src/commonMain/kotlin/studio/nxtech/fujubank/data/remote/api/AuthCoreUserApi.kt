package studio.nxtech.fujubank.data.remote.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.remote.dto.AuthCoreUserResponse
import studio.nxtech.fujubank.data.remote.runCatchingNetwork

/**
 * AuthCore (`fuju-system-authentication`) の自分自身プロフィールを取得する API。
 *
 * `GET /v1/user/profile` を Bearer access_token 付きで呼び、`AuthCoreUserResponse` を返す。
 * Ktor の `Auth` plugin が現在の access_token を自動で付与するため、このクラスでは
 * Authorization ヘッダを明示的に組み立てない。
 */
class AuthCoreUserApi(
    private val client: HttpClient,
    private val authCoreBaseUrl: String,
) {

    suspend fun getProfile(): NetworkResult<AuthCoreUserResponse> = runCatchingNetwork {
        client.get("$authCoreBaseUrl/v1/user/profile").body()
    }
}
