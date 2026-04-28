package studio.nxtech.fujubank.data.repository

import studio.nxtech.fujubank.auth.TokenStorage
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.remote.api.AuthApi
import studio.nxtech.fujubank.data.remote.api.LoginRawResponse
import studio.nxtech.fujubank.data.remote.map

/**
 * AuthCore (`fuju-system-authentication`) の認証フローを束ねる Repository。
 *
 * 役割:
 * - login: identifier (メール or 公開ID) + password で認証。MFA 必須なら NeedsMfa を返す。
 * - verifyMfa: pre_token + TOTP / recovery_code で MFA を完了し access_token を保存。
 * - refresh: HttpOnly cookie 経由で `/v1/auth/refresh` を叩いて新 access を保存。
 * - logout: サーバ側 refresh_family を revoke しつつ local の access を消す。
 *
 * refresh_token 文字列はクライアント側に存在しない（HttpCookies plugin の
 * CookiesStorage が cookie を保管・送信する）。
 */
class AuthRepository(
    private val authApi: AuthApi,
    private val tokenStorage: TokenStorage,
    private val nowMillis: () -> Long = { 0L },
) {
    suspend fun login(
        identifier: String,
        password: String,
    ): NetworkResult<LoginResult> =
        when (val result = authApi.login(identifier, password)) {
            is NetworkResult.Success -> {
                val mapped = when (val raw = result.value) {
                    is LoginRawResponse.Token -> {
                        tokenStorage.saveAccess(
                            token = raw.response.accessToken,
                            expiresAt = expiresAtFrom(raw.response.expiresIn),
                        )
                        LoginResult.Authenticated(
                            accessToken = raw.response.accessToken,
                            expiresIn = raw.response.expiresIn,
                        )
                    }
                    is LoginRawResponse.Mfa -> LoginResult.NeedsMfa(
                        preToken = raw.response.preToken,
                        expiresIn = raw.response.expiresIn,
                    )
                }
                NetworkResult.Success(mapped)
            }
            is NetworkResult.Failure -> result
            is NetworkResult.NetworkFailure -> result
        }

    suspend fun verifyMfa(
        preToken: String,
        code: String? = null,
        recoveryCode: String? = null,
    ): NetworkResult<Unit> =
        authApi.mfaVerify(preToken, code = code, recoveryCode = recoveryCode).let { result ->
            when (result) {
                is NetworkResult.Success -> {
                    tokenStorage.saveAccess(
                        token = result.value.accessToken,
                        expiresAt = expiresAtFrom(result.value.expiresIn),
                    )
                    NetworkResult.Success(Unit)
                }
                is NetworkResult.Failure -> result
                is NetworkResult.NetworkFailure -> result
            }
        }

    suspend fun refresh(): NetworkResult<Unit> =
        authApi.refresh().let { result ->
            when (result) {
                is NetworkResult.Success -> {
                    tokenStorage.saveAccess(
                        token = result.value.accessToken,
                        expiresAt = expiresAtFrom(result.value.expiresIn),
                    )
                    NetworkResult.Success(Unit)
                }
                is NetworkResult.Failure -> result
                is NetworkResult.NetworkFailure -> result
            }
        }

    suspend fun logout(): NetworkResult<Unit> {
        val result = authApi.logout()
        // サーバが落ちていてもローカルの access はクリアする。cookie は HttpCookies の
        // storage が握っているが、access が無ければ認証済み扱いにならないので OK。
        tokenStorage.clear()
        return result.map { Unit }
    }

    suspend fun isAuthenticated(): Boolean = tokenStorage.loadAccess() != null

    private fun expiresAtFrom(expiresInSec: Long): Long? {
        val now = nowMillis()
        if (now <= 0L) return null
        return now + expiresInSec * 1_000L
    }
}
