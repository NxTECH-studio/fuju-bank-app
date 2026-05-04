package studio.nxtech.fujubank.account

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * プライバシー設定（トラッキング許諾）のオン・オフを永続化するプリファレンス。
 *
 * Figma `798:12559` の「アプリのトラッキングを許可」トグルに対応。
 * 既定値は `false`（オプトイン方式）— ユーザーが明示的に許可するまで
 * データ収集はオフ扱いとする安全側のデフォルト。
 *
 * `NotificationSettingsPreferences` と同パターンで `multiplatform-settings` を使い、
 * 値の変化は `StateFlow` で配信して Android / iOS いずれの UI からも購読できるようにする。
 *
 * 書き込み API（[setAnalyticsOptInEnabled]）は UI スレッドからの単一スレッド呼び出しを
 * 前提とする。`putBoolean` と `StateFlow.value` の更新が個別の atomic 操作で構成されているため、
 * 並行呼び出しでは Settings 永続値と StateFlow 観測値の順序が逆転する可能性がある。
 */
class PrivacyPreferences(private val settings: Settings) {
    private val _analyticsOptInEnabled = MutableStateFlow(
        settings.getBoolean(KEY_ANALYTICS_OPT_IN, false),
    )
    val analyticsOptInEnabled: StateFlow<Boolean> = _analyticsOptInEnabled.asStateFlow()

    fun setAnalyticsOptInEnabled(value: Boolean) {
        settings.putBoolean(KEY_ANALYTICS_OPT_IN, value)
        _analyticsOptInEnabled.value = value
    }

    private companion object {
        // 既定値 false（オプトイン）。GDPR / 改正個人情報保護法準拠の安全側デフォルト。
        const val KEY_ANALYTICS_OPT_IN = "privacy.analytics.optIn.enabled"
    }
}
