package studio.nxtech.fujubank.features.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import studio.nxtech.fujubank.theme.FujuBankColors

/**
 * ホーム画面の 4 アクション（取引履歴 / 送る・もらう / スキャン / チャージ）。Figma `89:12356` 準拠。
 *
 * 各タイルは白い丸角矩形 (rounded 20)、内側に 28dp のブランドカラーアイコン + 10sp Bold のラベル。
 * アイコン SVG はブランドカラーが焼き込み済みのため `Image` で描画して `tint` は使わない。
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
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // 暫定: 旧決済アプリ専用カテゴリ色 (ActionPurple/Green/Blue) を削除したため、
        // 残ったブランドカラーで仮置きしている。ホーム画面そのものが
        // bank-rebranding-home タスクで作り直される予定なのでこのタイル群は撤去される。
        ActionTile(
            iconRes = R.drawable.ic_history,
            label = "取引履歴",
            labelColor = FujuBankColors.TextPrimary,
            onClick = onTransactionHistory,
            modifier = Modifier.weight(1f),
        )
        ActionTile(
            iconRes = R.drawable.ic_send,
            label = "送る・もらう",
            labelColor = FujuBankColors.TextPrimary,
            onClick = onSendReceive,
            modifier = Modifier.weight(1f),
        )
        ActionTile(
            iconRes = R.drawable.ic_qr_scanner,
            label = "スキャン",
            labelColor = FujuBankColors.BrandPink,
            onClick = onScan,
            modifier = Modifier.weight(1f),
        )
        ActionTile(
            iconRes = R.drawable.ic_add_circle,
            label = "チャージ",
            labelColor = FujuBankColors.TextPrimary,
            onClick = onCharge,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ActionTile(
    iconRes: Int,
    label: String,
    labelColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(FujuBankColors.Surface)
            .clickable(onClick = onClick)
            .padding(top = 12.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Figma CSS 仕様: width: 28px / height: 28px / aspect-ratio: 1/1
        // 各 VectorDrawable は viewport 32x32 + group transform で正方化済みなので、
        // ここで size(28.dp) を渡せば 4 つ均一に 1:1 の 28dp 枠で描画される。
        Image(
            painter = painterResource(iconRes),
            contentDescription = label,
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = label,
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = labelColor,
            ),
        )
    }
}
