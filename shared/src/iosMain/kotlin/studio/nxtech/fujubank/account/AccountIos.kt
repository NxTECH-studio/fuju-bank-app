package studio.nxtech.fujubank.account

import studio.nxtech.fujubank.session.FlowToken
import studio.nxtech.fujubank.session.observeFlow

/**
 * Swift 側から `NotificationSettingsPreferences.depositEnabled` を観測するための薄い API。
 *
 * `SessionStoreIos` の `observeBootstrapped` と同じスタイルで、初期値も subscribe 直後に
 * 1 回 emit される。観測の実体は [observeFlow] を共用する。
 */
fun observeDepositEnabled(
    preferences: NotificationSettingsPreferences,
    onChange: (Boolean) -> Unit,
): FlowToken = observeFlow(preferences.depositEnabled) { value -> onChange(value) }

/**
 * Swift 側から `NotificationSettingsPreferences.transferEnabled` を観測するための薄い API。
 */
fun observeTransferEnabled(
    preferences: NotificationSettingsPreferences,
    onChange: (Boolean) -> Unit,
): FlowToken = observeFlow(preferences.transferEnabled) { value -> onChange(value) }
