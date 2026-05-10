package studio.nxtech.fujubank.features.signup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import studio.nxtech.fujubank.theme.NotoSansJP

/**
 * Screen 3 (MFA セットアップ): TOTP 6 桁コード入力。
 *
 * - 既存 MfaVerifyScreen と同じ「hidden TextField + 表示 6 Box」構成。
 * - **CTA タップで明示送信**。6 桁完了時の自動 submit / IME Done での submit は禁止
 *   （CLAUDE.md: 誤入力リカバリを潰さない）。
 * - エラー（TOTP_CODE_INVALID 等）は CTA 下に赤字表示。
 * - 戻るボタン抑止（OS バック / ヘッダボタン両方）。
 */
@Composable
fun MfaCodeScreen(
    viewModel: SignUpFlowViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = true) { /* セットアップ離脱を抑止 */ }

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
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
                .imePadding()
                .padding(horizontal = 10.dp),
        ) {
            SignUpHeader(onBack = null)
            Spacer(Modifier.height(24.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "認証コードを入力",
                    style = TextStyle(
                        fontFamily = NotoSansJP,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = SignUpTokens.PrimaryText,
                    ),
                )
                Text(
                    text = "認証アプリに表示されている 6 桁のコードを入力してください",
                    style = TextStyle(
                        fontFamily = NotoSansJP,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = SignUpTokens.SecondaryText,
                    ),
                )
            }
            Spacer(Modifier.height(28.dp))
            TotpSlotRow(
                code = state.totpCode,
                enabled = !state.isSubmitting,
                onCodeChange = viewModel::onTotpCodeChange,
                focusRequester = focusRequester,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            )
            Spacer(Modifier.weight(1f))
            state.mfaCodeError?.let { error ->
                Text(
                    text = error,
                    style = TextStyle(
                        fontFamily = NotoSansJP,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = ErrorRed,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            PageIndicator(
                total = 4,
                activeIndex = 2,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 12.dp),
            )
            PrimaryButton(
                text = if (state.isSubmitting) "確認中..." else "確認する",
                enabled = state.totpCode.length == SignUpFlowViewModel.TOTP_LENGTH && !state.isSubmitting,
                onClick = viewModel::submitMfaCode,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun TotpSlotRow(
    code: String,
    enabled: Boolean,
    onCodeChange: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = code,
        onValueChange = onCodeChange,
        enabled = enabled,
        singleLine = true,
        cursorBrush = SolidColor(Color.Transparent),
        textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
        // ImeAction.None 固定: IME Done での自動 submit を防ぐ。
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
                for (i in 0 until SignUpFlowViewModel.TOTP_LENGTH) {
                    TotpSlot(
                        digit = code.getOrNull(i)?.toString().orEmpty(),
                        isActive = i == code.length.coerceAtMost(SignUpFlowViewModel.TOTP_LENGTH - 1) && enabled,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
    )
}

@Composable
private fun TotpSlot(
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
                    color = SignUpTokens.PrimaryText,
                ),
            )
        }
        if (isActive) {
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SignUpTokens.PrimaryText),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFB0B0B0)),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MfaCodeScreenPreview() {
    // Preview 用に hot ViewModel を作るには Koin 依存が要るので省略。
}
