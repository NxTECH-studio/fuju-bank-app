package studio.nxtech.fujubank.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * fuju 銀行アプリの軽量タイポグラフィトークン。Figma `bzm13wVWQmgaFFmlEbJZ3k` (銀行版 6 画面) から抽出。
 *
 * Material3 の `Typography` ではなく軽量 data class として提供する。Compose Multiplatform 移行時にも
 * そのまま `commonMain` に持ち運びやすい形に揃えてある。フォントファミリは指定せず OS のデフォルトに任せる
 * （iOS は SF Pro、Android は Roboto / Noto Sans JP にフォールバックする想定）。
 */
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
    headline = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold),
    title = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    body = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    caption = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
    sectionLabel = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
    amount = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Bold),
    amountUnit = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
)
