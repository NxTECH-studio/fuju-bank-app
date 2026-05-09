package studio.nxtech.fujubank.features.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import studio.nxtech.fujubank.R
import studio.nxtech.fujubank.theme.NotoSansJP

/**
 * MFA 検証 → 検証成功後オンボーディング 3 画面の Compose ルート。
 *
 * - [MfaPhase.Input] : OTP 6 スロット入力 + ピンク CTA。
 * - [MfaPhase.Onboarding] (Success / Welcome / Brand) : それぞれ Figma 03 / 04 / 05。
 * - フェード切替: `AnimatedContent` + `fadeIn() / fadeOut()`。
 */
@Composable
fun MfaVerifyScreen(viewModel: MfaVerifyViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.fuju_splash_bg)),
    ) {
        AnimatedContent(
            targetState = state.phase,
            transitionSpec = { fadeIn(tween(durationMillis = 280)) togetherWith fadeOut(tween(durationMillis = 220)) },
            label = "MfaPhase",
            modifier = Modifier.fillMaxSize(),
        ) { phase ->
            when (phase) {
                MfaPhase.Input -> MfaVerifyInputContent(
                    code = state.code,
                    isSubmitting = state.isSubmitting,
                    errorMessage = state.errorMessage,
                    onCodeChange = viewModel::onCodeChange,
                    onSubmit = viewModel::submit,
                    onCancel = viewModel::cancel,
                )
                is MfaPhase.Onboarding -> when (phase.stage) {
                    OnboardingStage.Success -> MfaSuccessContent(onNext = viewModel::advanceOnboarding)
                    OnboardingStage.Welcome -> MfaWelcomeContent(onAdvance = viewModel::advanceOnboarding)
                    OnboardingStage.Brand -> MfaBrandContent(onAdvance = viewModel::advanceOnboarding)
                }
            }
        }
    }
}

// --- Input phase --------------------------------------------------------------

@Composable
private fun MfaVerifyInputContent(
    code: String,
    isSubmitting: Boolean,
    errorMessage: String?,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 14.dp),
    ) {
        AuthHeader(onBack = onCancel, backEnabled = !isSubmitting)
        Spacer(Modifier.height(24.dp))
        TitleAndSubtitle(
            title = "二段階認証",
            // Figma の「登録したメールに 6 桁のコードを送信しました」は誤情報のため
            // TOTP 用の説明文に差し替える。MFA factor は TOTP のまま。
            subtitle = "認証アプリの 6 桁コードを入力してください",
        )
        Spacer(Modifier.weight(1f))
        OtpSlotRow(
            code = code,
            enabled = !isSubmitting,
            onCodeChange = onCodeChange,
            focusRequester = focusRequester,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        )
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = TextStyle(
                    fontFamily = NotoSansJP,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFD32F2F),
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, start = 24.dp, end = 24.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        PrimaryCta(
            label = if (isSubmitting) "確認中..." else "確認する",
            enabled = code.length == OTP_LENGTH && !isSubmitting,
            isLoading = isSubmitting,
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 16.dp),
        )
    }
}

private const val OTP_LENGTH = 6

@Composable
private fun OtpSlotRow(
    code: String,
    enabled: Boolean,
    onCodeChange: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    // 単一の隠し BasicTextField に全文字を集約し、視覚は 6 個の Box で表現する OTP の定番パターン。
    // 6 桁完了時の自動 submit は誤入力からの復帰余地を奪うため行わず、確認は CTA タップ必須に統一する。
    BasicTextField(
        value = code,
        onValueChange = onCodeChange,
        enabled = enabled,
        singleLine = true,
        // テキスト・カーソルとも完全に透明にして、視覚は decorationBox の OtpSlot 群が担当する。
        cursorBrush = SolidColor(Color.Transparent),
        textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.None,
        ),
        modifier = modifier.focusRequester(focusRequester),
        decorationBox = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (i in 0 until OTP_LENGTH) {
                    OtpSlot(
                        digit = code.getOrNull(i)?.toString().orEmpty(),
                        isActive = i == code.length.coerceAtMost(OTP_LENGTH - 1) && enabled,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
    )
}

@Composable
private fun OtpSlot(
    digit: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.height(56.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = digit,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111111),
                ),
            )
        }
        if (isActive) {
            // 現在入力位置: 太い黒の下線。
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF111111)),
            )
        } else {
            // 未入力 / 入力済みスロット: 細い灰色のドット。
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFB0B0B0)),
            )
        }
    }
}

