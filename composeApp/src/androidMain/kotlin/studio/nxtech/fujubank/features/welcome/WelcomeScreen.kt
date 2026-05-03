package studio.nxtech.fujubank.features.welcome

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlinx.coroutines.delay
import studio.nxtech.fujubank.R
import studio.nxtech.fujubank.theme.NotoSansJP

/**
 * サインアップ完了直後に 1 度だけ表示する Welcome シーケンス（Figma node 383-16889 → 383-17075）。
 *
 * 演出は「ようこそ」テキスト → fuju pay ロゴへのクロスフェード自動遷移。
 * 完了時に [onFinish] を呼び、呼び出し側が `signupWelcomePreferences.markCompleted()` /
 * `signupCompletionSignal.consume()` を行ってホームへ進める。
 *
 * 表示時間は Compose / SwiftUI 間で揃えること。両プラットフォームとも `WelcomeTimings`
 * 相当の定数を直値で持つ（共通定数化は別タスクで検討）。
 */
@Composable
fun WelcomeScreen(onFinish: () -> Unit) {
    var phase by rememberSaveable { mutableStateOf(Phase.Text) }
    // configuration change で LaunchedEffect が再起動されても onFinish を二度呼ばないためのガード。
    // markCompleted / consume はどちらも冪等だが、設計として明示的にラッチを切る。
    var hasFinished by rememberSaveable { mutableStateOf(false) }

    val textAlpha by animateFloatAsState(
        targetValue = if (phase == Phase.Text) 1f else 0f,
        animationSpec = tween(durationMillis = CROSSFADE_MS),
        label = "welcome-text-alpha",
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (phase == Phase.Logo) 1f else 0f,
        animationSpec = tween(durationMillis = CROSSFADE_MS),
        label = "welcome-logo-alpha",
    )

    LaunchedEffect(Unit) {
        if (hasFinished) return@LaunchedEffect
        delay(TEXT_VISIBLE_MS.toLong())
        phase = Phase.Logo
        delay(CROSSFADE_MS.toLong() + LOGO_VISIBLE_MS.toLong())
        hasFinished = true
        onFinish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.fuju_splash_bg)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "ようこそ",
            modifier = Modifier.alpha(textAlpha),
            style = TextStyle(
                fontFamily = NotoSansJP,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111111),
                textAlign = TextAlign.Center,
            ),
        )
        Image(
            painter = painterResource(R.drawable.fuju_logo),
            contentDescription = "fuju pay",
            modifier = Modifier
                .width(195.dp)
                .alpha(logoAlpha),
            contentScale = ContentScale.Fit,
        )
    }
}

private enum class Phase { Text, Logo }

private const val TEXT_VISIBLE_MS = 1200
private const val CROSSFADE_MS = 600
private const val LOGO_VISIBLE_MS = 1500

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun WelcomeScreenPreview() {
    WelcomeScreen(onFinish = {})
}
