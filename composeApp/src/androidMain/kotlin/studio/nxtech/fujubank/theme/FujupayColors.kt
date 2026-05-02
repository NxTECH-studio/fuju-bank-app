package studio.nxtech.fujubank.theme

import androidx.compose.ui.graphics.Color

/**
 * fujupay ホーム画面 (Figma `89:12356`) のカラートークン。
 *
 * Material3 の ColorScheme と意味論が衝突するため、専用 object に切り出す。
 * Compose Material3 の `MaterialTheme.colorScheme` には依存させず、コンポーザブルから
 * `FujupayColors.background` のように直接参照する。
 */
object FujupayColors {
    /** 画面の地の色。fujupay ブランドのオフホワイト。 */
    val Background: Color = Color(0xFFF6F7F9)

    /** 残高カードやタブバーなどの白い面。 */
    val Surface: Color = Color(0xFFFFFFFF)

    /** 主要テキスト（金額・見出し等）。 */
    val TextPrimary: Color = Color(0xFF111111)

    /** 補助テキスト（ラベル等）。 */
    val TextSecondary: Color = Color(0xFF4B4C50)

    /** より淡い補助テキスト（QR 周りのキャプション等）。 */
    val TextTertiary: Color = Color(0xFFB0B0B0)

    /** ブランドピンク。支払い FAB / スキャン / 通知バッジ。 */
    val BrandPink: Color = Color(0xFFFF1E9E)

    /** 取引履歴アクションのカテゴリ色。 */
    val ActionPurple: Color = Color(0xFF9E1EFF)

    /** 送る・もらうアクションのカテゴリ色。 */
    val ActionGreen: Color = Color(0xFF0CD80C)

    /** チャージアクションのカテゴリ色。 */
    val ActionBlue: Color = Color(0xFF1E83FF)
}
