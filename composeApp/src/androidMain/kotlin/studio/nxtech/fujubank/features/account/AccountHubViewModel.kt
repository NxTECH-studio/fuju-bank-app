package studio.nxtech.fujubank.features.account

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import studio.nxtech.fujubank.account.AccountProfile
import studio.nxtech.fujubank.account.AccountProfileProvider

/**
 * アカウントハブ画面（Figma `697:8394`）の状態保持。
 *
 * [AccountProfileProvider] が公開する `StateFlow<AccountProfile>` を直接 UI に流し、
 * 編集操作は [updateDisplayName] / [updateEmail] で Provider に書き戻す。
 * Provider 側が in-memory state を持つため、保存時に Provider が更新されると
 * 本 VM 経由でも UI に即時反映される。
 */
class AccountHubViewModel(
    private val profileProvider: AccountProfileProvider,
) : ViewModel() {

    val profile: StateFlow<AccountProfile> = profileProvider.profile

    /** 表示名のみ更新。メールアドレスは現在値を維持する。 */
    fun updateDisplayName(displayName: String) {
        val current = profileProvider.profile.value
        profileProvider.updateProfile(displayName = displayName, email = current.email)
    }

    /** メールアドレスのみ更新。表示名は現在値を維持する。 */
    fun updateEmail(email: String) {
        val current = profileProvider.profile.value
        profileProvider.updateProfile(displayName = current.displayName, email = email)
    }
}
