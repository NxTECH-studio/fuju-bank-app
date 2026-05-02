package studio.nxtech.fujubank.features.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import studio.nxtech.fujubank.R
import studio.nxtech.fujubank.theme.FujuBankColors

/**
 * 通知ベル（48dp タップ領域 + 24dp アイコン + 赤ドット）。
 *
 * ホーム画面・取引履歴画面どちらからも参照される共通コンポーネント。
 * Figma では `89:12356` (home) と `410:20343` (transaction history) の右上に配置される。
 */
@Composable
fun NotificationBellButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_notifications),
            contentDescription = "通知",
            modifier = Modifier.size(24.dp),
        )
        // 赤ドット（白縁付き）。Figma では円 r=3.5、stroke 2 で 8x8 相当。
        // 外側 8dp 円を背景色（オフホワイト）で塗り、その上に 5dp の赤円を中心に重ねて縁を表現。
        Box(
            modifier = Modifier
                .size(8.dp)
                .offset(x = 7.dp, y = (-7).dp)
                .background(color = FujuBankColors.Background, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(color = FujuBankColors.NotificationDot, shape = CircleShape),
            )
        }
    }
}
