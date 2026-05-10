package studio.nxtech.fujubank.data.repository

/**
 * `mfa/register` 成功時の戻り値を UI が扱いやすい形にしたドメイン型。
 *
 * - [qrPngBase64]: AuthCore の `qr_code` data URL から `data:image/png;base64,` 接頭を
 *   除去した、純粋な base64 PNG 文字列。decode → ImageBitmap 化は UI 層の責務。
 * - [secret]: TOTP の Base32 シード。QR が読めない場合の手動入力用に表示する。
 * - [recoveryCodes]: AuthCore が **平文で 1 度だけ返す** リカバリコード列。
 *   ユーザに保存させるまで失わないこと。
 */
data class MfaSetupBundle(
    val secret: String,
    val qrPngBase64: String,
    val recoveryCodes: List<String>,
)
