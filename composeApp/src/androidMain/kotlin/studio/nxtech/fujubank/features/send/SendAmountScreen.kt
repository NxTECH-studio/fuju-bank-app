package studio.nxtech.fujubank.features.send

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import studio.nxtech.fujubank.R
import studio.nxtech.fujubank.format.CurrencyFormatter
import studio.nxtech.fujubank.theme.FujuBankColors
import studio.nxtech.fujubank.theme.NotoSansJP

/**
 * 送金フロー Step 2 — 金額入力 + プレビュー画面。
 *
 * Figma `437-22416`（チャージ画面）のテンプレートを踏襲し、ピンクの大 CTA「送金する」と
 * AlertDialog による最終確認を組み合わせる。
 *
 * 完了時は [onComplete] を呼び、親で Snackbar 表示 + ホーム自動遷移を行う。
 */
@Composable
fun SendAmountScreen(
    viewModel: SendFlowViewModel,
    onBack: () -> Unit,
    onComplete: (transactionId: String, newBalance: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recipient = state.recipient

    // 完了 Submission に遷移したら 1 度だけ親へ通知する。
    LaunchedEffect(state.submission) {
        val submission = state.submission
        if (submission is SendFlowState.Submission.Success) {
            onComplete(submission.transactionId, submission.newBalance)
        }
    }

    // recipient が null（プロセス再生成等）の場合は Step 1 に戻すリクエストを上に通知。
    if (recipient == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val isOverBalance = state.amount > state.balance
    val canSubmit = state.amount > 0L && !isOverBalance &&
        state.submission !is SendFlowState.Submission.Submitting

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FujuBankColors.Background),
    ) {
        Header(onBack = onBack, backEnabled = state.submission !is SendFlowState.Submission.Submitting)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RecipientChip(
                name = recipient.name,
                publicId = recipient.publicId,
            )
            AmountDisplay(amount = state.amount)
            if (isOverBalance) {
                Text(
                    text = "残高が不足しています",
                    style = TextStyle(
                        fontFamily = NotoSansJP,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = FujuBankColors.Error,
                    ),
                )
            }
            BalancePreviewCard(
                balanceAfter = (state.balance - state.amount).coerceAtLeast(0L),
            )
            state.error?.let { errMsg ->
                Text(
                    text = errMsg,
                    style = TextStyle(
                        fontFamily = NotoSansJP,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = FujuBankColors.Error,
                    ),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = viewModel::showAmountConfirm,
                enabled = canSubmit,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FujuBankColors.BrandPink,
                    contentColor = FujuBankColors.Surface,
                    disabledContainerColor = Color(0xFFE6E6E6),
                    disabledContentColor = Color(0xFFC3C3CA),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                if (state.submission is SendFlowState.Submission.Submitting) {
                    CircularProgressIndicator(
                        color = FujuBankColors.Surface,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "送金中...",
                        style = TextStyle(
                            fontFamily = NotoSansJP,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                } else {
                    Text(
                        text = "送金する",
                        style = TextStyle(
                            fontFamily = NotoSansJP,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }
            }
            NumericKeypad(
                onDigit = viewModel::onDigitAppend,
                onDelete = viewModel::onDigitDelete,
                enabled = state.submission !is SendFlowState.Submission.Submitting,
            )
            Spacer(modifier = Modifier.size(8.dp))
        }
    }

    if (state.showAmountConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissAmountConfirm,
            title = {
                Text(
                    text = "確認",
                    style = TextStyle(
                        fontFamily = NotoSansJP,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = FujuBankColors.TextPrimary,
                    ),
                )
            },
            text = {
                Text(
                    text = "${recipient.name}さんに ${CurrencyFormatter.formatAmount(state.amount)}${CurrencyFormatter.UNIT} 送りますか？",
                    style = TextStyle(
                        fontFamily = NotoSansJP,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = FujuBankColors.TextPrimary,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::submit) {
                    Text(
                        text = "送金する",
                        style = TextStyle(
                            fontFamily = NotoSansJP,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FujuBankColors.BrandPink,
                        ),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissAmountConfirm) {
                    Text(
                        text = "キャンセル",
                        style = TextStyle(
                            fontFamily = NotoSansJP,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = FujuBankColors.TextSecondary,
                        ),
                    )
                }
            },
            containerColor = FujuBankColors.Surface,
        )
    }
}

@Composable
private fun Header(onBack: () -> Unit, backEnabled: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "送金",
            style = TextStyle(
                fontFamily = NotoSansJP,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = FujuBankColors.TextPrimary,
            ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(48.dp)
                .clip(CircleShape)
                .let { if (backEnabled) it.clickable(onClick = onBack) else it },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_chevron_left),
                contentDescription = "戻る",
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(modifier = Modifier.size(48.dp).align(Alignment.CenterEnd))
    }
}

@Composable
private fun RecipientChip(name: String, publicId: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(FujuBankColors.AvatarPerson),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_avatar_tomato),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
        }
        Column {
            Text(
                text = name,
                style = TextStyle(
                    fontFamily = NotoSansJP,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FujuBankColors.TextPrimary,
                ),
            )
            Text(
                text = "#" + publicId.takeLast(4),
                style = TextStyle(
                    fontFamily = NotoSansJP,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = FujuBankColors.TextTertiary,
                ),
            )
        }
    }
}

@Composable
private fun AmountDisplay(amount: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = CurrencyFormatter.formatAmount(amount),
            style = TextStyle(
                fontFamily = NotoSansJP,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = FujuBankColors.TextPrimary,
            ),
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = CurrencyFormatter.UNIT,
            style = TextStyle(
                fontFamily = NotoSansJP,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = FujuBankColors.TextSecondary,
            ),
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
}

@Composable
private fun BalancePreviewCard(balanceAfter: Long) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFFEAF6))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "送金後の残高",
                style = TextStyle(
                    fontFamily = NotoSansJP,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = FujuBankColors.TextSecondary,
                ),
            )
            Text(
                text = CurrencyFormatter.formatFujus(balanceAfter),
                style = TextStyle(
                    fontFamily = NotoSansJP,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = FujuBankColors.TextPrimary,
                ),
            )
        }
    }
}

