package studio.nxtech.fujubank.features.account.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.nxtech.fujubank.R
import studio.nxtech.fujubank.theme.FujuBankColors
import studio.nxtech.fujubank.theme.NotoSansJP

/**
 * アカウントハブ画面（Figma `697:8394`）の「アカウント情報」セクション。
 *
 * 白角丸カード内に「表示名」と「メールアドレス」の 2 行を配置する。
 * 各行はラベル（小さなグレー）+ 値（黒）の縦積みで、行右端に編集鉛筆アイコン
 * （[R.drawable.ic_edit_pencil]）を配置する。タップで該当フィールド単独の
 * 編集シートを開く。
 */
@Composable
fun AccountInfoSection(
    displayName: String,
    email: String,
    onEditDisplayName: () -> Unit,
    onEditEmail: () -> Unit,
    modifier: Modifier = Modifier,
    editable: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(20.dp),
                clip = false,
            )
            .clip(RoundedCornerShape(20.dp))
            .background(FujuBankColors.Surface),
    ) {
        InfoRow(
            label = "表示名",
            value = displayName,
            onEditClick = onEditDisplayName,
            editContentDescription = "表示名を編集",
            editable = editable,
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = 1.dp,
            color = FujuBankColors.Hairline,
        )
        InfoRow(
            label = "メールアドレス",
            value = email,
            onEditClick = onEditEmail,
            editContentDescription = "メールアドレスを編集",
            editable = editable,
        )
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    onEditClick: () -> Unit,
    editContentDescription: String,
    editable: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = NotoSansJP,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = FujuBankColors.TextTertiary,
                ),
            )
            Text(
                text = value,
                style = TextStyle(
                    fontFamily = NotoSansJP,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = FujuBankColors.TextPrimary,
                ),
            )
        }
        // MVP では AuthCore に email/displayName 更新 API が無いため編集 UI を無効化する
        // （`editable=false` で鉛筆アイコンごと非表示）。コードは将来 API 提供時の復活前提で残す。
        if (editable) {
            IconButton(
                onClick = onEditClick,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_edit_pencil),
                    contentDescription = editContentDescription,
                    modifier = Modifier.size(18.dp),
                    tint = FujuBankColors.TextTertiary,
                )
            }
        }
    }
}
