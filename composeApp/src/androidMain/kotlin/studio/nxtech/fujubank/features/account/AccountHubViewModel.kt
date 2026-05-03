package studio.nxtech.fujubank.features.account

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import studio.nxtech.fujubank.account.AccountProfile
import studio.nxtech.fujubank.account.AccountProfileProvider

/**
 * アカウントハブ画面（Figma `697:8394`）の状態保持。
 *
 * 本タスクではダミー固定の [AccountProfileProvider] から 1 度だけプロフィールを取得し、
 * `StateFlow` として公開する。実 API 連携時には provider の実装が差し替わるだけで
 * 本クラスは変更不要。
 */
class AccountHubViewModel(
    profileProvider: AccountProfileProvider,
) : ViewModel() {

    private val _profile = MutableStateFlow(profileProvider.current())
    val profile: StateFlow<AccountProfile> = _profile.asStateFlow()
}
