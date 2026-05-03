package studio.nxtech.fujubank.features.placeholder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.nxtech.fujubank.theme.FujuBankColors
import studio.nxtech.fujubank.theme.NotoSansJP

/**
 * 取引履歴 / 送る・もらう など、A3 ではまだ未実装の画面の汎用プレースホルダ。
 * タップで前画面に戻る簡易導線を提供する。
 */
@Composable
fun ComingSoonScreen(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FujuBankColors.Background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = TextStyle(
                fontFamily = NotoSansJP,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = FujuBankColors.TextPrimary,
            ),
        )
        Text(
            text = "この画面は別タスクで実装します",
            style = TextStyle(
                fontFamily = NotoSansJP,
                fontSize = 14.sp,
                color = FujuBankColors.TextSecondary,
            ),
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = "戻る",
            style = TextStyle(
                fontFamily = NotoSansJP,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = FujuBankColors.BrandPink,
            ),
            modifier = Modifier
                .padding(top = 24.dp)
                .clickable(onClick = onBack),
        )
    }
}
