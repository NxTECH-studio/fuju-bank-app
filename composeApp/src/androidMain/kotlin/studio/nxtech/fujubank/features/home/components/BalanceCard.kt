package studio.nxtech.fujubank.features.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import studio.nxtech.fujubank.theme.FujupayColors
import studio.nxtech.fujubank.util.MASKED_BALANCE
import studio.nxtech.fujubank.util.formatBalanceFuju

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
                elevation = 8.dp,
                shape = RoundedCornerShape(32.dp),
                clip = false,
            )
            .clip(RoundedCornerShape(32.dp))
            .background(FujupayColors.Surface)
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        BarcodeImage(
            content = publicId,
            modifier = Modifier
                .fillMaxWidth()
                .height(63.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            QrCodeImage(
                content = publicId,
                modifier = Modifier.size(66.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "現在の残高",
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = FujupayColors.TextSecondary,
                    ),
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = if (revealed) formatBalanceFuju(balanceFuju) else MASKED_BALANCE,
                        style = TextStyle(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = FujupayColors.TextPrimary,
                        ),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "円",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = FujupayColors.TextPrimary,
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
            .clip(RoundedCornerShape(12.dp))
            .background(FujupayColors.Background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (revealed) "隠す" else "表示",
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = FujupayColors.TextSecondary,
            ),
        )
    }
}

