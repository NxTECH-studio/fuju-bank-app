package studio.nxtech.fujubank

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import org.koin.android.ext.android.inject
import studio.nxtech.fujubank.session.SessionStore
import studio.nxtech.fujubank.splash.SplashConfig

class MainActivity : ComponentActivity() {
    private val sessionStore: SessionStore by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        // installSplashScreen は super.onCreate より前に呼ぶ必要がある（公式ガイドライン）。
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // bootstrap が即座に完了しても min-duration の間はロゴを見せる。
        // 実際の bootstrap 起動は App() Composable 内の LaunchedEffect 任せ。
        val startedAt = SystemClock.uptimeMillis()
        splashScreen.setKeepOnScreenCondition {
            val elapsed = SystemClock.uptimeMillis() - startedAt
            !sessionStore.bootstrapped.value || elapsed < SplashConfig.MIN_DURATION_MS
        }

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
