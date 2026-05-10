package studio.nxtech.fujubank.features.signup

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import studio.nxtech.fujubank.theme.NotoSansJP

/**
 * Screen 2 (MFA セットアップ): TOTP QR 表示。
 *
 * - 中央に AuthCore が生成した PNG QR を表示。base64 デコードは Android `BitmapFactory` で行う。
 * - 「QR を再生成」リンクを置き、`mfa/register` の非べき等性に対するエスケープハッチを提供する。
 * - 戻るボタン（ヘッダ・OS バック）は完全に抑止。一度離脱すると secret 失効リスクがあるため。
 */
@Composable
fun MfaQrScreen(
    viewModel: SignUpFlowViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val bundle = state.mfaSetup
    BackHandler(enabled = true) { /* セットアップ離脱を抑止 */ }

    val qrBitmap: ImageBitmap? = remember(bundle?.qrPngBase64) {
        bundle?.qrPngBase64?.let(::decodeQrPng)
    }

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
            // 戻るボタン非表示。視覚対称のためロゴだけ中央に置く。
            SignUpHeader(onBack = null)
            Spacer(Modifier.weight(1f))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    text = "二段階認証の設定",
                    style = TextStyle(
                        fontFamily = NotoSansJP,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = SignUpTokens.PrimaryText,
                    ),
                )
                Text(
                    text = "Google Authenticator などの認証アプリで\n以下の QR コードをスキャンしてください",
                    style = TextStyle(
                        fontFamily = NotoSansJP,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = SignUpTokens.SecondaryText,
                        lineHeight = 21.sp,
                        textAlign = TextAlign.Center,
                    ),
                )

                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SignUpTokens.Card),
                    contentAlignment = Alignment.Center,
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap,
                            contentDescription = "TOTP QR コード",
                            modifier = Modifier
                                .size(196.dp),
                        )
                    } else {
                        Text(
                            text = "QR を準備中...",
                            style = TextStyle(
                                fontFamily = NotoSansJP,
                                fontSize = 13.sp,
                                color = SignUpTokens.SecondaryText,
                            ),
                        )
                    }
                }

                if (bundle?.secret != null) {
                    Text(
                        text = "QR が読めない場合: ${bundle.secret}",
                        style = TextStyle(
                            fontFamily = NotoSansJP,
                            fontSize = 12.sp,
                            color = SignUpTokens.SecondaryText,
                        ),
                    )
                }

                Text(
                    text = "QR を再生成",
                    modifier = Modifier
                        .clickable(enabled = !state.isSubmitting) { viewModel.regenerateMfaQr() },
                    style = TextStyle(
                        fontFamily = NotoSansJP,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = SignUpTokens.Primary,
                        textDecoration = TextDecoration.Underline,
                    ),
                )

                if (state.formError != null) {
                    Text(
                        text = state.formError!!,
                        style = TextStyle(
                            fontFamily = NotoSansJP,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = ErrorRed,
                        ),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            PageIndicator(
                total = 4,
                activeIndex = 1,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 12.dp),
            )
            PrimaryButton(
                text = "コードを入力する",
                enabled = qrBitmap != null && !state.isSubmitting,
                onClick = viewModel::goToMfaCodeInput,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 16.dp),
            )
        }
    }
}

/**
 * AuthCore が data URL から接頭を剥がして渡す純粋 base64 PNG をデコードする。
 *
 * Android API 26 以降は `java.util.Base64` を使えるが、minSdk と一致させるため
 * `android.util.Base64` を採用。失敗時は null を返し UI 側でローディング表示にする。
 */
private fun decodeQrPng(base64: String): ImageBitmap? = runCatching {
    val bytes = Base64.decode(base64, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}.getOrNull()

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MfaQrScreenPreview() {
    // Preview 用にダミー bundle を持つ ViewModel は組み立てづらいので、
    // 視覚確認は実機で行う方針。空 ViewModel ではビルド成立しないため Preview は省略。
}
