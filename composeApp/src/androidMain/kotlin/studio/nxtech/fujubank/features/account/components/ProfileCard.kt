package studio.nxtech.fujubank.features.account.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.nxtech.fujubank.R
import studio.nxtech.fujubank.theme.FujuBankColors

/**
 * アカウントハブ画面（Figma `697:8394`）のプロフィールカード。
 *
 * - 白背景・角丸 20dp・薄影
 * - 上段: 64dp の円形アバター（左寄せ）
 * - 中段: 「表示名」+ 編集鉛筆アイコン
 * - 下段: 「ID: xxxxxxxxxxxxx」のグレー小文字
 */
@Composable
fun ProfileCard(
    displayName: String,
    accountId: String,
    modifier: Modifier = Modifier,
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
            .background(FujuBankColors.Surface)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 円形アバター
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(FujuBankColors.Surface)
                .semantics { contentDescription = "プロフィールアバター" },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_avatar_tomato),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = displayName,
                style = TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = FujuBankColors.TextPrimary,
                ),
            )
            Image(
                painter = painterResource(R.drawable.ic_edit_pencil),
                contentDescription = "表示名を編集",
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.height(0.dp))
        }
        Text(
            text = "ID: $accountId",
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = FujuBankColors.TextTertiary,
            ),
        )
    }
}
