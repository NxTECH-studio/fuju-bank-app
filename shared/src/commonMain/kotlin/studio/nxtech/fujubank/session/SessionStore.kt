package studio.nxtech.fujubank.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.repository.AuthRepository
import studio.nxtech.fujubank.data.repository.UserRepository

/**
 * プロセス内で 1 つ共有されるセッション状態のホルダー。
 *
 * - `state` は UI から `collectAsState()` / Combine で観測する。
 * - 認証フロー（[AuthRepository]）と bank 側 user provision（[UserRepository.provisionMe]）の
 *   結果に応じて呼び出し側が `setMfaPending` / `setAuthenticated` / `clear` を切り替える。
 * - [bootstrap] はアプリ起動時に 1 度だけ呼び、access が残っていれば `getMe` で生死確認、
 *   ダメなら refresh→getMe の順でセッション復元を試みる。
 */
class SessionStore {
    private val _state = MutableStateFlow<SessionState>(SessionState.Unauthenticated)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    val current: SessionState
        get() = _state.value

    fun setAuthenticated(userId: String) {
        _state.value = SessionState.Authenticated(userId)
    }

    fun setMfaPending(preToken: String) {
        _state.value = SessionState.MfaPending(preToken)
    }

    fun clear() {
        _state.value = SessionState.Unauthenticated
    }

    /**
     * アプリ起動時に呼び出し、保存済みのアクセストークン or refresh cookie でセッションを復元する。
     *
     * 流れ:
     * 1. access が無ければ refresh を試行（HttpOnly cookie が残っていれば成功する）。
     * 2. access が手に入ったら bank `GET /users/me` を叩いて自分の userId を確定。
     * 3. どこかで失敗したら [SessionState.Unauthenticated] のまま。
     *
     * 失敗時に既存トークンを破棄するかどうかは呼び出し側（authRepository.logout など）に任せる。
     * ここでは「成功したら Authenticated にする」だけに責務を絞る。
     */
    suspend fun bootstrap(
        authRepository: AuthRepository,
        userRepository: UserRepository,
    ) {
        if (!authRepository.isAuthenticated()) {
            // access 無し → refresh を試す（HttpOnly cookie が残っていれば成功する）
            when (authRepository.refresh()) {
                is NetworkResult.Success -> Unit
                is NetworkResult.Failure, is NetworkResult.NetworkFailure -> {
                    _state.value = SessionState.Unauthenticated
                    return
                }
            }
        }
        when (val me = userRepository.getMe()) {
            is NetworkResult.Success -> _state.value = SessionState.Authenticated(me.value.id)
            is NetworkResult.Failure, is NetworkResult.NetworkFailure -> {
                _state.value = SessionState.Unauthenticated
            }
        }
    }
}
