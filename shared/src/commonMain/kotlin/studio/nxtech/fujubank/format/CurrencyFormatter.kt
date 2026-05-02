package studio.nxtech.fujubank.format

/**
 * 通貨表記「ふじゅ〜」を共通化するフォーマッタ。
 *
 * - [formatFujus]: `1234` → `"1,234 ふじゅ〜"` のように 3 桁区切り + 単位サフィックス。
 * - [formatAmount]: `1234` → `"1,234"`。数値部分のみ。UI で数値と単位を別 Text に分けて
 *   フォントサイズを変えたい場合（残高カード / 取引履歴行など）に利用する。
 *
 * `commonMain` から使えるよう、`java.text.NumberFormat` などのロケール依存 API は使わず、
 * `kotlin.text` のみで完結させている。負数は `-1,234 ふじゅ〜` の形式（マイナス→数値→単位）。
 */
object CurrencyFormatter {

    /** 単位文字列。Figma 上の表記に合わせる。 */
    const val UNIT: String = "ふじゅ〜"

    /** 単位と数値の区切り（半角スペース 1 つ）。 */
    private const val UNIT_SEPARATOR: String = " "

    /**
     * 金額を `"1,234 ふじゅ〜"` の形式にフォーマットする。
     */
    fun formatFujus(amount: Long): String = "${formatAmount(amount)}$UNIT_SEPARATOR$UNIT"

    /**
     * 金額の数値部分のみを 3 桁区切りでフォーマットする。
     *
     * UI 上で数値と単位のフォントサイズを変えたい場合に、単位を呼び出し側で別 Text として
     * 描画するために使う。負数は先頭に `-` を付ける。
     */
    fun formatAmount(amount: Long): String {
        if (amount == Long.MIN_VALUE) {
            // Long.MIN_VALUE は abs を取れないので特別扱い。実運用では到達しない想定だが
            // クラッシュさせるよりは桁区切りで表示するほうが無難。
            return "-9,223,372,036,854,775,808"
        }
        val negative = amount < 0
        val absDigits = (if (negative) -amount else amount).toString()
        val withCommas = buildString {
            val len = absDigits.length
            for (i in 0 until len) {
                append(absDigits[i])
                val remaining = len - i - 1
                if (remaining > 0 && remaining % 3 == 0) append(',')
            }
        }
        return if (negative) "-$withCommas" else withCommas
    }
}
