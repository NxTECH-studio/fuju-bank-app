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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
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
            RecipientChip(publicId = recipient.publicId)
            AmountField(
                amount = state.amount,
                onAmountChange = viewModel::onAmountChange,
                enabled = state.submission !is SendFlowState.Submission.Submitting,
            )
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
            MemoField(
                memo = state.memo,
                onMemoChange = viewModel::onMemoChange,
                enabled = state.submission !is SendFlowState.Submission.Submitting,
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
                    disabledContainerColor = FujuBankColors.DisabledButtonBg,
                    disabledContentColor = FujuBankColors.DisabledButtonText,
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
                    text = "@${recipient.publicId} さんに ${CurrencyFormatter.formatAmount(state.amount)}${CurrencyFormatter.UNIT} 送りますか？",
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
private fun RecipientChip(publicId: String) {
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
        Text(
            text = "@" + publicId,
            style = TextStyle(
                fontFamily = NotoSansJP,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = FujuBankColors.TextPrimary,
            ),
        )
    }
}

/**
 * 金額入力欄。OS 標準の数字キーボード IME を起動し、メモ欄 ([MemoField]) と並列の
 * 「2 つの入力欄」UI として機能する。旧実装の `AmountDisplay` + 画面下 `NumericKeypad`
 * は撤去し、tap 対象（金額 vs メモ）に応じて IME が出し分けられる構成へ変更した。
 *
 * - `KeyboardType.NumberPassword` で **純粋な数字テンキー** を起動する（`Number` だと OS に
 *   よっては `.` `-` `,` 等の記号キーが並ぶため）。`NumberPassword` は IME 種類だけを変える
 *   指定で、表示が bullet (`●`) 化されたりはしない。
 * - `singleLine = true` だが OTP 同様、IME Done での自動 submit はしない（CTA タップ必須）。
 * - 数字以外を `onValueChange` 内で除去（物理キーボード / paste 経路の保険）、先頭 0 を
 *   `trimStart('0')` で正規化、`toLongOrNull()` で parse。Long 範囲外（20 桁超）は null になり
 *   0 扱いで安全側へ。
 * - [TextFieldValue] を Compose 側で保持するのは memo 同様、IME composition 維持のため。
 *   数字キーボードでは composition はほぼ発生しないが、外部 state (amount) との単方向同期を
 *   `LaunchedEffect(amount)` で行うパターンを揃えておく。
 */
@Composable
private fun AmountField(
    amount: Long,
    onAmountChange: (Long) -> Unit,
    enabled: Boolean,
) {
    var textFieldValue by remember {
        val initial = if (amount == 0L) "" else amount.toString()
        mutableStateOf(TextFieldValue(text = initial, selection = TextRange(initial.length)))
    }
    LaunchedEffect(amount) {
        val expected = if (amount == 0L) "" else amount.toString()
        if (textFieldValue.text != expected) {
            textFieldValue = textFieldValue.copy(
                text = expected,
                selection = TextRange(expected.length),
            )
        }
    }

    OutlinedTextField(
        value = textFieldValue,
        onValueChange = { newValue ->
            val digitsOnly = newValue.text.filter { it.isDigit() }
            val normalized = digitsOnly.trimStart('0')
            val parsed = normalized.toLongOrNull() ?: 0L
            textFieldValue = TextFieldValue(
                text = normalized,
                selection = TextRange(normalized.length),
            )
            onAmountChange(parsed)
        },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        textStyle = TextStyle(
            fontFamily = NotoSansJP,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = FujuBankColors.TextPrimary,
            textAlign = TextAlign.End,
        ),
        placeholder = {
            Text(
                text = "0",
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(
                    fontFamily = NotoSansJP,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = FujuBankColors.TextTertiary,
                    textAlign = TextAlign.End,
                ),
            )
        },
        suffix = {
            Text(
                text = CurrencyFormatter.UNIT,
                style = TextStyle(
                    fontFamily = NotoSansJP,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FujuBankColors.TextSecondary,
                ),
            )
        },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = FujuBankColors.Surface,
            unfocusedContainerColor = FujuBankColors.Surface,
            disabledContainerColor = FujuBankColors.Surface,
            focusedBorderColor = FujuBankColors.BrandPink,
            unfocusedBorderColor = FujuBankColors.Hairline,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )
}

@Composable
private fun BalancePreviewCard(balanceAfter: Long) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(FujuBankColors.LightPink)
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
 * 任意メモ入力欄 + `n/80` カウンタ。
 *
 * - 80 文字上限のクライアントガードは ViewModel 側 ([SendFlowViewModel.onMemoChange]) で
 *   切り捨てるため、TextField 自体は素の入力値を `onMemoChange` に流すだけでよい。
 * - `singleLine = false` + `maxLines = 3` でメモらしい複数行入力を許容する。OTP 同様の
 *   「IME Done で自動 submit」は発生しないので、CTA タップが必須。
 * - 残量が 10 文字以下になったらカウンタを警告色 (Error) に切り替え、上限近接を視認できるようにする。
 *
 * 日本語 IME の変換中状態 (composition) を維持するため [TextFieldValue] を Compose 側で保持し、
 * 外部 state (memo) との同期は単方向 (memo が変わったときだけ反映) で行う。String 版
 * `OutlinedTextField` を直接 hoisting すると IME composition が破棄され日本語入力が成立しない。
 */
@Composable
private fun MemoField(
    memo: String,
    onMemoChange: (String) -> Unit,
    enabled: Boolean,
) {
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = memo, selection = TextRange(memo.length)))
    }
    LaunchedEffect(memo) {
        if (textFieldValue.text != memo) {
            textFieldValue = textFieldValue.copy(
                text = memo,
                selection = TextRange(memo.length),
            )
        }
    }

    val length = memo.length
    val remaining = MEMO_MAX_LENGTH - length
    val counterColor = if (remaining <= MEMO_REMAINING_WARN_THRESHOLD) {
        FujuBankColors.Error
    } else {
        FujuBankColors.TextSecondary
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                textFieldValue = newValue
                onMemoChange(newValue.text)
            },
            enabled = enabled,
            singleLine = false,
            maxLines = 3,
            shape = RoundedCornerShape(16.dp),
            placeholder = {
                Text(
                    text = "メモ（任意・80文字まで）",
                    style = TextStyle(
                        fontFamily = NotoSansJP,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = FujuBankColors.TextTertiary,
                    ),
                )
            },
            textStyle = TextStyle(
                fontFamily = NotoSansJP,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = FujuBankColors.TextPrimary,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = FujuBankColors.Surface,
                unfocusedContainerColor = FujuBankColors.Surface,
                disabledContainerColor = FujuBankColors.Surface,
                focusedBorderColor = FujuBankColors.BrandPink,
                unfocusedBorderColor = FujuBankColors.Hairline,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = "$length/$MEMO_MAX_LENGTH",
            style = TextStyle(
                fontFamily = NotoSansJP,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = counterColor,
            ),
        )
    }
}

/** memo 上限。`SendFlowViewModel.MEMO_MAX_LENGTH` と同期させる。 */
private const val MEMO_MAX_LENGTH = 80

/** 残量がこの値以下になったらカウンタを警告色に切り替える。 */
private const val MEMO_REMAINING_WARN_THRESHOLD = 10
