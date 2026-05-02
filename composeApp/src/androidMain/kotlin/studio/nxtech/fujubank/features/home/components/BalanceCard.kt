package studio.nxtech.fujubank.features.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.nxtech.fujubank.format.CurrencyFormatter
import studio.nxtech.fujubank.theme.FujuBankColors
import studio.nxtech.fujubank.util.maskedBalance

/**
 * 残高カード（バーコード + QR + 残高 + 表示トグル）。Figma `89:12356` のメインカード。
 */
@Composable
fun BalanceCard(
    publicId: String,
    balanceFuju: Long,
    revealed: Boolean,
    onToggleReveal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(32.dp),
                clip = false,
            )
            .clip(RoundedCornerShape(32.dp))
            .background(FujuBankColors.Surface)
            .padding(30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BarcodeImage(
            content = publicId,
            modifier = Modifier
                .fillMaxWidth()
                .height(63.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QrCodeImage(
                content = publicId,
                modifier = Modifier.size(66.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "現在の残高",
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = FujuBankColors.TextSecondary,
                    ),
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = if (revealed) CurrencyFormatter.formatAmount(balanceFuju) else maskedBalance(),
                        style = TextStyle(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black,
                        ),
                    )
                    Text(
                        text = CurrencyFormatter.UNIT,
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black,
                        ),
                    )
                }
            }
            RevealToggle(
                revealed = revealed,
                onClick = onToggleReveal,
            )
        }
    }
}

@Composable
private fun RevealToggle(
    revealed: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(35.dp))
            .background(FujuBankColors.BrandPink.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (revealed) "隠す" else "表示",
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = FujuBankColors.BrandPink,
            ),
        )
    }
}

