package studio.nxtech.fujubank.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import studio.nxtech.fujubank.R

/**
 * 起動スプラッシュ (Figma node 175-2457) を Compose 側で再現する画面。
 *
 * Material splash screen API は中央正方形アイコン 1 枚しか描画できず、Figma の
 * 横長合成 (icon + "fuju pay" wordmark + 背景装飾) を表現できないため、OS splash は
 * 背景色のみを表示し、本 Composable で Figma 通りのレイアウトを描く構成にする。
 *
 * 構成:
 * - 背景 `#F6F7F9` (`R.color.fuju_splash_bg`)
 * - 中央付近に Subtract 装飾 (3 枚のリング、Figma の `calc(50%+6.97px)` 相当に
 *   合わせて y を +7dp オフセット)
 * - 中央に icon + wordmark の合成ロゴ
 */
@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.fuju_splash_bg)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.fuju_splash_decoration),
            contentDescription = null,
            modifier = Modifier
                .width(252.dp)
                .height(352.dp)
                .offset(y = 7.dp),
            contentScale = ContentScale.Fit,
        )
        Image(
            painter = painterResource(R.drawable.fuju_logo),
            contentDescription = "fuju pay",
            modifier = Modifier.width(195.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun SplashScreenPreview() {
    SplashScreen()
}