// --- Onboarding: Success ------------------------------------------------------

@Composable
private fun MfaSuccessContent(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 14.dp),
    ) {
        AuthHeader(onBack = null, backEnabled = false)
        Spacer(Modifier.weight(1f))
        Text(
            text = "認証が\n成功しました",
            textAlign = TextAlign.Center,
            style = TextStyle(
                fontFamily = NotoSansJP,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111111),
                lineHeight = 44.sp,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.weight(1f))
        PageIndicator(activeIndex = 1, total = 3)
        PrimaryCta(
            label = "次へ",
            enabled = true,
            isLoading = false,
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 16.dp),
        )
    }
}

@Composable
private fun PageIndicator(activeIndex: Int, total: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until total) {
            val isActive = i == activeIndex
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .height(4.dp)
                    .width(if (isActive) 18.dp else 4.dp)
                    .clip(if (isActive) RoundedCornerShape(2.dp) else CircleShape)
                    .background(if (isActive) Color(0xFF111111) else Color(0xFFB0B0B0)),
            )
        }
    }
}

// --- Onboarding: Welcome ------------------------------------------------------

@Composable
private fun MfaWelcomeContent(onAdvance: () -> Unit) {
    LaunchedEffect(Unit) {
        // 自動進行: 約 1.8 秒間「ようこそ」を表示してから次の Brand 画面へ。
        delay(1800)
        onAdvance()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "ようこそ",
            style = TextStyle(
                fontFamily = NotoSansJP,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111111),
            ),
        )
    }
}

// --- Onboarding: Brand --------------------------------------------------------

@Composable
private fun MfaBrandContent(onAdvance: () -> Unit) {
    LaunchedEffect(Unit) {
        // 自動進行: 約 1.5 秒間ブランドロゴを表示してから setAuthenticated → ホームへ。
        delay(1500)
        onAdvance()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        // fuju 銀行 / fujupay 兼用のブランドロゴ。Figma 05 は fujupay のロゴだが、
        // 既存スプラッシュ／ログイン画面と同一の `fuju_logo.xml` を使い回し、ロゴ系統の
        // 一貫性を担保する。fujupay 専用ロゴへの差し替えは未決事項として残す。
        Image(
            painter = painterResource(R.drawable.fuju_logo),
            contentDescription = "fuju 銀行",
            modifier = Modifier
                .width(196.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

// --- Shared building blocks ---------------------------------------------------

@Composable
private fun AuthHeader(onBack: (() -> Unit)?, backEnabled: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .let { if (onBack != null && backEnabled) it.clickable(onClick = onBack) else it },
            contentAlignment = Alignment.Center,
        ) {
            if (onBack != null) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_left),
                    contentDescription = "戻る",
                    tint = Color(0xFF111111),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Image(
            painter = painterResource(R.drawable.ic_logo_fujupay),
            contentDescription = "fujupay",
            modifier = Modifier.height(24.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(modifier = Modifier.size(48.dp))
    }
}

@Composable
private fun TitleAndSubtitle(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            style = TextStyle(
                fontFamily = NotoSansJP,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111111),
            ),
        )
        Text(
            text = subtitle,
            style = TextStyle(
                fontFamily = NotoSansJP,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF6E6F72),
            ),
        )
    }
}

@Composable
private fun PrimaryCta(
    label: String,
    enabled: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFFF1E9E),
            contentColor = Color.White,
            disabledContainerColor = Color(0xFFE6E6E6),
            disabledContentColor = Color(0xFFC3C3CA),
        ),
        modifier = modifier.height(48.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = Color.White,
            )
            Spacer(Modifier.size(8.dp))
        }
        Text(
            text = label,
            style = TextStyle(
                fontFamily = NotoSansJP,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

// --- Previews -----------------------------------------------------------------

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MfaVerifyInputPreview() {
    MfaVerifyInputContent(
        code = "2968",
        isSubmitting = false,
        errorMessage = null,
        onCodeChange = {},
        onSubmit = {},
        onCancel = {},
    )
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MfaSuccessPreview() {
    MfaSuccessContent(onNext = {})
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MfaWelcomePreview() {
    MfaWelcomeContent(onAdvance = {})
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MfaBrandPreview() {
    MfaBrandContent(onAdvance = {})
}
