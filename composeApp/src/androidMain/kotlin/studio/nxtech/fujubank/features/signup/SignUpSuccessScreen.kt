package studio.nxtech.fujubank.features.signup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.nxtech.fujubank.theme.NotoSansJP

/**
 * Screen 3: 認証成功（Figma `383-16105`）。完了 CTA で Welcome に戻る（モック挙動）。
 */
@Composable
fun SignUpSuccessScreen(onFinish: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SignUpTokens.Background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 10.dp),
        ) {
            // 戻るは無効化。視覚対称のためロゴのみ中央に配置。
            SignUpHeader(onBack = null)
            Spacer(Modifier.weight(1f))
            Text(
                text = "認証が\n成功しました",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                style = TextStyle(
                    fontFamily = NotoSansJP,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = SignUpTokens.PrimaryText,
                    textAlign = TextAlign.Center,
                    lineHeight = 48.sp,
                ),
            )
            Spacer(Modifier.weight(1f))
            PageIndicator(
                total = 3,
                activeIndex = 2,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 12.dp),
            )
            PrimaryButton(
                text = "次へ",
                enabled = true,
                onClick = onFinish,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 16.dp),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun SignUpSuccessScreenPreview() {
    SignUpSuccessScreen(onFinish = {})
}
