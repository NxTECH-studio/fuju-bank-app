package studio.nxtech.fujubank.features.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import studio.nxtech.fujubank.R

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
            painter = painterResource(R.drawable.ic_logo_fujupay),
            contentDescription = "fujupay",
            modifier = Modifier.height(29.dp),
            contentScale = ContentScale.Fit,
        )
        NotificationBellButton(onClick = onNotificationClick)
    }
}
