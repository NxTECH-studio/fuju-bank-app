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
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import studio.nxtech.fujubank.R
import studio.nxtech.fujubank.theme.FujupayColors

/**
 * ホーム画面ヘッダー：左 48×48 空 / 中央 fujupay ロゴ / 右 通知ベル（赤ドット）。
 *
 * fujupay ロゴはサインアップ画面と共通の `fuju_logo` を再利用している。Figma の
 * 「fujupay」ワードマーク版に差し替える場合はアセットを別途用意する想定。
 *
 * TODO(asset): Figma `89:12356` の fujupay ロゴ（fuju キャラクター + テキスト）に差し替え。
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
            painter = painterResource(R.drawable.fuju_logo),
            contentDescription = "fujupay",
            modifier = Modifier.height(28.dp),
            contentScale = ContentScale.Fit,
        )
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onNotificationClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_notifications),
                contentDescription = "通知",
                tint = FujupayColors.TextPrimary,
                modifier = Modifier.size(24.dp),
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .offset(x = 8.dp, y = (-8).dp)
                    .clip(CircleShape)
                    .background(FujupayColors.BrandPink)
                    .padding(2.dp),
            )
        }
    }
}
