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

/**
 * Swift 側から `PrivacyPreferences.analyticsOptInEnabled` を観測するための薄い API。
 *
 * `observeDepositEnabled` / `observeTransferEnabled` と同じスタイル。subscribe 直後に現在値が
 * 1 回 emit され、以降 [PrivacyPreferences.setAnalyticsOptInEnabled] による変更も流れてくる。
 */
fun observeAnalyticsOptInEnabled(
    preferences: PrivacyPreferences,
    onChange: (Boolean) -> Unit,
): FlowToken = observeFlow(preferences.analyticsOptInEnabled) { value -> onChange(value) }

/**
 * Swift 側から `AccountProfileProvider.profile` を観測するための薄い API。
 *
 * `observeDepositEnabled` / `observeTransferEnabled` と同パターン。subscribe 直後に現在値が
 * 1 回 emit され、以降 [AccountProfileProvider.updateProfile] による変更も流れてくる。
 */
fun observeAccountProfile(
    provider: AccountProfileProvider,
    onChange: (AccountProfile) -> Unit,
): FlowToken = observeFlow(provider.profile) { value -> onChange(value) }

/**
 * VM の `init` で `@Published` の初期値を埋めるための同期取得 API。
 *
 * Kotlin generic `StateFlow<T>.value` は Swift 側で `Any` ベースに露出するため
 * Swift 側で force cast (`as!`) が必要になる。この関数で型保証された
 * [AccountProfile] を直接返すことで Swift 側のキャストを排除する。
 *
 * 観測自体は [observeAccountProfile] を使い、本関数は `@StateObject` 初期化時の
 * 「最初の 1 回」だけ用いる用途を想定する。
 */
fun currentAccountProfile(provider: AccountProfileProvider): AccountProfile =
    provider.profile.value
