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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import studio.nxtech.fujubank.R
import studio.nxtech.fujubank.theme.FujupayColors

/**
 * 送金画面 — A5 MVP。
 *
 * - ヘッダー: 戻る `<` / タイトル「送る」/ 右側は空 (取引履歴と幅を揃える)
 * - 本文: 受取人 ID 入力 / 金額入力 / 送るボタン
 * - 確認ダイアログで最終 submit、submit 中はボタン無効化
 * - 成功 / エラーは Snackbar 相当（onShowToast 経由）で表示
 *
 * QR 送金 (`qr-payment-foundation-mpm`) は別タスク。
 */
@Composable
fun SendScreen(
    viewModel: SendViewModel,
    onBack: () -> Unit,
    onShowToast: (String) -> Unit,
    onTransferSucceeded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 成功イベントは UI 側で 1 度だけ消費して、ホームへ戻すと同時に親へ通知する。
    // LaunchedEffect の key を successEvent にすることで、新しいイベント毎に 1 回だけ走る。
    LaunchedEffect(state.successEvent) {
        val event = state.successEvent ?: return@LaunchedEffect
        onShowToast("送金しました（残高 ${event.newBalance} fuju）")
        viewModel.consumeSuccess()
        onTransferSucceeded()
    }

    // エラーは Toast で表示しつつ、表示後にクリアして次のエラーで再発火できるようにする。
    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        onShowToast(message)
        viewModel.consumeError()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FujupayColors.Background),
    ) {
        Header(onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            RecipientField(
                value = state.recipientExternalId,
                enabled = state.phase == SendUiState.Phase.Editing,
                onValueChange = viewModel::onRecipientChanged,
            )
            AmountField(
                value = state.amountFuju,
                enabled = state.phase == SendUiState.Phase.Editing,
                onValueChange = viewModel::onAmountChanged,
            )
            Spacer(Modifier.height(8.dp))
            SubmitButton(
                enabled = state.canSubmit,
                submitting = state.phase == SendUiState.Phase.Submitting,
                onClick = viewModel::requestConfirm,
            )
        }
    }

    if (state.phase == SendUiState.Phase.Confirming || state.phase == SendUiState.Phase.Submitting) {
        ConfirmDialog(
            recipient = state.recipientExternalId.trim(),
            amount = state.parsedAmount ?: 0L,
            submitting = state.phase == SendUiState.Phase.Submitting,
            onConfirm = viewModel::submit,
            onDismiss = viewModel::cancelConfirm,
        )
    }
}

@Composable
private fun Header(onBack: () -> Unit) {
    // 取引履歴と同じヘッダー構造に揃える（タイトル中央寄せ、左に 48dp 戻るボタン）。
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "送る",
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = FujupayColors.TextPrimary,
            ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_chevron_left),
                contentDescription = "戻る",
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun RecipientField(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "送り先（ユーザー ID）",
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = FujupayColors.TextSecondary,
            ),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            enabled = enabled,
            placeholder = { Text("usr_xxxx", color = FujupayColors.TextTertiary) },
            colors = fujupayTextFieldColors(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AmountField(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "金額（fuju）",
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = FujupayColors.TextSecondary,
            ),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            placeholder = { Text("1000", color = FujupayColors.TextTertiary) },
            // 送金単位（fuju）を suffix として表示する。
            trailingIcon = {
                Text(
                    text = "fuju",
                    style = TextStyle(
                        fontSize = 14.sp,
                        color = FujupayColors.TextSecondary,
                    ),
                    modifier = Modifier.padding(end = 12.dp),
                )
            },
            colors = fujupayTextFieldColors(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SubmitButton(
    enabled: Boolean,
    submitting: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !submitting,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = FujupayColors.BrandPink,
            contentColor = FujupayColors.Surface,
            disabledContainerColor = FujupayColors.BrandPink.copy(alpha = 0.4f),
            disabledContentColor = FujupayColors.Surface,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (submitting) {
                CircularProgressIndicator(
                    color = FujupayColors.Surface,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(8.dp))
            }
            Text(
                text = if (submitting) "送金中..." else "送る",
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

@Composable
private fun ConfirmDialog(
    recipient: String,
    amount: Long,
    submitting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(text = "送金内容の確認") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "送り先",
                    style = TextStyle(fontSize = 12.sp, color = FujupayColors.TextSecondary),
                )
                Text(
                    text = recipient,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = FujupayColors.TextPrimary,
                    ),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "金額",
                    style = TextStyle(fontSize = 12.sp, color = FujupayColors.TextSecondary),
                )
                Text(
                    text = "$amount fuju",
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = FujupayColors.TextPrimary,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !submitting,
            ) {
                Text(
                    text = if (submitting) "送金中..." else "送金する",
                    color = FujupayColors.BrandPink,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) {
                Text(text = "キャンセル", color = FujupayColors.TextSecondary)
            }
        },
    )
}

@Composable
private fun fujupayTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = FujupayColors.BrandPink,
    unfocusedBorderColor = FujupayColors.BottomBarBorder,
    focusedTextColor = FujupayColors.TextPrimary,
    unfocusedTextColor = FujupayColors.TextPrimary,
    disabledTextColor = FujupayColors.TextSecondary,
    disabledBorderColor = FujupayColors.BottomBarBorder,
    cursorColor = FujupayColors.BrandPink,
    focusedContainerColor = FujupayColors.Surface,
    unfocusedContainerColor = FujupayColors.Surface,
    disabledContainerColor = FujupayColors.Surface,
)
