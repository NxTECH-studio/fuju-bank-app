package studio.nxtech.fujubank.features.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import studio.nxtech.fujubank.R
import studio.nxtech.fujubank.theme.FujupayColors

/**
 * ホーム画面ヘッダー：左 48×48 空 / 中央 fujupay ロゴ（fuju キャラクター + ワードマーク）/
 * 右 通知ベル（赤ドット）。Figma `89:12356` 準拠。
 */
@Composable
fun FujupayHeader(
    onNotificationClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(modifier = Modifier.size(48.dp))
        Image(
            painter = painterResource(R.drawable.fujupay_full_logo),
            contentDescription = "fujupay",
            modifier = Modifier.height(29.dp),
            contentScale = ContentScale.Fit,
        )
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onNotificationClick),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_notifications),
                contentDescription = "通知",
                modifier = Modifier.size(24.dp),
            )
            // 赤ドット（白縁付き）。Figma では円 r=3.5、stroke 2 で 8x8 相当。
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .offset(x = 7.dp, y = (-7).dp)
                    .clip(CircleShape)
                    .background(FujupayColors.Background)
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF0000)),
            )
        }
    }
}
