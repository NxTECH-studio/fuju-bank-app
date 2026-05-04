package studio.nxtech.fujubank.features.account

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import studio.nxtech.fujubank.R
import studio.nxtech.fujubank.theme.FujuBankColors
import studio.nxtech.fujubank.theme.NotoSansJP

/**
 * パスワード変更画面 — Figma `799:13327` をベースに、ユーザー指示で 3 入力欄＋保存ボタン構成。
 *
 * - ヘッダー: 戻る `<` + 中央タイトル「パスワード変更」(17sp Bold)
 * - 入力欄: 「現在のパスワード」「新しいパスワード」「新しいパスワード（確認）」
 *   いずれも `PasswordVisualTransformation` でマスク表示
 * - 保存ボタン: ハブ画面の編集シートと同じスタイル（48dp / 16dp 角丸 / BrandPink）
 *
 * バックエンド連携は未提供のため、`viewModel.submit()` は疑似遅延後に成功扱いで戻る。
 * 成功時は [onSuccess] でトースト表示し、[onBack] でハブへ戻る（呼び出しは [RootScaffold] 側）。
 */
@Composable
fun PasswordChangeScreen(
    viewModel: PasswordChangeViewModel,
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 送信成功フラグの立ち上がりで toast → ハブへ戻る。
    // ViewModel は Activity スコープに残るので、消費後にフラグを reset して再訪時の二重発火を防ぐ。
    LaunchedEffect(uiState.isSubmitted) {
        if (uiState.isSubmitted) {
            onSuccess()
            onBack()
            viewModel.consumeSubmitted()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FujuBankColors.Background)
            .imePadding(),
    ) {
        Header(onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PasswordField(
                value = uiState.current,
                onValueChange = viewModel::onCurrentChange,
                label = "現在のパスワード",
                imeAction = ImeAction.Next,
            )
            PasswordField(
                value = uiState.newPassword,
                onValueChange = viewModel::onNewChange,
                label = "新しいパスワード",
                imeAction = ImeAction.Next,
            )
            PasswordField(
                value = uiState.confirm,
                onValueChange = viewModel::onConfirmChange,
                label = "新しいパスワード（確認）",
                imeAction = ImeAction.Done,
                onImeDone = {
                    if (uiState.canSubmit) viewModel.submit()
                },
            )

            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = TextStyle(
                        fontFamily = NotoSansJP,
                        fontSize = 12.sp,
                        color = FujuBankColors.Error,
                    ),
                )
            }

            Button(
                onClick = viewModel::submit,
                enabled = uiState.canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FujuBankColors.BrandPink,
                    contentColor = Color.White,
                    disabledContainerColor = FujuBankColors.Hairline,
                    disabledContentColor = FujuBankColors.TextTertiary,
                ),
            ) {
                Text(
                    text = "保存",
                    style = TextStyle(
                        fontFamily = NotoSansJP,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
        }
    }
}

@Composable
private fun Header(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "パスワード変更",
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
                .clickable(role = Role.Button, onClick = onBack)
                .semantics { contentDescription = "戻る" },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_chevron_left),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    imeAction: ImeAction,
    onImeDone: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = {
            Text(
                text = label,
                style = TextStyle(fontFamily = NotoSansJP),
            )
        },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onDone = { onImeDone?.invoke() },
        ),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = FujuBankColors.BrandPink,
            focusedLabelColor = FujuBankColors.BrandPink,
            cursorColor = FujuBankColors.BrandPink,
        ),
        textStyle = TextStyle(
            fontFamily = NotoSansJP,
            fontSize = 14.sp,
            color = FujuBankColors.TextPrimary,
        ),
    )
}
