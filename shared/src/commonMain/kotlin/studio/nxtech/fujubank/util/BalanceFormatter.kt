package studio.nxtech.fujubank.util

/**
 * fuju 残高表示用ユーティリティ。
 *
 * - [formatBalanceFuju]: 1234567 → "1,234,567" のように 3 桁ごとにカンマ区切り。
 *   負値は先頭に "-" を付けたうえで絶対値をフォーマットする。
 * - [MASKED_BALANCE]: 残高マスク表示時の固定文字列。
 *
 * `kotlin.text` のロケール依存 API（NumberFormat 等）は commonMain で使えないため、
 * 純粋なロジックでカンマを挿入する。fuju は整数（小数なし）のため Long で十分。
 */
const val MASKED_BALANCE: String = "--,---,---,---"

/**
 * Swift / Obj-C ブリッジ用の関数版。K/N の `const val` は ObjC エクスポート時に
 * 静的プロパティ扱いになるが、安定して呼べる関数経由を Swift 側では推奨する。
 */
fun maskedBalance(): String = MASKED_BALANCE

fun formatBalanceFuju(value: Long): String {
    if (value == Long.MIN_VALUE) {
        // Long.MIN_VALUE は abs を取れないので特別扱い。実運用では到達しない想定だが
        // クラッシュさせるよりは桁区切りで表示するほうが無難。
        return "-9,223,372,036,854,775,808"
    }
    val negative = value < 0
    val absDigits = (if (negative) -value else value).toString()
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
