package studio.nxtech.fujubank.data.remote.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.remote.dto.UserSearchResponse
import studio.nxtech.fujubank.data.remote.dto.UserSearchResultDto
import studio.nxtech.fujubank.data.remote.runCatchingNetwork

/**
 * 表示名で送金先候補を絞り込むための薄い API ラッパ。
 *
 * バックエンド `GET /users/search?q={query}` を叩く。サーバ側で 2 文字未満は弾かれる前提だが、
 * 余計なリクエストを発生させないため呼び出し側 (ViewModel) でも 2 文字未満をガードする。
 */
class UserSearchApi(private val client: HttpClient) {

    suspend fun searchByDisplayName(query: String): NetworkResult<List<UserSearchResultDto>> =
        runCatchingNetwork {
            client.get("/users/search") {
                parameter("q", query)
            }.body<UserSearchResponse>().users
        }
}
