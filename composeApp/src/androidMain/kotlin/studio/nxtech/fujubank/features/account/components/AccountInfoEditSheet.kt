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
 * 表示名 / メールアドレスを編集して保存ボタンで [onSave] を呼び出す。
 * バリデーションは MVP として「表示名 1 文字以上」「メールに `@` を含む」の緩めの条件のみ。
 *
 * - `skipPartiallyExpanded = true` で半開きを禁止し、全展開のみ。
 * - `imePadding` を root に付与してキーボードとの重なりを回避。
 * - 入力値は `rememberSaveable` で保持し、回転にも耐える。
 *
 * 閉じる経路:
 * - シート外タップ / バックジェスチャ → [onDismiss]
 * - 保存ボタン → `sheetState.hide()` の後に [onSave]（呼び出し側で `showSheet = false` に倒す）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountInfoEditSheet(
    initialDisplayName: String,
    initialEmail: String,
    onSave: (displayName: String, email: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var displayName by rememberSaveable { mutableStateOf(initialDisplayName) }
    var email by rememberSaveable { mutableStateOf(initialEmail) }
    val isValid = displayName.isNotBlank() && email.contains("@")

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
                text = "アカウント情報を編集",
                style = TextStyle(
                    fontFamily = NotoSansJP,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = FujuBankColors.TextPrimary,
                ),
            )
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        text = "表示名",
                        style = TextStyle(fontFamily = NotoSansJP),
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
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
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        text = "メールアドレス",
                        style = TextStyle(fontFamily = NotoSansJP),
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
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
                    val newName = displayName
                    val newEmail = email
                    // シートを綺麗に畳んでから親へ通知する。`hide()` 完了後に親で showSheet=false に
                    // 倒すと再合成で `ModalBottomSheet` 自体が外れる。
                    scope.launch {
                        sheetState.hide()
                    }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            onSave(newName, newEmail)
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
