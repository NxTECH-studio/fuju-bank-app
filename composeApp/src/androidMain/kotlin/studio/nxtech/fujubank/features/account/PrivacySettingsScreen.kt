package studio.nxtech.fujubank.features.account

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
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
import studio.nxtech.fujubank.account.PrivacyContent
import studio.nxtech.fujubank.features.account.components.SettingsCard
import studio.nxtech.fujubank.features.account.components.SettingsRowSpec
import studio.nxtech.fujubank.theme.FujuBankColors
import studio.nxtech.fujubank.theme.NotoSansJP

/**
 * プライバシー設定画面 — Figma `798:12559` 準拠（Android 先行）。
 *
 * - ヘッダー: 戻る `<` + 中央タイトル「プライバシー設定」(17sp Bold)
 * - セクション 1「トラッキング」: 単一カード内に「アプリのトラッキングを許可」+ サブテキスト + 右端トグル
 * - セクション 2「法的情報」: 「プライバシーポリシー」「利用規約」の 2 行リスト、タップで外部ブラウザ起動
 *
 * トグルの永続化は [PrivacySettingsViewModel] 経由で
 * [studio.nxtech.fujubank.account.PrivacyPreferences] が担う。
 * 法的情報の URL は [PrivacyContent] を参照する。
 */
@Composable
fun PrivacySettingsScreen(
    viewModel: PrivacySettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val optIn by viewModel.analyticsOptInEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FujuBankColors.Background),
    ) {
        Header(onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionHeading(text = "トラッキング")
            TrackingOptInCard(
                checked = optIn,
                onCheckedChange = viewModel::setAnalyticsOptInEnabled,
            )
            SectionHeading(text = "法的情報")
            SettingsCard(
                rows = listOf(
                    SettingsRowSpec(
                        label = "プライバシーポリシー",
                        onClick = { openUrl(context, PrivacyContent.PRIVACY_POLICY_URL) },
                    ),
                    SettingsRowSpec(
                        label = "利用規約",
                        onClick = { openUrl(context, PrivacyContent.TERMS_OF_SERVICE_URL) },
                    ),
                ),
            )
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
            text = "プライバシー設定",
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
private fun SectionHeading(text: String) {
    // Figma 798:12559: セクション見出しは 12sp Bold、白カード外に左寄せ、px:8 py:8。
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        style = TextStyle(
            fontFamily = NotoSansJP,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = FujuBankColors.TextSecondary,
        ),
    )
}

@Composable
private fun TrackingOptInCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(20.dp),
                clip = false,
            )
            .clip(RoundedCornerShape(20.dp))
            .background(FujuBankColors.Surface)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "アプリのトラッキングを許可",
                style = TextStyle(
                    fontFamily = NotoSansJP,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FujuBankColors.TextPrimary,
                ),
            )
            Text(
                text = "利用状況の分析と改善に使用されます",
                style = TextStyle(
                    fontFamily = NotoSansJP,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = FujuBankColors.TextTertiary,
                ),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = "アプリのトラッキングを許可" },
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

/**
 * 外部ブラウザで URL を開く。ブラウザ未インストール等の特殊環境では
 * `ActivityNotFoundException` が発生し得るため、`runCatching` でクラッシュを防ぐ。
 * Figma 確定デザインでは標準 Android 端末前提のため、失敗時の UI 通知は行わない。
 */
private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    runCatching { context.startActivity(intent) }
}
