package studio.nxtech.fujubank.features.account.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import studio.nxtech.fujubank.theme.FujuBankColors
import studio.nxtech.fujubank.theme.NotoSansJP

/**
 * アカウント情報編集ボトムシート（Figma `697:8394` の編集 UI）。
 *
 * 公開ID / メールアドレスのいずれか単一フィールドを編集して保存するシート。
 * 行ごとの鉛筆アイコンタップで開き、対応する 1 フィールドのみを編集する。
 *
 * - `skipPartiallyExpanded = true` で半開きを禁止し、全展開のみ。
 * - `imePadding` を root に付与してキーボードとの重なりを回避。
 * - 入力値は `rememberSaveable` で保持し、回転にも耐える。
 *
 * 閉じる経路:
 * - シート外タップ / バックジェスチャ → [onDismiss]
 * - 保存ボタン → `sheetState.hide()` の後に [onSave]（呼び出し側で state を倒す）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountInfoEditSheet(
    title: String,
    label: String,
    initialValue: String,
    keyboardType: KeyboardType,
    validate: (String) -> Boolean,
    onSave: (value: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var value by rememberSaveable { mutableStateOf(initialValue) }
    val isValid = validate(value)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = FujuBankColors.Surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = title,
                style = TextStyle(
                    fontFamily = NotoSansJP,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = FujuBankColors.TextPrimary,
                ),
            )
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        text = label,
                        style = TextStyle(fontFamily = NotoSansJP),
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                shape = RoundedCornerShape(12.dp),
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
            Button(
                onClick = {
                    val newValue = value
                    scope.launch {
                        sheetState.hide()
                    }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            onSave(newValue)
                        }
                    }
                },
                enabled = isValid,
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
