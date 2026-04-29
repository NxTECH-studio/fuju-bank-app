package studio.nxtech.fujubank.features.signup

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private const val LOG_TAG = "SignUpCreateScreen"

/**
 * Screen 1: アカウント作成（Figma `383-12951` / `296-2092`）。
 *
 * email + password を入力して OTP 画面へ進む。代替手段として Google 認証を提示するが、
 * 本タスクでは onClick はログ出力のみ。
 */
@Composable
fun SignUpCreateScreen(
    viewModel: SignUpFlowViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onLoginRedirect: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val canSubmit = state.email.isNotBlank() && state.email.contains('@') && state.password.isNotBlank()

    SignUpCreateContent(
        email = state.email,
        password = state.password,
        canSubmit = canSubmit,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onNext = onNext,
        onBack = onBack,
        onLoginRedirect = onLoginRedirect,
    )
}

@Composable
private fun SignUpCreateContent(
    email: String,
    password: String,
    canSubmit: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onLoginRedirect: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SignUpTokens.Background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
        ) {
            SignUpHeader(onBack = onBack)
            Spacer(Modifier.weight(1f))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "アカウントの作成",
                        style = TextStyle(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = SignUpTokens.PrimaryText,
                        ),
                    )
                    Text(
                        text = "メールを入力",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = SignUpTokens.SecondaryText,
                        ),
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BankTextField(
                        value = email,
                        onValueChange = onEmailChange,
                        placeholder = "メールアドレス または ユーザーID",
                        keyboardType = KeyboardType.Email,
                    )
                    BankTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        placeholder = "パスワード",
                        keyboardType = KeyboardType.Password,
                        isPassword = true,
                    )
                }

                DividerWithLabel(label = "または")

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    GoogleSignInButton(
                        onClick = { Log.d(LOG_TAG, "Google sign-in tapped (mock)") },
                    )
                    LoginRedirectLink(onLoginClick = onLoginRedirect)
                }

                LegalAgreementText()
            }
            Spacer(Modifier.weight(1f))
            PageIndicator(
                total = 4,
                activeIndex = 0,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 12.dp),
            )
            PrimaryButton(
                text = "次へ",
                enabled = canSubmit,
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 16.dp),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun SignUpCreateScreenPreview() {
    SignUpCreateContent(
        email = "",
        password = "",
        canSubmit = false,
        onEmailChange = {},
        onPasswordChange = {},
        onNext = {},
        onBack = {},
        onLoginRedirect = {},
    )
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun SignUpCreateScreenFilledPreview() {
    SignUpCreateContent(
        email = "ryota@example.com",
        password = "password",
        canSubmit = true,
        onEmailChange = {},
        onPasswordChange = {},
        onNext = {},
        onBack = {},
        onLoginRedirect = {},
    )
}
