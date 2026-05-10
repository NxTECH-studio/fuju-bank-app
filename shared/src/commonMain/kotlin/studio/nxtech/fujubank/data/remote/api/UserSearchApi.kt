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
 * 公開ID (public_id) の前方一致で送金先候補を絞り込むための薄い API ラッパ。
 *
 * バックエンド `GET /users/search?q={query}` を叩く。検索は LOWER(public_id) LIKE '{q}%' で
 * 大文字小文字を無視する前方一致。サーバ側で 2 文字未満は弾かれる前提だが、余計なリクエストを
 * 発生させないため呼び出し側 (ViewModel) でも 2 文字未満をガードする。
 *
 * クエリ文字列はクライアント側で **正規化せず** そのまま送る。サーバ側で `LOWER()` を適用して
 * 比較するため大文字小文字は問わず、ユーザーが入力した形を保ったままサーバに渡す方がエコー時の
 * 挙動が直感的になる（先頭が `Y` でも `y` でも同じ候補が返る）。
 */
class UserSearchApi(private val client: HttpClient) {

    suspend fun searchByPublicId(query: String): NetworkResult<List<UserSearchResultDto>> =
        runCatchingNetwork {
            client.get("/users/search") {
                parameter("q", query)
            }.body<UserSearchResponse>().users
        }
}
