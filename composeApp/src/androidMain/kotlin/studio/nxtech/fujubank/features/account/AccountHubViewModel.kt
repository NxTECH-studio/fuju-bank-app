package studio.nxtech.fujubank.features.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import studio.nxtech.fujubank.account.AccountProfile
import studio.nxtech.fujubank.account.AccountProfileProvider
import studio.nxtech.fujubank.data.repository.AuthRepository
import studio.nxtech.fujubank.session.SessionStore

/**
 * アカウントハブ画面（Figma `697:8394`）の状態保持。
 *
 * [AccountProfileProvider] が公開する `StateFlow<AccountProfile>` を直接 UI に流し、
 * 編集操作は [updatePublicId] / [updateEmail] で Provider に書き戻す。
 * Provider 側が in-memory state を持つため、保存時に Provider が更新されると
 * 本 VM 経由でも UI に即時反映される。
 *
 * client-bank-16: ログアウト導線を追加。[logout] は `AuthRepository.logout()` の結果に
 * 関わらず最後に [SessionStore.clear] を呼んで `Unauthenticated` に倒す（サーバ失敗時も
 * `tokenStorage.clear()` は走るため UI へのエラー通知は出さない方針）。
 */
class AccountHubViewModel(
    private val profileProvider: AccountProfileProvider,
    private val authRepository: AuthRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {

    val profile: StateFlow<AccountProfile> = profileProvider.profile

    private val _isLoggingOut = MutableStateFlow(false)
    val isLoggingOut: StateFlow<Boolean> = _isLoggingOut.asStateFlow()

    /**
     * AccountHub 画面初回表示時に呼び、プロフィールキャッシュをロードする。
     *
     * Provider 側で冪等化されているため、複数回呼ばれても API は 1 度しか叩かない
     * （ログアウト → 再ログイン後の再表示でのみ再 fetch される）。
     */
    fun ensureProfileLoaded() {
        viewModelScope.launch {
            profileProvider.ensureLoaded()
        }
    }

    /** 公開IDのみ更新。メールアドレスは現在値を維持する。 */
    fun updatePublicId(publicId: String) {
        val current = profileProvider.profile.value
        profileProvider.updateProfile(publicId = publicId, email = current.email)
    }

    /** メールアドレスのみ更新。公開IDは現在値を維持する。 */
    fun updateEmail(email: String) {
        val current = profileProvider.profile.value
        profileProvider.updateProfile(publicId = current.publicId, email = email)
    }

    /**
     * ログアウト処理。サーバ呼び出しの結果に関わらず最後に [SessionStore.clear] を呼ぶ。
     *
     * - `AuthRepository.logout()` 自体がサーバ失敗時もローカル `tokenStorage.clear()` を
     *   行うため、UI へのエラー通知は出さない（client-bank-16 確定方針）。
     * - 二度押し防止に [_isLoggingOut] でガードする。
     * - `runCatching` は [CancellationException] を握り潰してしまうため、明示的な
     *   try/catch で再 throw するプロジェクト共通パターンに合わせる
     *   （`shared/.../NetworkResult.kt` の `runCatchingNetwork` と同等）。
     * - 起点 scope は [SessionStore.scope]（app lifecycle に紐付く SupervisorJob）。
     *   logout 進行中に AccountHub から離脱して VM が cancel されても、サーバ呼び出し→
     *   `sessionStore.clear()` まで完走させるため `viewModelScope` ではなくこちらを使う。
     *   iOS 側 `AuthFlowIos.logoutAndClear` と同じ scope 戦略。
     */
    fun logout() {
        if (_isLoggingOut.value) return
        _isLoggingOut.value = true
        sessionStore.scope.launch {
            try {
                authRepository.logout()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // logout は失敗しても UI に通知しない方針（最終的に sessionStore.clear で
                // Unauthenticated に倒すので、ユーザーから見ればログアウト成功と区別不能）。
            } finally {
                // 親 scope が cancel されても clear と flag リセットは必ず実行する。
                sessionStore.clear()
                _isLoggingOut.value = false
            }
        }
    }
}
