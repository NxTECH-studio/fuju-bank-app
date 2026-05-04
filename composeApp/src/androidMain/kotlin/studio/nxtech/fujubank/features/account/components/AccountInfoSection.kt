package studio.nxtech.fujubank.features.account.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
 * 各行はラベル（小さなグレー）+ 値（黒）の縦積み。
 *
 * カード右上には編集鉛筆アイコン（[R.drawable.ic_edit_pencil]）を重ね、タップで
 * [onEditClick] を呼ぶ。実装は [AccountHubScreen] 側で `AccountInfoEditSheet` を開く。
 */
@Composable
fun AccountInfoSection(
    displayName: String,
    email: String,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
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
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            InfoRow(label = "表示名", value = displayName)
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 1.dp,
                color = FujuBankColors.Hairline,
            )
            InfoRow(label = "メールアドレス", value = email)
        }
        // 鉛筆アイコンはカード右上にオーバーレイ。タップ領域を 36dp 確保しつつ、
        // 視覚サイズは ProfileCard と揃えて 18dp。
        IconButton(
            onClick = onEditClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(36.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_edit_pencil),
                contentDescription = "アカウント情報を編集",
                modifier = Modifier.size(18.dp),
                tint = FujuBankColors.TextTertiary,
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
}
