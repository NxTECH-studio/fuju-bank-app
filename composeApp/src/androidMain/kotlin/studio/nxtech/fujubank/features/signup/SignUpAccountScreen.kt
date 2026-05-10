package studio.nxtech.fujubank.features.signup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import studio.nxtech.fujubank.theme.NotoSansJP

/**
 * Screen 1: アカウント作成（client-bank-21 で旧 SignUpCreateScreen を置換）。
 *
 * email + password + public_id を入力し、CTA タップで AuthCore `/v1/auth/register` を叩く。
 * 成功時は ViewModel が裏で login → provisionMe → mfa/register まで進めて MfaQr 画面に遷移する。
 *
 * - public_id はリアルタイム検証（4-16 文字、半角英数字）。エラーは欄下に赤字で表示。
 * - 409 エラーは欄下インライン（emailError / publicIdError）+ ログイン画面リンクで誘導。
 * - その他のエラーは CTA 下の formError に表示。
 */
@Composable
fun SignUpAccountScreen(
    viewModel: SignUpFlowViewModel,
    onBack: () -> Unit,
    onLoginRedirect: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val canSubmit = state.email.isNotBlank() &&
        state.password.isNotBlank() &&
        state.publicId.isNotBlank() &&
        state.publicIdError == null &&
        !state.isSubmitting

    SignUpAccountContent(
        email = state.email,
        password = state.password,
        publicId = state.publicId,
        emailError = state.emailError,
        publicIdError = state.publicIdError,
        formError = state.formError,
        canSubmit = canSubmit,
        isSubmitting = state.isSubmitting,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onPublicIdChange = viewModel::onPublicIdChange,
        onSubmit = viewModel::submitAccount,
        onBack = onBack,
        onLoginRedirect = onLoginRedirect,
    )
}

@Composable
private fun SignUpAccountContent(
    email: String,
    password: String,
    publicId: String,
    emailError: String?,
    publicIdError: String?,
    formError: String?,
    canSubmit: Boolean,
    isSubmitting: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPublicIdChange: (String) -> Unit,
    onSubmit: () -> Unit,
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
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 10.dp),
        ) {
            SignUpHeader(onBack = onBack)
            Spacer(Modifier.weight(1f))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
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
                            fontFamily = NotoSansJP,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = SignUpTokens.PrimaryText,
                        ),
                    )
                    Text(
                        text = "メール・パスワード・ユーザー ID を入力",
                        style = TextStyle(
                            fontFamily = NotoSansJP,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = SignUpTokens.SecondaryText,
                        ),
                    )
                }

                FieldWithError(
                    error = emailError,
                ) {
                    BankTextField(
                        value = email,
                        onValueChange = onEmailChange,
                        placeholder = "メールアドレス",
                        keyboardType = KeyboardType.Email,
                        enabled = !isSubmitting,
                    )
                }

                BankTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    placeholder = "パスワード（8 文字以上）",
                    keyboardType = KeyboardType.Password,
                    isPassword = true,
                    enabled = !isSubmitting,
                )

                FieldWithError(
                    error = publicIdError,
                    helper = "半角英数字 4〜16 文字",
                ) {
                    BankTextField(
                        value = publicId,
                        onValueChange = onPublicIdChange,
                        placeholder = "ユーザー ID",
                        keyboardType = KeyboardType.Ascii,
                        enabled = !isSubmitting,
                    )
                }

                LoginRedirectLink(onLoginClick = onLoginRedirect)

                LegalAgreementText()

                if (formError != null) {
                    Text(
                        text = formError,
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
                activeIndex = 0,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 12.dp),
            )
            PrimaryButton(
                text = if (isSubmitting) "送信中..." else "次へ",
                enabled = canSubmit,
                onClick = onSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 16.dp),
            )
        }
    }
    // OAuth (GoogleSignIn) 連携は MVP 範囲外のため UI 自体を撤去している。
}

internal val ErrorRed = Color(0xFFD32F2F)

@Composable
internal fun FieldWithError(
    error: String?,
    helper: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        content()
        if (error != null) {
            Text(
                text = error,
                style = TextStyle(
                    fontFamily = NotoSansJP,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = ErrorRed,
                ),
            )
        } else if (helper != null) {
            Text(
                text = helper,
                style = TextStyle(
                    fontFamily = NotoSansJP,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = SignUpTokens.SecondaryText,
                ),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun SignUpAccountScreenPreview() {
    SignUpAccountContent(
        email = "",
        password = "",
        publicId = "",
        emailError = null,
        publicIdError = null,
        formError = null,
        canSubmit = false,
        isSubmitting = false,
        onEmailChange = {},
        onPasswordChange = {},
        onPublicIdChange = {},
        onSubmit = {},
        onBack = {},
        onLoginRedirect = {},
    )
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun SignUpAccountScreenErrorPreview() {
    SignUpAccountContent(
        email = "ryota@example.com",
        password = "password",
        publicId = "ryo",
        emailError = "このメールアドレスは既に登録されています",
        publicIdError = "4 文字以上必要です",
        formError = null,
        canSubmit = false,
        isSubmitting = false,
        onEmailChange = {},
        onPasswordChange = {},
        onPublicIdChange = {},
        onSubmit = {},
        onBack = {},
        onLoginRedirect = {},
    )
}
