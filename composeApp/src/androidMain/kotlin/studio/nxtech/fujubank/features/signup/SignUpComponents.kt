package studio.nxtech.fujubank.features.signup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.nxtech.fujubank.R

/**
 * サインアップ 3 画面で共通利用する UI 部品群。
 *
 * 既存 LoginScreen でも類似のパーツが組まれているが、A2f では UI のみで API/Koin 依存がないため
 * features/signup 配下にローカルな private 想定で寄せる。LoginScreen 側の移行は別タスク扱い。
 */

internal object SignUpTokens {
    val Background = Color(0xFFF6F7F9)
    val Card = Color.White
    val Primary = Color(0xFFFF1E9E)
    val PrimaryText = Color(0xFF111111)
    val SecondaryText = Color(0xFF6E6F72)
    val Placeholder = Color(0xFFDADBDF)
    val SubText = Color(0xFF64748B)
    val LinkBlue = Color(0xFF006CD7)
    val DividerLine = Color(0xFFE9E9EC)
    val DividerLabel = Color(0xFFC5C5CB)
    val IndicatorActive = Color(0xFF111111)
    val IndicatorInactive = Color(0xFFE1E2E4)
    val OtpUnderlineActive = Color(0xFF333436)
    val OtpUnderlineIdle = Color(0xFFE8E9ED)
    val CtaDisabledBg = Color(0xFFE6E6E6)
    val CtaDisabledText = Color(0xFFC3C3CA)
}

@Composable
internal fun SignUpHeader(
    onBack: (() -> Unit)?,
) {
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
                .let { if (onBack != null) it.clickable(onClick = onBack) else it },
            contentAlignment = Alignment.Center,
        ) {
            if (onBack != null) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_left),
                    contentDescription = "戻る",
                    tint = SignUpTokens.PrimaryText,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Image(
            painter = painterResource(R.drawable.fuju_wordmark),
            contentDescription = "fuju pay",
            modifier = Modifier.height(28.dp),
            contentScale = ContentScale.Fit,
        )
        Spacer(modifier = Modifier.size(48.dp))
    }
}

@Composable
internal fun BankTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    isPassword: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SignUpTokens.Card),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            cursorBrush = SolidColor(SignUpTokens.PrimaryText),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = LocalTextStyle.current.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = SignUpTokens.PrimaryText,
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
                            color = SignUpTokens.Placeholder,
                        ),
                    )
                }
                innerTextField()
            },
        )
    }
}

@Composable
internal fun PrimaryButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = SignUpTokens.Primary,
            contentColor = Color.White,
            disabledContainerColor = SignUpTokens.CtaDisabledBg,
            disabledContentColor = SignUpTokens.CtaDisabledText,
        ),
        border = BorderStroke(0.dp, Color.Transparent),
        modifier = modifier.height(48.dp),
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

@Composable
internal fun DividerWithLabel(label: String) {
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
                .background(SignUpTokens.DividerLine),
        )
        Text(
            text = label,
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = SignUpTokens.DividerLabel,
            ),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .clip(RoundedCornerShape(27.dp))
                .background(SignUpTokens.DividerLine),
        )
    }
}

@Composable
internal fun GoogleSignInButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SignUpTokens.Card)
            .clickable(onClick = onClick),
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
                color = SignUpTokens.PrimaryText,
            ),
        )
    }
}

/**
 * Figma 準拠のページインジケータ。アクティブは 35x6 の角丸ピル、非アクティブは 6x6 の円。
 *
 * Figma の指定通り画面1のみ 4 ドット、画面2/3 は 3 ドットで表現する（実 Pager は使わない）。
 */
@Composable
internal fun PageIndicator(
    total: Int,
    activeIndex: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { index ->
            if (index == activeIndex) {
                Box(
                    modifier = Modifier
                        .width(35.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(SignUpTokens.IndicatorActive),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(SignUpTokens.IndicatorInactive),
                )
            }
        }
    }
}

@Composable
internal fun LoginRedirectLink(onLoginClick: () -> Unit) {
    val annotated = buildAnnotatedString {
        withStyle(
            SpanStyle(
                color = SignUpTokens.SecondaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
        ) {
            append("アカウントをお持ちの方は ")
        }
        withStyle(
            SpanStyle(
                color = SignUpTokens.Primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textDecoration = TextDecoration.Underline,
            ),
        ) {
            append("ログイン")
        }
    }
    // 文言全体を 1 つのクリック領域にする。Figma 上もリンク部分のみを精緻にヒット領域化する仕様ではない。
    Text(
        text = annotated,
        modifier = Modifier.clickable(onClick = onLoginClick),
    )
}

@Composable
internal fun LegalAgreementText() {
    // 利用規約 / プライバシーポリシーの遷移先実装は本タスクのスコープ外。視覚のみで配線しない。
    val annotated = buildAnnotatedString {
        withStyle(
            SpanStyle(
                color = SignUpTokens.SecondaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
        ) {
            append("登録することで、")
        }
        withStyle(
            SpanStyle(
                color = SignUpTokens.LinkBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textDecoration = TextDecoration.Underline,
            ),
        ) {
            append("利用規約")
        }
        withStyle(
            SpanStyle(
                color = SignUpTokens.SecondaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
        ) {
            append(" と ")
        }
        withStyle(
            SpanStyle(
                color = SignUpTokens.LinkBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textDecoration = TextDecoration.Underline,
            ),
        ) {
            append("プライバシーポリシー")
        }
        withStyle(
            SpanStyle(
                color = SignUpTokens.SecondaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
        ) {
            append(" に同意します")
        }
    }
    Text(text = annotated)
}
