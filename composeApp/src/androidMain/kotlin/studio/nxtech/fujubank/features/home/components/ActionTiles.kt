package studio.nxtech.fujubank.features.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.nxtech.fujubank.R
import studio.nxtech.fujubank.theme.FujupayColors

/**
 * ホーム画面の 4 アクション（取引履歴 / 送る・もらう / スキャン / チャージ）。
 *
 * 各タイルは白い丸ボタン + 下部にラベル。アイコンの色だけがカテゴリごとに切り替わる。
 */
@Composable
fun ActionTiles(
    onTransactionHistory: () -> Unit,
    onSendReceive: () -> Unit,
    onScan: () -> Unit,
    onCharge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ActionTile(
            iconRes = R.drawable.ic_history,
            tint = FujupayColors.ActionPurple,
            label = "取引履歴",
            onClick = onTransactionHistory,
        )
        ActionTile(
            iconRes = R.drawable.ic_send,
            tint = FujupayColors.ActionGreen,
            label = "送る・もらう",
            onClick = onSendReceive,
        )
        ActionTile(
            iconRes = R.drawable.ic_qr_scanner,
            tint = FujupayColors.BrandPink,
            label = "スキャン",
            onClick = onScan,
        )
        ActionTile(
            iconRes = R.drawable.ic_add_circle,
            tint = FujupayColors.ActionBlue,
            label = "チャージ",
            onClick = onCharge,
        )
    }
}

@Composable
private fun ActionTile(
    iconRes: Int,
    tint: Color,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(FujupayColors.Surface)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            text = label,
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = FujupayColors.TextSecondary,
            ),
        )
    }
}
