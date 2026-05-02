package studio.nxtech.fujubank.features.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import studio.nxtech.fujubank.theme.FujupayColors

/**
 * アカウントタブ画面の Coming Soon プレースホルダ。
 * 実装は A3b（Figma `100:19982`）で行う。
 */
@Composable
fun AccountPlaceholderScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(FujupayColors.Background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "アカウント画面は準備中です",
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = FujupayColors.TextSecondary,
            ),
        )
    }
}
