package studio.nxtech.fujubank.features.account

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import studio.nxtech.fujubank.account.PrivacyPreferences

/**
 * プライバシー設定画面（Figma `798:12559`）の状態とアクションを束ねる ViewModel。
 *
 * トラッキング許諾トグルの状態は [PrivacyPreferences] が `multiplatform-settings`
 * で永続化しているものを `StateFlow` のまま公開する。トグル操作はそのまま preferences の
 * 書き込みメソッドへ流す。
 */
class PrivacySettingsViewModel(
    private val preferences: PrivacyPreferences,
) : ViewModel() {

    val analyticsOptInEnabled: StateFlow<Boolean> = preferences.analyticsOptInEnabled

    fun setAnalyticsOptInEnabled(value: Boolean) = preferences.setAnalyticsOptInEnabled(value)
}
