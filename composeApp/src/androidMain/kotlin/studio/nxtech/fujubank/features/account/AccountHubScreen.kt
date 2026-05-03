package studio.nxtech.fujubank.features.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import studio.nxtech.fujubank.features.account.components.AccountInfoSection
import studio.nxtech.fujubank.features.account.components.ProfileCard
import studio.nxtech.fujubank.features.account.components.SettingsCard
import studio.nxtech.fujubank.features.account.components.SettingsRowSpec
import studio.nxtech.fujubank.theme.FujuBankColors

/**
 * アカウントハブ画面 — Figma `697:8394` 準拠（Android 先行）。
 *
 * 構成:
 * - プロフィールカード（円形アバター / ユーザー名 + 編集鉛筆 / ID）
 * - 「アカウント情報」セクション（表示名 / メールアドレス）
 * - 「設定」セクション（通知 / プライバシー設定 / アカウント情報）
 *
 * 画面遷移は呼び出し側（`RootScaffold`）で `onNavigateNotifications` 等のコールバックを
 * 受け取り、手動スタックで `NotificationSettingsScreen` / `ComingSoonScreen` に切り替える。
 *
 * ボトムナビは [studio.nxtech.fujubank.features.shell.RootScaffold] が描画する。
 */
@Composable
fun AccountHubScreen(
    viewModel: AccountHubViewModel,
    onNavigateNotifications: () -> Unit,
    onNavigatePrivacy: () -> Unit,
    onNavigateAccountEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FujuBankColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProfileCard(
            displayName = profile.displayName,
            accountId = profile.accountId,
        )

        SectionLabel(text = "アカウント情報")
        AccountInfoSection(
            displayName = profile.displayName,
            email = profile.email,
        )

        SectionLabel(text = "設定")
        SettingsCard(
            rows = listOf(
                SettingsRowSpec(label = "通知", onClick = onNavigateNotifications),
                SettingsRowSpec(label = "プライバシー設定", onClick = onNavigatePrivacy),
                SettingsRowSpec(label = "アカウント情報", onClick = onNavigateAccountEdit),
            ),
        )
    }
}

/** Figma `697:8394` の「アカウント情報」「設定」見出し（12sp Bold）。 */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .padding(start = 4.dp)
            .semantics { heading() },
        style = TextStyle(
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = FujuBankColors.TextPrimary,
        ),
    )
}
