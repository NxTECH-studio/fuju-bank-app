package studio.nxtech.fujubank.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `POST /users/me` のリクエストボディ。
 *
 * AuthCore の access_token に含まれる sub (=external_user_id) を bank が読み取って
 * 自分の user 行を lazy provision するエンドポイント。クライアントは name / public_key を
 * 任意で同梱できる（A2b 段階では両方 null 送信で OK）。
 *
 * `explicitNulls = false` の Json 設定により null フィールドは送信されない。
 */
@Serializable
data class UpsertMeRequest(
    val name: String? = null,
    @SerialName("public_key")
    val publicKey: String? = null,
)
