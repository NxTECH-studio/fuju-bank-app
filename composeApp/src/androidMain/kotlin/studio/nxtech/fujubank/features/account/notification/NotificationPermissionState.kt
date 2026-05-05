package studio.nxtech.fujubank.features.account.notification

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * OS 通知許可の状態を表す sealed interface。
 *
 * client-bank-14 / client-bank-15 の共通仕様 A に従い、Android / iOS で
 * 4 状態（[NotDetermined] / [Granted] / [Denied] / [SystemSettingsOnly]）を
 * 区別して扱う。Android API レベルと OS 設定の組み合わせを以下にマッピングする:
 *
 * - API 33+ で `POST_NOTIFICATIONS` が `PERMISSION_DENIED` かつ rationale 不要 → [NotDetermined]
 * - `NotificationManagerCompat.areNotificationsEnabled()` が true → [Granted]
 * - API 33+ で rationale が必要、または永続拒否 → [Denied]
 * - API 32 以下で OS 設定により通知オフ → [Denied]
 * - API 32 以下で通知許可済み（ランタイム要求の概念がない） → [SystemSettingsOnly]
 */
internal sealed interface NotificationPermissionState {
    data object NotDetermined : NotificationPermissionState
    data object Granted : NotificationPermissionState
    data object Denied : NotificationPermissionState
    data object SystemSettingsOnly : NotificationPermissionState
}

private const val LOG_TAG = "NotificationPermission"

/**
 * 権限ダイアログ要求中フラグ。`Switch` の `enabled` を制御して二重タップを防ぐ。
 */
@Composable
internal fun rememberRequestingState(): MutableState<Boolean> = remember { mutableStateOf(false) }

/**
 * 現在の OS 通知許可状態を返す Composable。
 *
 * 共通仕様 E に従い、[Lifecycle.Event.ON_RESUME] で再評価する。設定アプリから
 * 戻ってきた際に表示が即時更新されることを保証する。
 */
@Composable
internal fun rememberNotificationPermissionState(): MutableState<NotificationPermissionState> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state = remember { mutableStateOf(evaluateNotificationPermissionState(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state.value = evaluateNotificationPermissionState(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return state
}

/**
 * `POST_NOTIFICATIONS` を要求するランチャを生成し、結果反映用のコールバックを束ねる。
 *
 * - 要求中フラグは [requesting] で受け取り、UI のボタンを `enabled = false` にできる。
 * - 結果コールバックは状態の即時再評価に使う（共通仕様 E）。
 * - `launch` 例外は `Log.w` でローカルログに残す（共通仕様 F）。
 */
@Composable
internal fun rememberNotificationPermissionLauncher(
    state: MutableState<NotificationPermissionState>,
    requesting: MutableState<Boolean>,
): ActivityResultLauncher<String> {
    val context = LocalContext.current
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        requesting.value = false
        state.value = evaluateNotificationPermissionState(context)
    }
}

/**
 * `POST_NOTIFICATIONS` を安全に要求する。例外は握り潰してログに残し、
 * 二重起動でクラッシュしないよう [requesting] を立ててから launch する。
 */
internal fun safelyRequestNotificationPermission(
    launcher: ActivityResultLauncher<String>,
    requesting: MutableState<Boolean>,
) {
    if (requesting.value) return
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        // API 32 以下ではランタイム要求の概念がないため何もしない（UI 側でも
        // NotDetermined にはならない設計だが、防御的にガードする）。
        return
    }
    requesting.value = true
    try {
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    } catch (e: Exception) {
        requesting.value = false
        Log.w(LOG_TAG, "Failed to launch POST_NOTIFICATIONS request", e)
    }
}

private fun evaluateNotificationPermissionState(
    context: Context,
): NotificationPermissionState {
    val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (enabled) {
            NotificationPermissionState.Granted
        } else {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            // areNotificationsEnabled() が false なので granted は基本通らないが、
                // 念のためフォールバック。
            if (granted) {
                NotificationPermissionState.Granted
            } else if (shouldShowRationale(context)) {
                NotificationPermissionState.Denied
            } else {
                // 1 度も要求していない、または永続拒否のいずれか。前者を優先して
                // NotDetermined を返し、ボタンタップで launch を試みる（永続拒否は
                // OS が即時拒否を返すので、結果的に Denied に遷移する）。
                NotificationPermissionState.NotDetermined
            }
        }
    } else {
        // API 32 以下はランタイム要求の概念がないため、許可状態のみで判別する。
        if (enabled) {
            NotificationPermissionState.SystemSettingsOnly
        } else {
            NotificationPermissionState.Denied
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun shouldShowRationale(context: Context): Boolean {
    val activity = context.findActivity() ?: return false
    return ActivityCompat.shouldShowRequestPermissionRationale(
        activity,
        Manifest.permission.POST_NOTIFICATIONS,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
