package studio.nxtech.fujubank.features.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import studio.nxtech.fujubank.features.account.components.AccountInfoEditSheet
import studio.nxtech.fujubank.features.account.components.AccountInfoSection
import studio.nxtech.fujubank.features.account.components.ProfileCard
import studio.nxtech.fujubank.features.account.components.SettingsCard
import studio.nxtech.fujubank.features.account.components.SettingsRowSpec
import studio.nxtech.fujubank.theme.FujuBankColors
import studio.nxtech.fujubank.theme.NotoSansJP

/**
 * アカウントハブ画面 — Figma `697:8394` 準拠（Android 先行）。
 *
 * 構成:
 * - プロフィールカード（円形アバター / ユーザー名 + 編集鉛筆 / ID）
 * - 「アカウント情報」セクション（表示名 / メールアドレス、各行に編集鉛筆）
 * - 「設定」セクション（通知 / プライバシー設定）
 *
 * 各行の鉛筆タップで [AccountInfoEditSheet] を開き、対応するフィールド単独で
 * 編集する（client-bank-10）。
 *
 * ボトムナビは [studio.nxtech.fujubank.features.shell.RootScaffold] が描画する。
 */
@Composable
fun AccountHubScreen(
    viewModel: AccountHubViewModel,
    onNavigateNotifications: () -> Unit,
    onNavigatePrivacy: () -> Unit,
    onNavigatePasswordChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val isLoggingOut by viewModel.isLoggingOut.collectAsStateWithLifecycle()

    // 画面初回表示時にプロフィールをロード（Provider 側で冪等化されているので
    // タブ切替で再合成されても API は 1 度しか叩かない）。
    LaunchedEffect(Unit) {
        viewModel.ensureProfileLoaded()
    }
    // 編集中フィールド。MVP では編集 UI 無効化のため常に null だが、将来復活時に
    // 再利用できるよう state とシート分岐自体は保持する。
    var editingField by rememberSaveable { mutableStateOf<AccountInfoField?>(null) }
    // ログアウト確認ダイアログの表示制御（client-bank-16）。プロセス再生成でも復元する。
    var showLogoutConfirm by rememberSaveable { mutableStateOf(false) }

    // MVP は受け取り専用のため AuthCore 側に email/displayName 更新 API が揃うまで
    // 編集 UI を無効化する。鉛筆アイコン非表示 + onClick no-op で「タップしても何も
    // 起きない」状態にし、AccountInfoEditSheet 側のコードは復活前提で残す。
    val editingEnabled = false

    // プロフィール取得失敗・取得前は空文字で来るので、UI 側で「-」プレースホルダに置換する。
    val displayNameOrPlaceholder = profile.displayName.ifBlank { PROFILE_PLACEHOLDER }
    val emailOrPlaceholder = profile.email.ifBlank { PROFILE_PLACEHOLDER }
    val accountIdOrPlaceholder = profile.accountId.ifBlank { PROFILE_PLACEHOLDER }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FujuBankColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProfileCard(
            displayName = displayNameOrPlaceholder,
            accountId = accountIdOrPlaceholder,
            editable = editingEnabled,
        )

        SectionLabel(text = "アカウント情報")
        AccountInfoSection(
            displayName = displayNameOrPlaceholder,
            email = emailOrPlaceholder,
            // editable=false の間は呼ばれないが、将来復活させた際の経路として残す。
            onEditDisplayName = { if (editingEnabled) editingField = AccountInfoField.DisplayName },
            onEditEmail = { if (editingEnabled) editingField = AccountInfoField.Email },
            editable = editingEnabled,
        )

        SectionLabel(text = "設定")
        SettingsCard(
            rows = listOf(
                SettingsRowSpec(label = "通知", onClick = onNavigateNotifications),
                SettingsRowSpec(label = "プライバシー設定", onClick = onNavigatePrivacy),
                SettingsRowSpec(label = "パスワード変更", onClick = onNavigatePasswordChange),
                // client-bank-16: 既存の「設定」末尾にログアウト行を追加する。
                // 行は通常スタイル（黒テキスト）で、destructive 表示は確認ダイアログの
                // 「ログアウト」ボタンに閉じる。logout 中は二度押しを防ぐためダイアログを開かない。
                SettingsRowSpec(
                    label = "ログアウト",
                    onClick = { if (!isLoggingOut) showLogoutConfirm = true },
                ),
            ),
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            // 画面外タップ / Back での dismiss は無効化する（明示的に
            // 「ログアウト」「キャンセル」のいずれかを選ばせる UX）。
            onDismissRequest = {},
            title = { Text("ログアウトしますか？") },
            text = { Text("再度利用するには再ログインが必要になります。") },
            confirmButton = {
                TextButton(
                    enabled = !isLoggingOut,
                    onClick = {
                        showLogoutConfirm = false
                        viewModel.logout()
                    },
                ) {
                    // destructive: 既存 FujuBankColors.Error (= 0xFFD32F2F) を流用。
                    Text("ログアウト", color = FujuBankColors.Error)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isLoggingOut,
                    onClick = { showLogoutConfirm = false },
                ) {
                    Text("キャンセル")
                }
            },
        )
    }

    // editingEnabled=false の間 editingField は常に null（鉛筆アイコン非表示のため
    // セットされない）。AccountInfoEditSheet 経路自体は将来の復活を見越して残す。
    when (editingField) {
        AccountInfoField.DisplayName -> AccountInfoEditSheet(
            title = "表示名を編集",
            label = "表示名",
            initialValue = profile.displayName,
            keyboardType = KeyboardType.Text,
            validate = { it.trim().isNotBlank() },
            onSave = { newName ->
                viewModel.updateDisplayName(newName.trim())
                editingField = null
            },
            onDismiss = { editingField = null },
        )
        AccountInfoField.Email -> AccountInfoEditSheet(
            title = "メールアドレスを編集",
            label = "メールアドレス",
            initialValue = profile.email,
            keyboardType = KeyboardType.Email,
            validate = { it.trim().contains("@") },
            onSave = { newEmail ->
                viewModel.updateEmail(newEmail.trim())
                editingField = null
            },
            onDismiss = { editingField = null },
        )
        null -> Unit
    }
}

/** プロフィール未取得 / 取得失敗時のフィールド表示プレースホルダ。 */
private const val PROFILE_PLACEHOLDER = "-"

/** Figma `697:8394` の「アカウント情報」「設定」見出し（12sp Bold）。 */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .padding(start = 4.dp)
            .semantics { heading() },
        style = TextStyle(
            fontFamily = NotoSansJP,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = FujuBankColors.TextPrimary,
        ),
    )
}
