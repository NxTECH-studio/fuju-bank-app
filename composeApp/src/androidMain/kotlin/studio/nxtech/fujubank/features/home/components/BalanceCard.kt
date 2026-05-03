package studio.nxtech.fujubank.features.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.nxtech.fujubank.format.CurrencyFormatter
import studio.nxtech.fujubank.theme.FujuBankColors

/**
 * 残高カード — Figma `709:8658` 準拠のシンプル表示版。
 *
 * - 高さ 154dp、白背景、角丸 32dp、わずかな drop-shadow。
 * - 左寄せに「現在の残高」ラベル(14sp medium) と、48sp Bold の数値 + 20sp Bold の単位「ふじゅ〜」をベースライン揃えで横並び。
 * - 旧デザインにあった QR / バーコード / マスクトグル / publicId 表示は新デザインで撤去された。
 *   （`HomeViewModel.toggleReveal()` は現状残しているが、本画面からは呼ばれない）
 */
@Composable
fun BalanceCard(
    balanceFuju: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(154.dp)
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(32.dp),
                clip = false,
            )
            .clip(RoundedCornerShape(32.dp))
            .background(FujuBankColors.Surface)
            .padding(horizontal = 36.dp, vertical = 0.dp)
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = "現在の残高",
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = FujuBankColors.TextPrimary,
                ),
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = CurrencyFormatter.formatAmount(balanceFuju),
                    style = TextStyle(
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = FujuBankColors.TextPrimary,
                    ),
                )
                Text(
                    text = CurrencyFormatter.UNIT,
                    modifier = Modifier.padding(bottom = 6.dp),
                    style = TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = FujuBankColors.TextPrimary,
                    ),
                )
            }
        }
    }
}
