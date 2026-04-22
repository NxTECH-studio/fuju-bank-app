package studio.nxtech.fujubank.data.repository

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import studio.nxtech.fujubank.auth.TokenStorage
import studio.nxtech.fujubank.data.remote.ApiErrorCode
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.remote.api.AuthApi

class AuthRepository(
    private val authApi: AuthApi,
    private val tokenStorage: TokenStorage,
) {
    private val _mfaRequiredEvents = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val mfaRequiredEvents: SharedFlow<Unit> = _mfaRequiredEvents.asSharedFlow()

    suspend fun login(email: String, password: String): NetworkResult<Unit> =
        when (val result = authApi.login(email, password)) {
            is NetworkResult.Success -> {
                tokenStorage.save(
                    access = result.value.accessToken,
                    refresh = result.value.refreshToken,
                    subject = result.value.subject,
                )
                NetworkResult.Success(Unit)
            }
            is NetworkResult.Failure -> {
                if (result.error.code == ApiErrorCode.MFA_REQUIRED) {
                    _mfaRequiredEvents.emit(Unit)
                }
                result
            }
            is NetworkResult.NetworkFailure -> result
        }

    suspend fun logout() {
        tokenStorage.clear()
    }

    suspend fun isAuthenticated(): Boolean = tokenStorage.getAccessToken() != null
}
