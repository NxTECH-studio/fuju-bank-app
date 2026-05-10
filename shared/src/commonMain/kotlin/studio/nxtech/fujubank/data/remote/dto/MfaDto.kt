package studio.nxtech.fujubank.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `POST /v1/auth/mfa/verify` リクエストボディ。
 *
 * `code`（TOTP）または `recovery_code`（リカバリコード）のいずれかを指定する。
 * 両方 null だとサーバが `VALIDATION_FAILED` を返す。
 *
 * `explicitNulls = false` の Json 設定により、null フィールドは送信されない。
 */
@Serializable
data class MfaVerifyRequest(
    val code: String? = null,
    @SerialName("recovery_code") val recoveryCode: String? = null,
)

/**
 * `POST /v1/auth/mfa/register` 成功レスポンス。
 *
 * - `secret`: TOTP の Base32 シード（QR が読めない場合の手動入力用）。
 * - `qrCodeDataUrl`: AuthCore 側で go-qrcode が生成した 256px PNG を data URL 化したもの。
 *   形式は `"data:image/png;base64,<base64>"` 固定。
 * - `recoveryCodes`: 平文のリカバリコード 8〜10 個。**1 回しか返らない**ため、
 *   ユーザに保存させた後は再表示不可。
 *
 * このレスポンスは非べき等。再呼び出しすると新しい secret/QR/recoveryCodes が返り、
 * 旧 secret は失効する。
 */
@Serializable
data class MfaRegisterResponse(
    val secret: String,
    @SerialName("qr_code") val qrCodeDataUrl: String,
    @SerialName("recovery_codes") val recoveryCodes: List<String>,
)

/**
 * `POST /v1/auth/mfa/enable` リクエストボディ。
 *
 * `mfa/register` で取得した secret に対して、ユーザが認証アプリで生成した 6 桁 TOTP を
 * 検証する。成功すると AuthCore 側で `mfa_enabled = true` がコミットされる。
 */
@Serializable
data class MfaEnableRequest(
    val code: String,
)
