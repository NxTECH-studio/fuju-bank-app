package studio.nxtech.fujubank.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import studio.nxtech.fujubank.R

/**
 * fuju 銀行アプリの軽量タイポグラフィトークン。Figma `bzm13wVWQmgaFFmlEbJZ3k` (銀行版 6 画面) から抽出。
 *
 * Material3 の `Typography` ではなく軽量 data class として提供する。日本語・英語ともに Noto Sans JP に
 * 統一しているのは、Android のデフォルトフォールバックでは日本語表示が不自然になりやすいため
 * （Roboto + システム CJK の合成で字形・字間が崩れる）。バンドル済みの TTF を `res/font/` から直接参照する。
 */
val NotoSansJP: FontFamily = FontFamily(
    Font(R.font.noto_sans_jp_regular, FontWeight.Normal),
    Font(R.font.noto_sans_jp_medium, FontWeight.Medium),
    Font(R.font.noto_sans_jp_semibold, FontWeight.SemiBold),
    Font(R.font.noto_sans_jp_bold, FontWeight.Bold),
)

/**
 * `MaterialTheme(typography = ...)` に渡す Material3 Typography。`MaterialTheme.typography.xxx` を
 * 直接参照している画面（`LoginScreen` / `MfaVerifyScreen` 等）も Noto Sans JP に揃える目的で、
 * Material3 デフォルトの全 13 スタイルに `fontFamily = NotoSansJP` を上書きしただけのもの。
 * フォントサイズ / line height は Material3 デフォルトのまま。
 */
val FujuBankMaterialTypography: Typography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = NotoSansJP),
        displayMedium = displayMedium.copy(fontFamily = NotoSansJP),
        displaySmall = displaySmall.copy(fontFamily = NotoSansJP),
        headlineLarge = headlineLarge.copy(fontFamily = NotoSansJP),
        headlineMedium = headlineMedium.copy(fontFamily = NotoSansJP),
        headlineSmall = headlineSmall.copy(fontFamily = NotoSansJP),
        titleLarge = titleLarge.copy(fontFamily = NotoSansJP),
        titleMedium = titleMedium.copy(fontFamily = NotoSansJP),
        titleSmall = titleSmall.copy(fontFamily = NotoSansJP),
        bodyLarge = bodyLarge.copy(fontFamily = NotoSansJP),
        bodyMedium = bodyMedium.copy(fontFamily = NotoSansJP),
        bodySmall = bodySmall.copy(fontFamily = NotoSansJP),
        labelLarge = labelLarge.copy(fontFamily = NotoSansJP),
        labelMedium = labelMedium.copy(fontFamily = NotoSansJP),
        labelSmall = labelSmall.copy(fontFamily = NotoSansJP),
    )
}
@Immutable
data class FujuBankTextStyles(
    /** 画面タイトル（「取引履歴」「取引詳細」等）。Figma `Inter Bold 17`。 */
    val headline: TextStyle,
    /** リストカード内のタイトル（取引相手名・項目名）。Figma `Inter SemiBold 14`。 */
    val title: TextStyle,
    /** 本文（メールアドレス・表示名・本文テキスト）。Figma `Inter Medium 14`。 */
    val body: TextStyle,
    /** キャプション（タイムスタンプ・サブ説明）。Figma `Inter Regular 12`。 */
    val caption: TextStyle,
    /** セクションラベル（「アカウント情報」「設定」等）。Figma `SF Pro Bold 12`。 */
    val sectionLabel: TextStyle,
    /** 残高金額の主要数字。Figma `SF Pro Bold 48`。 */
    val amount: TextStyle,
    /** 残高金額の単位（「ふじゅ〜」）。Figma `SF Pro Bold 20`。 */
    val amountUnit: TextStyle,
)

/** デフォルトのタイポグラフィトークン。Figma 銀行版 6 画面の値をそのまま反映。 */
val FujuBankTypography: FujuBankTextStyles = FujuBankTextStyles(
    headline = TextStyle(fontFamily = NotoSansJP, fontSize = 17.sp, fontWeight = FontWeight.Bold),
    title = TextStyle(fontFamily = NotoSansJP, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    body = TextStyle(fontFamily = NotoSansJP, fontSize = 14.sp, fontWeight = FontWeight.Medium),
    caption = TextStyle(fontFamily = NotoSansJP, fontSize = 12.sp, fontWeight = FontWeight.Normal),
    sectionLabel = TextStyle(fontFamily = NotoSansJP, fontSize = 12.sp, fontWeight = FontWeight.Bold),
    amount = TextStyle(fontFamily = NotoSansJP, fontSize = 48.sp, fontWeight = FontWeight.Bold),
    amountUnit = TextStyle(fontFamily = NotoSansJP, fontSize = 20.sp, fontWeight = FontWeight.Bold),
)
