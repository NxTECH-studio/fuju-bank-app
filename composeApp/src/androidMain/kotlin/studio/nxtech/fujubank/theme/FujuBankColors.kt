package studio.nxtech.fujubank.theme

import androidx.compose.ui.graphics.Color

/**
 * fuju 銀行アプリのカラートークン。Figma `bzm13wVWQmgaFFmlEbJZ3k` (銀行版 6 画面) から抽出。
 *
 * Material3 の ColorScheme と意味論が衝突するため、専用 object に切り出す。
 * Compose Material3 の `MaterialTheme.colorScheme` には依存させず、コンポーザブルから
 * `FujuBankColors.Background` のように直接参照する。
 */
object FujuBankColors {
    /** 画面の地の色。Figma 全画面で共通。 */
    val Background: Color = Color(0xFFF6F7F9)

    /** 残高カードや取引履歴カードなど白い面。 */
    val Surface: Color = Color(0xFFFFFFFF)

    /** 主要テキスト（金額・見出し・本文）。Figma `#111`。 */
    val TextPrimary: Color = Color(0xFF111111)

    /** 補助テキスト（タイムスタンプ・サブ説明）。Figma `#6e6f72`。 */
    val TextSecondary: Color = Color(0xFF6E6F72)

    /** より淡い補助テキスト（ID・フォームラベル・無効ナビ）。Figma `#b0b0b0`。 */
    val TextTertiary: Color = Color(0xFFB0B0B0)

    /** ブランドアクセント。金額表示・通知バッジ・トグル ON。Figma `#ff1e9e`。 */
    val BrandPink: Color = Color(0xFFFF1E9E)

    /** リンクテキスト（「もっとみる」等）。Figma `#187aea`。 */
    val LinkBlue: Color = Color(0xFF187AEA)

    /** 通知ベルの未読ドット。 */
    val NotificationDot: Color = Color(0xFFFF0000)

    /** ボトムナビ上部の細いボーダー。Figma `#efefef`。 */
    val BottomBarBorder: Color = Color(0xFFEFEFEF)

    /** カード内の区切り線（取引履歴行間 / 設定項目間）。 */
    val TransactionDivider: Color = Color(0xFFEFEFEF)

    /** アバターアイコンの細枠やフォーム内の区切り線。Figma `#e9ebed`。 */
    val Hairline: Color = Color(0xFFE9EBED)

    /** 取引相手アバターの暫定プレースホルダ（人物用）。 */
    val AvatarPerson: Color = Color(0xFFE5E5E5)

    /** 取引相手アバターの暫定プレースホルダ（アーティファクト用）。 */
    val AvatarArtifact: Color = Color(0xFFC9D8E1)
}
