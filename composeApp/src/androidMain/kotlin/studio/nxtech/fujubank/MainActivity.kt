package studio.nxtech.fujubank

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // installSplashScreen は super.onCreate より前に呼ぶ必要がある（公式ガイドライン）。
        // OS splash は背景色のみを表示する短いフラッシュとして使い、Figma 通りの合成
        // (icon + wordmark + 装飾) は App() Composable 内の SplashScreen で描画する。
        // setKeepOnScreenCondition は使わない（min-duration の保持は Compose 側で行う）。
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
