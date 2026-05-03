package studio.nxtech.fujubank.features.account

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import studio.nxtech.fujubank.account.NotificationSettingsPreferences

/**
 * 通知設定画面（Figma `718:7332`）の状態とアクションを束ねる ViewModel。
 *
 * 着金 / 転送のトグル状態は [NotificationSettingsPreferences] が `multiplatform-settings`
 * で永続化しているものを `StateFlow` のまま公開する。トグル操作はそのまま preferences の
 * 書き込みメソッドへ流す。
 */
class NotificationSettingsViewModel(
    private val preferences: NotificationSettingsPreferences,
) : ViewModel() {

    val depositEnabled: StateFlow<Boolean> = preferences.depositEnabled
    val transferEnabled: StateFlow<Boolean> = preferences.transferEnabled

    fun setDepositEnabled(value: Boolean) = preferences.setDepositEnabled(value)
    fun setTransferEnabled(value: Boolean) = preferences.setTransferEnabled(value)
}
