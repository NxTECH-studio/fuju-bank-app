package studio.nxtech.fujubank.data.remote.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.remote.dto.UpsertMeRequest
import studio.nxtech.fujubank.data.remote.dto.UserResponse
import studio.nxtech.fujubank.data.remote.runCatchingNetwork

/**
 * bank 側の自分自身（`/users/me`）に対する API クライアント。
 *
 * - [upsertMe]: `POST /users/me` で lazy provision する。Bearer access_token の sub が
 *   external_user_id として扱われ、初回のみ user 行が作成される。レスポンスは
 *   `UserResponse`（id / sub / balance_fuju / created_at）。
 * - [getMe]: `GET /users/me` で自分の最新状態を取得する。
 *
 * `userId` をパスに含める旧 [UserApi.get] とは責務が分離している。`/users/{id}` は
 * 公開鍵参照などの将来用途に残すが、自己情報の取得は必ず `/users/me` を使う。
 */
class UserMeApi(private val client: HttpClient) {

    suspend fun upsertMe(
        name: String? = null,
        publicKey: String? = null,
    ): NetworkResult<UserResponse> = runCatchingNetwork {
        client.post("/users/me") {
            contentType(ContentType.Application.Json)
            setBody(UpsertMeRequest(name = name, publicKey = publicKey))
        }.body()
    }

    suspend fun getMe(): NetworkResult<UserResponse> = runCatchingNetwork {
        client.get("/users/me").body()
    }
}