/**
 * 0-9 と削除キーを 4 行 x 3 列で並べる簡易数字パッド。
 *
 * Compose の TextField + ソフトキーボードを使わない理由: 大金額表示に直接バインドし、
 * カスタム書式（`12,020`）を維持しながら 1 桁ずつ確実に追記/削除させたいため。
 */
@Composable
private fun NumericKeypad(
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    enabled: Boolean,
) {
    val rows = listOf(
        listOf(KeyButton.Digit(1), KeyButton.Digit(2), KeyButton.Digit(3)),
        listOf(KeyButton.Digit(4), KeyButton.Digit(5), KeyButton.Digit(6)),
        listOf(KeyButton.Digit(7), KeyButton.Digit(8), KeyButton.Digit(9)),
        listOf(KeyButton.Empty, KeyButton.Digit(0), KeyButton.Delete),
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when (key) {
                                    is KeyButton.Empty -> Color.Transparent
                                    else -> FujuBankColors.Surface
                                },
                            )
                            .let {
                                when (key) {
                                    is KeyButton.Digit -> if (enabled) it.clickable { onDigit(key.value) } else it
                                    is KeyButton.Delete -> if (enabled) it.clickable(onClick = onDelete) else it
                                    is KeyButton.Empty -> it
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        when (key) {
                            is KeyButton.Digit -> Text(
                                text = key.value.toString(),
                                style = TextStyle(
                                    fontFamily = NotoSansJP,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = FujuBankColors.TextPrimary,
                                ),
                            )
                            is KeyButton.Delete -> Text(
                                text = "⌫",
                                style = TextStyle(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = FujuBankColors.TextPrimary,
                                ),
                            )
                            is KeyButton.Empty -> Unit
                        }
                    }
                }
            }
        }
    }
}

private sealed class KeyButton {
    data class Digit(val value: Int) : KeyButton()
    data object Delete : KeyButton()
    data object Empty : KeyButton()
}
