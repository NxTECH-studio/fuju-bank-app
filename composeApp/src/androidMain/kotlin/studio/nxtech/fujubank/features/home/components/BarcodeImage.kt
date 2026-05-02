package studio.nxtech.fujubank.features.home.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import studio.nxtech.fujubank.theme.FujupayColors

/**
 * Code128 風の縞模様プレースホルダー。
 *
 * MVP 段階では実際の Code128 エンコーダを使わず、`content` のハッシュから決定的に
 * バー幅を生成して見た目だけ合わせている。読み取り目的の用途には使えない。
 *
 * TODO(asset): Figma の本物のバーコード SVG / 軽量な Code128 ライブラリに差し替える。
 */
@Composable
fun BarcodeImage(
    content: String,
    modifier: Modifier = Modifier,
    barColor: Color = FujupayColors.TextPrimary,
) {
    // content から疑似ランダムにバー幅と空白幅を決める（描画ごとにブレないように memoize）。
    val pattern = remember(content) { generatePattern(content) }
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val totalUnits = pattern.sumOf { it.units }.coerceAtLeast(1)
            val unitWidth = size.width / totalUnits
            var x = 0f
            pattern.forEach { stripe ->
                val width = unitWidth * stripe.units
                if (stripe.filled) {
                    drawRect(
                        color = barColor,
                        topLeft = Offset(x, 0f),
                        size = Size(width, size.height),
                    )
                }
                x += width
            }
        }
    }
}

private data class Stripe(val units: Int, val filled: Boolean)

private fun generatePattern(content: String): List<Stripe> {
    // 単純な決定的ハッシュ：content の各文字コードを混ぜる。
    var seed = 0x9E3779B9.toInt() xor content.hashCode()
    val result = mutableListOf<Stripe>()
    var filled = true
    repeat(60) {
        seed = seed * 1664525 + 1013904223
        val width = ((seed ushr 24) and 0x03) + 1 // 1..4 units
        result += Stripe(units = width, filled = filled)
        filled = !filled
    }
    return result
}
