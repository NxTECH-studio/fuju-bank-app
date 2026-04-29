package studio.nxtech.fujubank.features.auth

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import studio.nxtech.fujubank.R

/**
 * ログイン画面（Android）— Figma node 302-2698 準拠。
 *
 * - 背景は splash と同じ `#F6F7F9`（Subtract 装飾はオープニング画面以外では出さない方針）。
 * - ヘッダにワードマーク `fuju pay` のみ表示（戻る矢印は導線上は無効、視覚的な対称のため左に配置）。
 * - 入力欄は flat な rounded-16 白カード（M3 OutlinedTextField ではなく BasicTextField で見た目を Figma に揃える）。
 * - ログイン CTA は底部固定（rounded-16, ブランドピンク `#FF1E9E`）。
 * - 「Googleで続ける」「新規登録」リンクは A2f 以降で配線するため本画面ではタップ無効。
 */
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onSignupClick: () -> Unit = {},
    onDebugSkip: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val canSubmit = state.identifier.isNotBlank() && state.password.isNotBlank() && !state.isSubmitting

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.fuju_splash_bg)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
        ) {
            Header()
            Spacer(Modifier.weight(1f))
            LoginCard(
                identifier = state.identifier,
                password = state.password,
                isSubmitting = state.isSubmitting,
                onIdentifierChange = viewModel::onIdentifierChange,
                onPasswordChange = viewModel::onPasswordChange,
                onSignupClick = onSignupClick,
            )
            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            BottomCta(
                isSubmitting = state.isSubmitting,
                enabled = canSubmit,
                onClick = viewModel::submit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 16.dp),
            )
            if (onDebugSkip != null) {
                DebugSkipButton(
                    onClick = onDebugSkip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .padding(bottom = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun Header() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_left),
                contentDescription = null,
                tint = Color(0xFF111111),
                modifier = Modifier.size(24.dp),
            )
        }
        Image(
            painter = painterResource(R.drawable.fuju_wordmark),
            contentDescription = "fuju pay",
            modifier = Modifier
                .height(28.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(modifier = Modifier.size(48.dp))
    }
}

@Composable
private fun LoginCard(
    identifier: String,
    password: String,
    isSubmitting: Boolean,
    onIdentifierChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSignupClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "ログイン",
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111111),
                ),
            )
            Text(
                text = "メールまたは公開IDを入力",
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF6E6F72),
                ),
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FlatTextField(
                    value = identifier,
                    onValueChange = onIdentifierChange,
                    placeholder = "メールアドレス または ユーザーID",
                    enabled = !isSubmitting,
                    keyboardType = KeyboardType.Email,
                )
                FlatTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    placeholder = "パスワード",
                    enabled = !isSubmitting,
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
                GoogleButton()
                SignupLink(onClick = onSignupClick)
            }
        }
    }
}

@Composable
private fun FlatTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    keyboardType: KeyboardType,
    isPassword: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            cursorBrush = SolidColor(Color(0xFF111111)),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = LocalTextStyle.current.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF111111),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFDADBDF),
                        ),
                    )
                }
                innerTextField()
            },
        )
    }
}

@Composable
private fun DividerWithLabel(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .clip(RoundedCornerShape(27.dp))
                .background(Color(0xFFE9E9EC)),
        )
        Text(
            text = label,
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFFC5C5CB),
            ),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .clip(RoundedCornerShape(27.dp))
                .background(Color(0xFFE9E9EC)),
        )
    }
}

@Composable
private fun GoogleButton() {
    // Google OAuth は本タスクのスコープ外。視覚的なフィデリティのため枠だけ用意し、タップ無効。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.google_g),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(16.dp))
        Text(
            text = "Googleで続ける",
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF111111),
            ),
        )
    }
}

@Composable
private fun SignupLink(onClick: () -> Unit) {
    // 「新規登録」のタップでサインアップフロー (A2f) に遷移する。文言・配色は変更しない。
    // AnnotatedString のヒットテストは粗いので、Text 全体をクリック領域として扱う。
    val annotated = buildAnnotatedString {
        withStyle(
            SpanStyle(
                color = Color(0xFF6E6F72),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
        ) {
            append("アカウントをお持ちでない方は ")
        }
        withStyle(
            SpanStyle(
                color = Color(0xFFFF1E9E),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textDecoration = TextDecoration.Underline,
            ),
        ) {
            append("新規登録")
        }
    }
    Text(
        text = annotated,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun BottomCta(
    isSubmitting: Boolean,
    enabled: Boolean,
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
        border = BorderStroke(0.dp, Color.Transparent),
        modifier = modifier.height(48.dp),
    ) {
        if (isSubmitting) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = Color.White,
            )
            Spacer(Modifier.size(8.dp))
        }
        Text(
            text = if (isSubmitting) "ログイン中..." else "ログイン",
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

/**
 * debug ビルド限定の認証スキップ CTA。
 *
 * 本番 UI に紛れた場合に一目で識別できるように、OutlinedButton + 「[DEBUG]」プレフィクス
 * + グレー枠 + 細字で本番 CTA と差別化する。release ビルドでは呼び出し側が onDebugSkip = null
 * を渡すため、このコンポーザブル自体が合成対象にならない。
 */
@Composable
private fun DebugSkipButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFC5C5CB)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Color(0xFF6E6F72),
        ),
        modifier = modifier.height(44.dp),
    ) {
        Text(
            text = "[DEBUG] ログインせず進む",
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
            ),
        )
    }
}

// Preview は LoginViewModel が Koin DI に依存しているため UI 全体は常駐レンダリングできない。
// 代わりにレイアウト確認用のスタブとして LoginCard / Header / BottomCta を直接組む。
// 認証 ViewModel との接続検証は実機 / エミュレータ起動で行う前提。
@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun LoginScreenLayoutPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F7F9)),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
            Header()
            Spacer(Modifier.weight(1f))
            LoginCard(
                identifier = "",
                password = "",
                isSubmitting = false,
                onIdentifierChange = {},
                onPasswordChange = {},
                onSignupClick = {},
            )
            Spacer(Modifier.weight(1f))
            BottomCta(
                isSubmitting = false,
                enabled = false,
                onClick = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 16.dp),
            )
            DebugSkipButton(
                onClick = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 16.dp),
            )
        }
    }
}
