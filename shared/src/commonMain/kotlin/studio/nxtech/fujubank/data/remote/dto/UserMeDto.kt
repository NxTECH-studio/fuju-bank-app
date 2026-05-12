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
    // AuthCore /v1/user/profile の `public_id` を lazy provision 時に bank へ伝搬する。
    // bank-backend 側はカラム自体は NULL 許容のままだが、NOT NULL 化に追従する想定で
    // 呼び出し側は基本的に値を渡す。AuthCore 側で取得失敗した場合のみ null 送信で
    // fail-safe（既存ユーザーの provision を止めないため）。
    @SerialName("public_id")
    val publicId: String? = null,
)
