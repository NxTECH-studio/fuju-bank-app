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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
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
 * 「プッシュ通知」マスターカード。
 *
 * 共通仕様 D に従い、OS のプッシュ通知許可をマスター、着金 / 転送をサブとする
 * 階層構造の最上段。マスターはステータステキスト + 単一のアクションボタンで
 * 表現する（Switch / Toggle は使わない。Toggle だと「ON タップで OFF にならず
 * 設定へ飛ぶ」など語彙と挙動の不整合が出るため）。
 *
 * 状態に応じてボタンラベルと挙動を切り替える（共通仕様 B / C）:
 *
 * - [NotificationPermissionState.NotDetermined] → 「許可する」、タップで OS 権限ダイアログ
 * - その他 → 「OS 設定を開く」、タップで OS 設定アプリ
 *
 * 要求中（[requesting] が true）はボタンを `enabled = false` にして二重タップを
 * 防ぐ（共通仕様 F）。
 */
@Composable
internal fun NotificationPermissionCard(
    state: NotificationPermissionState,
    requesting: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSystemSettings: () -> Unit,
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
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
        val label = buttonLabelFor(state)
        Button(
            onClick = {
                when (state) {
                    NotificationPermissionState.NotDetermined -> onRequestPermission()
                    NotificationPermissionState.Denied,
                    NotificationPermissionState.Granted,
                    NotificationPermissionState.SystemSettingsOnly -> onOpenSystemSettings()
                }
            },
            enabled = !requesting,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .semantics { contentDescription = label },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = FujuBankColors.BrandPink,
                contentColor = Color.White,
                disabledContainerColor = FujuBankColors.Hairline,
                disabledContentColor = FujuBankColors.TextTertiary,
            ),
        ) {
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = NotoSansJP,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
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
    NotificationPermissionState.Denied -> "現在 OS で通知が無効になっています"
    NotificationPermissionState.SystemSettingsOnly -> "OS 設定から変更できます"
}

private fun buttonLabelFor(state: NotificationPermissionState): String = when (state) {
    NotificationPermissionState.NotDetermined -> "許可する"
    NotificationPermissionState.Denied,
    NotificationPermissionState.Granted,
    NotificationPermissionState.SystemSettingsOnly -> "OS 設定を開く"
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
