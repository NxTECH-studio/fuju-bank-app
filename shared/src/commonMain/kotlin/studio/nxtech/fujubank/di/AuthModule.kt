package studio.nxtech.fujubank.di

import kotlin.time.Clock
import org.koin.dsl.module
import studio.nxtech.fujubank.auth.TokenStorage
import studio.nxtech.fujubank.auth.TokenStorageFactory
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.remote.api.AuthApi
import studio.nxtech.fujubank.data.repository.AuthRepository
import studio.nxtech.fujubank.network.AuthTokenRefresher
import studio.nxtech.fujubank.session.SessionStore
import studio.nxtech.fujubank.session.invalidateSession

val authModule = module {
    single<TokenStorage> { get<TokenStorageFactory>().create() }
    // AuthApi は 2 つの HttpClient を受け取る。
    // - authCoreClient (AUTHCORE_CLIENT_QUALIFIER): Auth プラグイン無し。
    //   login / refresh / logout / mfaVerify / register に使う。Auth 付きで叩くと
    //   refresh が 401 を返したときに refreshTokens ブロックが自己再帰して deadlock する。
    // - bearerClient (default): Auth プラグイン付き。mfaRegister / mfaEnable に使う。
    //   既に access_token を発行済みの状態で叩くため、Authorization ヘッダを自動付与させる。
    single {
        AuthApi(
            authCoreClient = get(qualifier = AUTHCORE_CLIENT_QUALIFIER),
            bearerClient = get(),
            authCoreBaseUrl = defaultAuthCoreBaseUrl(),
        )
    }
    // nowMillis に実時刻を渡さないと AuthRepository.expiresAtFrom() が常に null を返し、
    // proactive な期限監視（TokenExpiryWatcher）が機能しなくなるので必ず注入する。
    single {
        AuthRepository(
            authApi = get(),
            tokenStorage = get(),
            nowMillis = { Clock.System.now().toEpochMilliseconds() },
        )
    }
    // Ktor Auth plugin の refreshTokens フック実体。AuthApi.refresh() は cookie 経由で
    // refresh するため引数不要。新 access_token を返すか、refresh 不能なら null。
    single<AuthTokenRefresher> {
        createAuthTokenRefresher(
            authRepository = get(),
            tokenStorage = get(),
            sessionStore = get(),
        )
    }
}

/**
 * Ktor Auth プラグインの refreshTokens 用フックを組み立てる factory。
 *
 * refresh の結果ごとの振る舞い:
 * - [NetworkResult.Success]: 保存済みの access token を返し、Ktor 側でリトライさせる。
 * - [NetworkResult.Failure]: API エラー応答（MFA_REQUIRED / TOKEN_REVOKED /
 *   refresh cookie 失効など）。refresh_family が revoke された可能性が高いため、
 *   TokenStorage と SessionStore を即クリアして UI を Unauthenticated に倒し、null を返す。
 *   `tokenStorage.clear()` を `sessionStore.clear()` より先に呼ぶことで、
 *   UI が Unauthenticated を観測した時点で残存トークンが無いことを保証する。
 * - [NetworkResult.NetworkFailure]: 一時的な通信エラー（オフライン / DNS / タイムアウト）。
 *   clear せず null だけ返し、Ktor 側に再試行余地を残す（圏外で毎回ログアウトされる
 *   UX 破壊を回避）。
 *
 * テスト容易性のため `internal` で切り出している。本番では [authModule] が
 * Koin 経由で組み立てる。
 */
internal fun createAuthTokenRefresher(
    authRepository: AuthRepository,
    tokenStorage: TokenStorage,
    sessionStore: SessionStore,
): AuthTokenRefresher = AuthTokenRefresher {
    when (authRepository.refresh()) {
        is NetworkResult.Success -> tokenStorage.loadAccess()
        is NetworkResult.Failure -> {
            invalidateSession(tokenStorage, sessionStore)
            null
        }
        is NetworkResult.NetworkFailure -> null
    }
}
