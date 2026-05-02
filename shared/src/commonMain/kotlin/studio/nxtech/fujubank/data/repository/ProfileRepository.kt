package studio.nxtech.fujubank.data.repository

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.remote.api.AuthCoreUserApi
import studio.nxtech.fujubank.data.remote.api.UserMeApi
import studio.nxtech.fujubank.data.remote.dto.AuthCoreUserResponse
import studio.nxtech.fujubank.data.remote.dto.UserResponse
import studio.nxtech.fujubank.domain.model.UserProfile

/**
 * ホーム画面用のプロフィール取得を担うリポジトリ。
 *
 * AuthCore (`/v1/user/profile`) と bank (`/users/me`) を並列に叩き、両方成功した場合に
 * `UserProfile` にマージして返す。どちらかが失敗した場合は失敗をそのまま返す。
 *
 * - 401 などの認証エラーは Ktor Auth plugin の refresh フックで一度リトライされる。
 *   refresh も失敗した場合は `NetworkResult.Failure` で返り、上位の SessionStore で
 *   Unauthenticated に倒す想定（A2b の挙動）。
 */
class ProfileRepository(
    private val authCoreUserApi: AuthCoreUserApi,
    private val userMeApi: UserMeApi,
) {

    suspend fun getMyProfile(): NetworkResult<UserProfile> = coroutineScope {
        val authCoreDeferred = async { authCoreUserApi.getProfile() }
        val bankDeferred = async { userMeApi.getMe() }
        val authCoreResult = authCoreDeferred.await()
        val bankResult = bankDeferred.await()
        merge(authCoreResult, bankResult)
    }

    private fun merge(
        authCore: NetworkResult<AuthCoreUserResponse>,
        bank: NetworkResult<UserResponse>,
    ): NetworkResult<UserProfile> = when {
        authCore is NetworkResult.Success && bank is NetworkResult.Success ->
            NetworkResult.Success(toProfile(authCore.value, bank.value))
        authCore is NetworkResult.Failure -> authCore
        bank is NetworkResult.Failure -> bank
        authCore is NetworkResult.NetworkFailure -> authCore
        bank is NetworkResult.NetworkFailure -> bank
        // 上記で全分岐を網羅しているがコンパイラのため fallback。
        else -> NetworkResult.NetworkFailure(IllegalStateException("Unreachable merge state"))
    }

    private fun toProfile(authCore: AuthCoreUserResponse, bank: UserResponse): UserProfile =
        UserProfile(
            authCoreId = authCore.id,
            bankUserId = bank.id,
            publicId = authCore.publicId,
            email = authCore.email,
            iconUrl = authCore.iconUrl,
            mfaEnabled = authCore.mfaEnabled,
            balanceFuju = bank.balanceFuju,
        )
}
