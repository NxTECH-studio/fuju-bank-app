package studio.nxtech.fujubank.features.account

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import studio.nxtech.fujubank.account.AccountProfile
import studio.nxtech.fujubank.account.AccountProfileProvider

/**
 * アカウントハブ画面（Figma `697:8394`）の状態保持。
 *
 * [AccountProfileProvider] が公開する `StateFlow<AccountProfile>` を直接 UI に流し、
 * 編集操作は [updateProfile] で Provider に書き戻す。Provider 側が in-memory state を
 * 持つため、保存時に Provider が更新されると本 VM 経由でも UI に即時反映される。
 */
class AccountHubViewModel(
    private val profileProvider: AccountProfileProvider,
) : ViewModel() {

    val profile: StateFlow<AccountProfile> = profileProvider.profile

    /** 編集ボトムシートからの保存。表示名 / メールアドレスを Provider に書き戻す。 */
    fun updateProfile(displayName: String, email: String) {
        profileProvider.updateProfile(displayName = displayName, email = email)
    }
}
