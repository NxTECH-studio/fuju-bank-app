package studio.nxtech.fujubank.features.account

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import studio.nxtech.fujubank.R
import studio.nxtech.fujubank.features.home.components.NotificationBellButton
import studio.nxtech.fujubank.theme.FujuBankColors

/**
 * 通知設定画面 — Figma `718:7332` 準拠（Android 先行）。
 *
 * - ヘッダー: 戻る `<` (左 48dp) / タイトル「通知設定」(中央 17sp Bold) / 通知ベル (右 48dp)
 * - 本文: 白角丸カード内に「着金通知 / ふじゅ〜が届いたとき」「転送通知 / 送金が完了したとき」
 *   の 2 行 + 各行の右にトグル
 *
 * トグルの永続化は [NotificationSettingsViewModel] 経由で
 * [studio.nxtech.fujubank.account.NotificationSettingsPreferences] が担う。
 */
@Composable
fun NotificationSettingsScreen(
    viewModel: NotificationSettingsViewModel,
    onBack: () -> Unit,
    onNotificationClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val deposit by viewModel.depositEnabled.collectAsStateWithLifecycle()
    val transfer by viewModel.transferEnabled.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FujuBankColors.Background),
    ) {
        Header(
            onBack = onBack,
            onNotificationClick = onNotificationClick,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NotificationCard(
                depositEnabled = deposit,
                onDepositToggle = viewModel::setDepositEnabled,
                transferEnabled = transfer,
                onTransferToggle = viewModel::setTransferEnabled,
            )
        }
    }
}

@Composable
private fun Header(
    onBack: () -> Unit,
    onNotificationClick: () -> Unit,
) {
    // Figma 718:7332 の contents wrapper の余白に合わせて水平・垂直 10dp。
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "通知設定",
            style = TextStyle(
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
        NotificationBellButton(
            onClick = onNotificationClick,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun NotificationCard(
    depositEnabled: Boolean,
    onDepositToggle: (Boolean) -> Unit,
    transferEnabled: Boolean,
    onTransferToggle: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(20.dp),
                clip = false,
            )
            .clip(RoundedCornerShape(20.dp))
            .background(FujuBankColors.Surface),
    ) {
        ToggleRow(
            title = "着金通知",
            description = "ふじゅ〜が届いたとき",
            checked = depositEnabled,
            onCheckedChange = onDepositToggle,
            toggleContentDescription = "着金通知",
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = 1.dp,
            color = FujuBankColors.Hairline,
        )
        ToggleRow(
            title = "転送通知",
            description = "送金が完了したとき",
            checked = transferEnabled,
            onCheckedChange = onTransferToggle,
            toggleContentDescription = "転送通知",
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    toggleContentDescription: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FujuBankColors.TextPrimary,
                ),
            )
            Text(
                text = description,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = FujuBankColors.TextTertiary,
                ),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = toggleContentDescription },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = FujuBankColors.BrandPink,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = FujuBankColors.TextTertiary,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}
