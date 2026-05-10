package studio.nxtech.fujubank.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET /users/search?q=...` の 1 件分のレスポンス DTO。
 *
 * - [id]: bank 内部の user 主キー（Long）。`POST /ledger/transfer` の `to_user_id` に渡す。
 * - [publicId]: ハンドル文字列。検索キーかつ UI の主表示。
 * - [iconUrl]: 円形アバターに表示する画像 URL。null 可（現状サーバは常に null を返す）。
 *
 * name / email / balance / mfa_enabled 等の機密または ID enumeration 抑止対象は
 * API 側で意図的に返さない契約。検索キーは public_id 前方一致 (大小無視)。
 */
@Serializable
data class UserSearchResultDto(
    // bank 側のユーザー主キーは整数。クライアントの domain 表現では文字列に変換する。
    val id: Long,
    @SerialName("public_id")
    val publicId: String,
    @SerialName("icon_url")
    val iconUrl: String? = null,
)

/**
 * `GET /users/search` のレスポンスエンベロープ。
 *
 * バックエンドが配列を直接返すか `{ "users": [...] }` で包むかを実装着手時点で確定しきれていない
 * ため、`{ "users": [...] }` 形式を採用する。配列直接形式に変わった場合は API レイヤで
 * `Array<UserSearchResultDto>` への置換に差し替える（`UserSearchApi` 側で吸収）。
 */
@Serializable
data class UserSearchResponse(
    val users: List<UserSearchResultDto>,
)
