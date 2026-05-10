package studio.nxtech.fujubank.features.signup

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import studio.nxtech.fujubank.theme.NotoSansJP

/**
 * Screen 4 (MFA セットアップ): リカバリコード表示。
 *
 * - 8〜10 個の recovery codes を等幅フォントで表示。
 * - 「すべてコピー」でクリップボードに改行区切りでコピー。
 * - 「保存しました」チェックボックスを ON にしないと CTA が押せない。
 * - **戻るボタン抑止**。AuthCore は recovery codes をハッシュ化保存するため再表示不可。
 *   離脱時の警告文言を画面内に明示する。
 * - CTA タップで [SignUpFlowViewModel.confirmRecoveryCodes] が
 *   `signupCompletionSignal.arm()` → `sessionStore.setAuthenticated()` を呼び、
 *   AppRoot の Authenticated 分岐 → Welcome → ホームに遷移する。
 */
@Composable
fun RecoveryCodesScreen(
    viewModel: SignUpFlowViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val codes = state.mfaSetup?.recoveryCodes.orEmpty()
    val clipboard = LocalClipboardManager.current
    BackHandler(enabled = true) { /* セットアップ離脱を抑止 */ }

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
            SignUpHeader(onBack = null)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "リカバリコードを保存",
                    style = TextStyle(
                        fontFamily = NotoSansJP,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = SignUpTokens.PrimaryText,
                    ),
                )
                Text(
                    text = "認証アプリを失った場合のバックアップとして使用します。\n以下のコードは **この画面でしか表示されません**。安全な場所に必ず保存してください。",
                    style = TextStyle(
                        fontFamily = NotoSansJP,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = SignUpTokens.SecondaryText,
                        lineHeight = 20.sp,
                    ),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SignUpTokens.Card)
                    .padding(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (codes.isEmpty()) {
                        Text(
                            text = "リカバリコードを準備中...",
                            style = TextStyle(
                                fontFamily = NotoSansJP,
                                fontSize = 13.sp,
                                color = SignUpTokens.SecondaryText,
                            ),
                        )
                    } else {
                        codes.forEach { code ->
                            Text(
                                text = code,
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = SignUpTokens.PrimaryText,
                                ),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SignUpTokens.Card)
                    .clickable(enabled = codes.isNotEmpty()) {
                        clipboard.setText(AnnotatedString(codes.joinToString(separator = "\n")))
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "すべてコピー",
                    style = TextStyle(
                        fontFamily = NotoSansJP,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SignUpTokens.Primary,
                    ),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .clickable { viewModel.onRecoverySavedChange(!state.recoverySaved) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = state.recoverySaved,
                    onCheckedChange = viewModel::onRecoverySavedChange,
                    colors = CheckboxDefaults.colors(
                        checkedColor = SignUpTokens.Primary,
                        uncheckedColor = SignUpTokens.SecondaryText,
                    ),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "リカバリコードを安全な場所に保存しました",
                    style = TextStyle(
                        fontFamily = NotoSansJP,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = SignUpTokens.PrimaryText,
                    ),
                )
            }
            Spacer(Modifier.height(8.dp))
            PageIndicator(
                total = 4,
                activeIndex = 3,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 12.dp),
            )
            PrimaryButton(
                text = "完了",
                enabled = state.recoverySaved && state.pendingUserId != null,
                onClick = { viewModel.confirmRecoveryCodes() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 16.dp),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun RecoveryCodesScreenPreview() {
    // Preview 用 ViewModel は Koin 依存があるため省略。
}
