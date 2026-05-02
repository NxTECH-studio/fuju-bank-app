package studio.nxtech.fujubank.features.signup

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Screen 2: 二段階認証 OTP（Figma `383-14941` / `383-16473`）。
 *
 * モックのため任意の 6 桁数字で「確認する」を押せば成功扱い。
 * 実装は 1 個の hidden BasicTextField + 表示用 6 Box の構成にして、backspace/paste の挙動を OS 標準に任せる。
 */
@Composable
fun SignUpOtpScreen(
    viewModel: SignUpFlowViewModel,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SignUpOtpContent(
        otp = state.otp,
        onOtpChange = viewModel::onOtpChange,
        onConfirm = onConfirm,
        onBack = onBack,
    )
}

@Composable
private fun SignUpOtpContent(
    otp: String,
    onOtpChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    val canSubmit = otp.length == SignUpFlowViewModel.OTP_LENGTH

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
            SignUpHeader(onBack = onBack)
            Spacer(Modifier.height(24.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "二段階認証",
                    style = TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = SignUpTokens.PrimaryText,
                    ),
                )
                Text(
                    text = "登録したメールに6桁のコードを送信しました",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = SignUpTokens.SubText,
                        lineHeight = 21.sp,
                    ),
                )
            }
            Spacer(Modifier.height(32.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                // 表示用のボックス列。
                OtpBoxes(
                    otp = otp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                )
                // ヒットテストを取りこぼさないため hidden TextField を同位置に重ね、フォーカスを集約する。
                BasicTextField(
                    value = otp,
                    onValueChange = onOtpChange,
                    singleLine = true,
                    cursorBrush = SolidColor(Color.Transparent),
                    textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(horizontal = 14.dp)
                        .focusRequester(focusRequester)
                        .focusable(),
                )
            }

            Spacer(Modifier.weight(1f))
            PageIndicator(
                total = 3,
                activeIndex = 1,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 12.dp),
            )
            PrimaryButton(
                text = "確認する",
                enabled = canSubmit,
                onClick = onConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun OtpBoxes(
    otp: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(SignUpFlowViewModel.OTP_LENGTH) { index ->
            val char = otp.getOrNull(index)
            val isCursor = index == otp.length
            OtpBox(char = char, isCursor = isCursor)
        }
    }
}

@Composable
private fun OtpBox(
    char: Char?,
    isCursor: Boolean,
) {
    Column(
        modifier = Modifier
            .width(52.dp)
            .height(60.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            if (char != null) {
                Text(
                    text = char.toString(),
                    style = TextStyle(
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = SignUpTokens.OtpUnderlineActive,
                    ),
                )
            }
        }
        when {
            char != null -> Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .background(SignUpTokens.OtpUnderlineActive),
            )
            isCursor -> Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .background(SignUpTokens.OtpUnderlineActive),
            )
            else -> Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(SignUpTokens.OtpUnderlineIdle),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun SignUpOtpEmptyPreview() {
    SignUpOtpContent(
        otp = "",
        onOtpChange = {},
        onConfirm = {},
        onBack = {},
    )
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun SignUpOtpPartialPreview() {
    SignUpOtpContent(
        otp = "1234",
        onOtpChange = {},
        onConfirm = {},
        onBack = {},
    )
}
