package studio.nxtech.fujubank.features.account.notification

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.nxtech.fujubank.theme.FujuBankColors
import studio.nxtech.fujubank.theme.NotoSansJP

private const val LOG_TAG = "NotificationPermission"

/**
 * 「プッシュ通知」マスタートグルカード。
 *
 * 共通仕様 D に従い、OS のプッシュ通知許可をマスター、着金 / 転送をサブとする階層構造の最上段。
 * UI は既存 `NotificationCard` のサブトグル行と同じ `Switch` ベースに揃える:
 *
 * - `checked` は OS 許可状態を反映（`Granted` / `SystemSettingsOnly` → ON、それ以外 OFF）。
 * - `Switch` は受動コンポーネントとして扱い、`onCheckedChange` のパラメータ値は使わない。
 *   タップアクションは現在の OS 状態で分岐する:
 *   - `NotDetermined` → 権限要求ダイアログ（[onRequestPermission]）
 *   - `Denied` / `Granted` / `SystemSettingsOnly` → OS 設定アプリ起動（[onOpenSystemSettings]）
 * - 要求中（[requesting] が true）は `enabled = false` で二重タップを防ぐ。
 */
@Composable
internal fun NotificationPermissionCard(
    state: NotificationPermissionState,
    requesting: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isOn = state.isGranted()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(20.dp),
                clip = false,
            )
            .clip(RoundedCornerShape(20.dp))
            .background(FujuBankColors.Surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "プッシュ通知",
                style = TextStyle(
                    fontFamily = NotoSansJP,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FujuBankColors.TextPrimary,
                ),
            )
            Text(
                text = subDescriptionFor(state),
                style = TextStyle(
                    fontFamily = NotoSansJP,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = FujuBankColors.TextTertiary,
                ),
            )
        }
        Switch(
            checked = isOn,
            onCheckedChange = {
                // パラメータの値は無視し、OS 状態に応じて要求 / 設定起動を分岐する。
                // Switch は OS 状態取得後にしか checked を更新しない受動コンポーネント。
                when (state) {
                    NotificationPermissionState.NotDetermined -> onRequestPermission()
                    NotificationPermissionState.Denied,
                    NotificationPermissionState.Granted,
                    NotificationPermissionState.SystemSettingsOnly -> onOpenSystemSettings()
                }
            },
            enabled = !requesting,
            modifier = Modifier.semantics {
                contentDescription = "プッシュ通知"
                role = Role.Switch
            },
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

internal fun NotificationPermissionState.isGranted(): Boolean = when (this) {
    NotificationPermissionState.Granted,
    NotificationPermissionState.SystemSettingsOnly -> true
    NotificationPermissionState.NotDetermined,
    NotificationPermissionState.Denied -> false
}

private fun subDescriptionFor(state: NotificationPermissionState): String = when (state) {
    NotificationPermissionState.NotDetermined -> "プッシュ通知を受け取るには許可が必要です"
    NotificationPermissionState.Granted -> "許可済み"
    NotificationPermissionState.Denied -> "OS 設定から有効化できます"
    NotificationPermissionState.SystemSettingsOnly -> "OS 設定から変更できます"
}

/**
 * アプリの OS 通知設定画面を開く。失敗時はアプリ詳細設定にフォールバックする
 * （共通仕様 C, F）。両方失敗した場合はログのみ残して握り潰す。
 */
internal fun openAppNotificationSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val notificationIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        try {
            context.startActivity(notificationIntent)
            return
        } catch (e: ActivityNotFoundException) {
            Log.w(LOG_TAG, "ACTION_APP_NOTIFICATION_SETTINGS unavailable, falling back", e)
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Failed to open notification settings", e)
        }
    }

    val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    try {
        context.startActivity(fallback)
    } catch (e: Exception) {
        Log.w(LOG_TAG, "Failed to open application details settings", e)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F7F9)
@Composable
private fun NotificationPermissionCardPreview() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        NotificationPermissionCard(
            state = NotificationPermissionState.NotDetermined,
            requesting = false,
            onRequestPermission = {},
            onOpenSystemSettings = { openAppNotificationSettings(context) },
        )
        NotificationPermissionCard(
            state = NotificationPermissionState.Granted,
            requesting = false,
            onRequestPermission = {},
            onOpenSystemSettings = { openAppNotificationSettings(context) },
        )
        NotificationPermissionCard(
            state = NotificationPermissionState.Denied,
            requesting = false,
            onRequestPermission = {},
            onOpenSystemSettings = { openAppNotificationSettings(context) },
        )
        NotificationPermissionCard(
            state = NotificationPermissionState.SystemSettingsOnly,
            requesting = false,
            onRequestPermission = {},
            onOpenSystemSettings = { openAppNotificationSettings(context) },
        )
    }
}
