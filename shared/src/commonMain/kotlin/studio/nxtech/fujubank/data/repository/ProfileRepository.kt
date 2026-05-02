package studio.nxtech.fujubank.data.repository

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import studio.nxtech.fujubank.BuildKonfig
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

    suspend fun getMyProfile(): NetworkResult<UserProfile> {
        if (BuildKonfig.USE_DUMMY_PROFILE) {
            // 通信を伴わない UI 確認用フェイクデータ。loading 状態を観察できるよう少しだけ待つ。
            delay(300)
            return NetworkResult.Success(DUMMY_PROFILE)
        }
        return coroutineScope {
            val authCoreDeferred = async { authCoreUserApi.getProfile() }
            val bankDeferred = async { userMeApi.getMe() }
            val authCoreResult = authCoreDeferred.await()
            val bankResult = bankDeferred.await()
            merge(authCoreResult, bankResult)
        }
    }

    private fun merge(
        authCore: NetworkResult<AuthCoreUserResponse>,
        bank: NetworkResult<UserResponse>,
    ): NetworkResult<UserProfile> = when {
        authCore is NetworkResult.Success && bank is NetworkResult.Success ->
            toProfile(authCore.value, bank.value)
        authCore is NetworkResult.Failure -> authCore
        bank is NetworkResult.Failure -> bank
        authCore is NetworkResult.NetworkFailure -> authCore
        bank is NetworkResult.NetworkFailure -> bank
        else -> error("Unreachable: NetworkResult is sealed and all variants are handled above")
    }

    private fun toProfile(authCore: AuthCoreUserResponse, bank: UserResponse): NetworkResult<UserProfile> {
        // public_id は QR / Code128 にエンコードされるため、想定外文字（URL スキームや制御文字）が
        // 入った場合に第三者にスキャンされたとき任意リダイレクトを誘発しうる。サーバ侵害や
        // DTO 想定外応答に備えて、ここで形式を allow-list 検証する。
        if (!isValidPublicId(authCore.publicId)) {
            return NetworkResult.NetworkFailure(
                IllegalStateException("Invalid publicId from AuthCore"),
            )
        }
        return NetworkResult.Success(
            UserProfile(
                authCoreId = authCore.id,
                bankUserId = bank.id,
                publicId = authCore.publicId,
                email = authCore.email,
                iconUrl = authCore.iconUrl,
                mfaEnabled = authCore.mfaEnabled,
                balanceFuju = bank.balanceFuju,
            ),
        )
    }
}

private val PUBLIC_ID_REGEX = Regex("^[A-Za-z0-9_-]{1,64}$")

internal fun isValidPublicId(value: String): Boolean = PUBLIC_ID_REGEX.matches(value)

private val DUMMY_PROFILE = UserProfile(
    authCoreId = "01HX4T8K7N9P2QABC0DEF12345",
    bankUserId = "user_dummy_001",
    publicId = "fujupay_demo_user",
    email = "demo@fujupay.app",
    iconUrl = null,
    mfaEnabled = false,
    balanceFuju = 1_234_567L,
)
