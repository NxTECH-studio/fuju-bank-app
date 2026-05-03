package studio.nxtech.fujubank.account

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 通知設定（着金通知 / 転送通知）のオン・オフを永続化するプリファレンス。
 *
 * Figma `718:7332` の 2 トグルに対応。OS 側の通知許可とは独立した「アプリ内 UI 表現」の値。
 * 既定値は両方 `true`（ユーザーが明示的にオフにするまで通知意図あり扱い）。
 *
 * `SignupWelcomePreferences` と同じく `multiplatform-settings` を使い、
 * 値の変化は `StateFlow` で配信して Android / iOS いずれの UI からも購読できるようにする。
 *
 * 書き込み API（[setDepositEnabled] / [setTransferEnabled]）は UI スレッドからの単一スレッド呼び出しを
 * 前提とする。`putBoolean` と `StateFlow.value` の更新が個別の atomic 操作で構成されているため、
 * 並行呼び出しでは Settings 永続値と StateFlow 観測値の順序が逆転する可能性がある。
 */
class NotificationSettingsPreferences(private val settings: Settings) {
    private val _depositEnabled = MutableStateFlow(settings.getBoolean(KEY_DEPOSIT, true))
    val depositEnabled: StateFlow<Boolean> = _depositEnabled.asStateFlow()

    private val _transferEnabled = MutableStateFlow(settings.getBoolean(KEY_TRANSFER, true))
    val transferEnabled: StateFlow<Boolean> = _transferEnabled.asStateFlow()

    fun setDepositEnabled(value: Boolean) {
        settings.putBoolean(KEY_DEPOSIT, value)
        _depositEnabled.value = value
    }

    fun setTransferEnabled(value: Boolean) {
        settings.putBoolean(KEY_TRANSFER, value)
        _transferEnabled.value = value
    }

    private companion object {
        const val KEY_DEPOSIT = "notification.deposit.enabled"
        const val KEY_TRANSFER = "notification.transfer.enabled"
    }
}
