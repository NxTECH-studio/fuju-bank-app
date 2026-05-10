package studio.nxtech.fujubank.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import studio.nxtech.fujubank.auth.TokenStorage
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.remote.api.AuthApi
import studio.nxtech.fujubank.data.remote.api.LoginRawResponse
import studio.nxtech.fujubank.data.remote.dto.RegisterRequest
import studio.nxtech.fujubank.data.remote.dto.RegisterResponse
import studio.nxtech.fujubank.data.remote.map
import studio.nxtech.fujubank.network.BearerCacheInvalidator

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
 *
 * **Bearer キャッシュ整合**: 各 token 変更ポイント（saveAccess / clear）の直後に
 * [bearerCacheInvalidator] を呼ぶ。Ktor `Auth { bearer }` プラグインは loadTokens の結果を
 * メモリキャッシュしており、明示的に invalidate しないと別ユーザでログインした後も
 * 前ユーザの Bearer を送り続ける（/v1/user/profile が前ユーザのデータを返す事象の原因）。
 */
class AuthRepository(
    private val authApi: AuthApi,
    private val tokenStorage: TokenStorage,
    // テスト互換のためデフォルト no-op。本番は authModule で実体を注入する。
    private val bearerCacheInvalidator: BearerCacheInvalidator = BearerCacheInvalidator { },
    private val nowMillis: () -> Long = { 0L },
) {
    /**
     * 並行する refresh 呼び出しを直列化するための Mutex。
     *
     * Ktor `Auth` plugin の 401-driven refresh と [studio.nxtech.fujubank.session.TokenExpiryWatcher]
     * の proactive refresh が同じ refresh_token cookie を二重消費すると、AuthCore 側で
     * `TOKEN_REVOKED` が返って refresh_family ごと失効する。`withLock` で 1 本に絞り、
     * 後続呼び出しは先行呼び出しの完了後に最新の access_token を読み直すだけで済むようにする。
     */
    private val refreshMutex = Mutex()
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
                        bearerCacheInvalidator.invalidate()
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
                    bearerCacheInvalidator.invalidate()
                    NetworkResult.Success(Unit)
                }
                is NetworkResult.Failure -> result
                is NetworkResult.NetworkFailure -> result
            }
        }

    suspend fun refresh(): NetworkResult<Unit> = refreshMutex.withLock {
        when (val result = authApi.refresh()) {
            is NetworkResult.Success -> {
                tokenStorage.saveAccess(
                    token = result.value.accessToken,
                    expiresAt = expiresAtFrom(result.value.expiresIn),
                )
                bearerCacheInvalidator.invalidate()
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
        bearerCacheInvalidator.invalidate()
        return result.map { Unit }
    }

    suspend fun isAuthenticated(): Boolean = tokenStorage.loadAccess() != null

    /**
     * `POST /v1/auth/register` を叩いて新規アカウントを作成する。
     *
     * 認証ヘッダ不要。トークンは発行されないため、呼び出し側は続けて [login] を叩いて
     * access_token を取得する必要がある（自動 login は本リポジトリの責務外）。
     */
    suspend fun register(
        email: String,
        password: String,
        publicId: String,
    ): NetworkResult<RegisterResponse> = authApi.register(
        RegisterRequest(email = email, password = password, publicId = publicId),
    )

    /**
     * `POST /v1/auth/mfa/register` を叩いて TOTP secret + QR + recoveryCodes を取得する。
     *
     * **非べき等**: 呼び出すたびに新しい secret / QR / recoveryCodes を返し、旧 secret は
     * サーバ側で失効する。クライアントは「再生成」ボタンなど明示的トリガでのみ再呼び出しすること。
     */
    suspend fun setupMfa(): NetworkResult<MfaSetupBundle> = when (val result = authApi.mfaRegister()) {
        is NetworkResult.Success -> NetworkResult.Success(
            MfaSetupBundle(
                secret = result.value.secret,
                qrPngBase64 = result.value.qrCodeDataUrl.removePrefix(QR_DATA_URL_PREFIX),
                recoveryCodes = result.value.recoveryCodes,
            ),
        )
        is NetworkResult.Failure -> result
        is NetworkResult.NetworkFailure -> result
    }

    /**
     * `POST /v1/auth/mfa/enable` を叩いて MFA を有効化する。
     *
     * 成功すると AuthCore 側で `mfa_enabled = true` がコミットされ、以降のログインで MFA
     * 入力が要求されるようになる。
     */
    suspend fun enableMfa(code: String): NetworkResult<Unit> = authApi.mfaEnable(code = code)

    private fun expiresAtFrom(expiresInSec: Long): Long? {
        val now = nowMillis()
        if (now <= 0L) return null
        return now + expiresInSec * 1_000L
    }

    private companion object {
        const val QR_DATA_URL_PREFIX = "data:image/png;base64,"
    }
}
