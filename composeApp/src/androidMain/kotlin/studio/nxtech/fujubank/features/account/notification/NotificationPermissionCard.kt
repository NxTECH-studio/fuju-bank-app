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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.nxtech.fujubank.theme.FujuBankColors
import studio.nxtech.fujubank.theme.NotoSansJP

private const val LOG_TAG = "NotificationPermission"

/**
 * 「OS 通知許可」セクションのカード。
 *
 * 既存 `NotificationCard`（白背景 / `RoundedCornerShape(20.dp)` / `shadow(4.dp, clip=false)`）
 * と同じスタイルで実装する。状態に応じて右側のボタンを切り替える:
 *
 * - [NotificationPermissionState.NotDetermined] → 塗りつぶし「許可する」（OS ダイアログ起動）
 * - [NotificationPermissionState.Granted]      → サブテキスト「許可済み」+ アウトライン「OS 設定で開く」
 * - [NotificationPermissionState.Denied]       → アウトライン「OS 設定で開く」
 * - [NotificationPermissionState.SystemSettingsOnly] → アウトライン「OS 設定で開く」
 *
 * 要求中（[requesting] が true）はボタンを `enabled = false` にして二重タップを防ぐ。
 */
@Composable
internal fun NotificationPermissionCard(
    state: NotificationPermissionState,
    requesting: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                text = "OS 通知許可",
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
        when (state) {
            NotificationPermissionState.NotDetermined -> AllowButton(
                enabled = !requesting,
                onClick = onRequestPermission,
            )
            NotificationPermissionState.Granted -> OpenSettingsOutlinedButton(
                label = "OS 設定で開く",
                enabled = !requesting,
                onClick = onOpenSystemSettings,
            )
            NotificationPermissionState.Denied -> OpenSettingsOutlinedButton(
                label = "OS 設定で開く",
                enabled = !requesting,
                onClick = onOpenSystemSettings,
            )
            NotificationPermissionState.SystemSettingsOnly -> OpenSettingsOutlinedButton(
                label = "OS 設定で開く",
                enabled = !requesting,
                onClick = onOpenSystemSettings,
            )
        }
    }
}

@Composable
private fun AllowButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = FujuBankColors.BrandPink,
            contentColor = Color.White,
            disabledContainerColor = FujuBankColors.BrandPink.copy(alpha = 0.4f),
            disabledContentColor = Color.White.copy(alpha = 0.7f),
        ),
    ) {
        Text(
            text = "許可する",
            style = TextStyle(
                fontFamily = NotoSansJP,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

@Composable
private fun OpenSettingsOutlinedButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = FujuBankColors.BrandPink,
            disabledContentColor = FujuBankColors.BrandPink.copy(alpha = 0.4f),
        ),
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = NotoSansJP,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

private fun subDescriptionFor(state: NotificationPermissionState): String = when (state) {
    NotificationPermissionState.NotDetermined -> "通知を受け取るには許可が必要です"
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

@Composable
internal fun rememberRequestingState() = remember { mutableStateOf(false) }
