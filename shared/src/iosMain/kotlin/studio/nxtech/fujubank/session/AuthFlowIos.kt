package studio.nxtech.fujubank.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import studio.nxtech.fujubank.data.remote.ApiError
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.repository.AuthRepository
import studio.nxtech.fujubank.data.repository.LoginResult
import studio.nxtech.fujubank.data.repository.UserRepository

/**
 * Swift 側から呼びやすいログインフローの結果型。
 *
 * - [Authenticated]: bank `POST /users/me` まで含めて完了済み。userId は SessionStore に
 *   既に書き込まれている。
 * - [MfaRequired]: MFA 入力画面に遷移すべき。SessionStore も既に MfaPending に切り替えてある。
 * - [Failure]: API エラー。`message` は AuthErrorMessages 経由で日本語化済み。
 * - [NetworkFailure]: 通信失敗。`message` は AuthErrorMessages 経由で日本語化済み。
 *
 * NetworkResult を直接 Swift に晒すよりサブクラス分岐がシンプルになる。
 */
sealed class AuthFlowOutcome {
    object Authenticated : AuthFlowOutcome()
    object MfaRequired : AuthFlowOutcome()
    data class Failure(val message: String, val error: ApiError) : AuthFlowOutcome()
    data class NetworkFailure(val message: String) : AuthFlowOutcome()
}

/**
 * Swift から `loginAndProvision(...) { outcome in ... }` 形で呼び出すためのファサード。
 *
 * AuthRepository.login → 成功なら UserRepository.provisionMe → SessionStore へ反映、
 * までを 1 リクエストとして見立てて [AuthFlowOutcome] にまとめる。失敗時は SessionStore は
 * 触らないので Unauthenticated のまま。
 */
fun loginAndProvision(
    authRepository: AuthRepository,
    userRepository: UserRepository,
    sessionStore: SessionStore,
    identifier: String,
    password: String,
    onResult: (AuthFlowOutcome) -> Unit,
) {
    sessionStore.scope.launch {
        val outcome = when (val login = authRepository.login(identifier, password)) {
            is NetworkResult.Success -> when (val value = login.value) {
                is LoginResult.NeedsMfa -> {
                    sessionStore.setMfaPending(value.preToken)
                    AuthFlowOutcome.MfaRequired
                }
                is LoginResult.Authenticated -> provisionAfterAuth(userRepository, sessionStore)
            }
            is NetworkResult.Failure -> AuthFlowOutcome.Failure(
                message = AuthErrorMessages.forLogin(login.error),
                error = login.error,
            )
            is NetworkResult.NetworkFailure -> AuthFlowOutcome.NetworkFailure(
                message = AuthErrorMessages.forNetworkFailure(),
            )
        }
        onResult(outcome)
    }
}

/**
 * MFA 確認 → provisionMe まで終わったが、SessionStore は **MfaPending のまま** 維持して
 * userId だけ返すバリエーション。MFA 検証成功後に UI 内でオンボーディング画面を挟む際、
 * Authenticated への遷移タイミングを呼び出し側でコントロールするために使う。
 *
 * 呼び出し側はオンボーディング完了時に `sessionStore.setAuthenticated(userId)` を呼ぶ責務を持つ。
 */
fun verifyMfaWithoutAuthenticating(
    authRepository: AuthRepository,
    userRepository: UserRepository,
    sessionStore: SessionStore,
    preToken: String,
    code: String?,
    recoveryCode: String?,
    onResult: (MfaVerifyOutcome) -> Unit,
) {
    sessionStore.scope.launch {
        val outcome = when (val verify = authRepository.verifyMfa(preToken, code = code, recoveryCode = recoveryCode)) {
            is NetworkResult.Success -> when (val provision = userRepository.provisionMe()) {
                is NetworkResult.Success -> MfaVerifyOutcome.Verified(provision.value.id)
                is NetworkResult.Failure -> MfaVerifyOutcome.Failure(
                    message = AuthErrorMessages.forMfa(provision.error),
                    error = provision.error,
                )
                is NetworkResult.NetworkFailure -> MfaVerifyOutcome.NetworkFailure(
                    message = AuthErrorMessages.forNetworkFailure(),
                )
            }
            is NetworkResult.Failure -> MfaVerifyOutcome.Failure(
                message = AuthErrorMessages.forMfa(verify.error),
                error = verify.error,
            )
            is NetworkResult.NetworkFailure -> MfaVerifyOutcome.NetworkFailure(
                message = AuthErrorMessages.forNetworkFailure(),
            )
        }
        onResult(outcome)
    }
}

/** [verifyMfaWithoutAuthenticating] の結果。Authenticated 遷移は呼び出し側が後で行う。 */
sealed class MfaVerifyOutcome {
    data class Verified(val userId: String) : MfaVerifyOutcome()
    data class Failure(val message: String, val error: ApiError) : MfaVerifyOutcome()
    data class NetworkFailure(val message: String) : MfaVerifyOutcome()
}

/**
 * MFA 確認 → 成功なら provisionMe → SessionStore に Authenticated を伝搬。
 */
fun verifyMfaAndProvision(
    authRepository: AuthRepository,
    userRepository: UserRepository,
    sessionStore: SessionStore,
    preToken: String,
    code: String?,
    recoveryCode: String?,
    onResult: (AuthFlowOutcome) -> Unit,
) {
    sessionStore.scope.launch {
        val outcome = when (val verify = authRepository.verifyMfa(preToken, code = code, recoveryCode = recoveryCode)) {
            is NetworkResult.Success -> provisionAfterAuth(userRepository, sessionStore)
            is NetworkResult.Failure -> AuthFlowOutcome.Failure(
                message = AuthErrorMessages.forMfa(verify.error),
                error = verify.error,
            )
            is NetworkResult.NetworkFailure -> AuthFlowOutcome.NetworkFailure(
                message = AuthErrorMessages.forNetworkFailure(),
            )
        }
        onResult(outcome)
    }
}

/**
 * Swift から `logoutAndClear(...) { ... }` 形で呼ぶための logout ヘルパー（client-bank-16）。
 *
 * AuthRepository.logout() はサーバ失敗時もローカル `tokenStorage.clear()` を行うため、
 * 戻り値は意図的に無視する。常に最後に [SessionStore.clear] を呼んで `Unauthenticated` に
 * 倒し、UI へのエラー通知は出さない方針（client-bank-16 確定事項）。
 *
 * `runCatching` は [CancellationException] を握り潰すため、明示的な try/catch で再 throw する
 * プロジェクト共通パターンに合わせる（`NetworkResult.runCatchingNetwork` と同等）。
 */
fun logoutAndClear(
    authRepository: AuthRepository,
    sessionStore: SessionStore,
    onComplete: () -> Unit,
) {
    sessionStore.scope.launch {
        try {
            authRepository.logout()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // logout は失敗しても UI に通知しない方針。最終的に sessionStore.clear() で
            // Unauthenticated に倒すので、ユーザーから見ればログアウト成功と区別不能。
        }
        sessionStore.clear()
        onComplete()
    }
}

/**
 * セッション復元（access or refresh cookie が残っているか確認）。
 * Swift 側はアプリ起動時に呼ぶ。
 */
fun bootstrapSession(
    sessionStore: SessionStore,
    authRepository: AuthRepository,
    userRepository: UserRepository,
    onComplete: () -> Unit,
) {
    sessionStore.scope.launch {
        sessionStore.bootstrap(authRepository, userRepository)
        onComplete()
    }
}

private suspend fun provisionAfterAuth(
    userRepository: UserRepository,
    sessionStore: SessionStore,
): AuthFlowOutcome = when (val provision = userRepository.provisionMe()) {
    is NetworkResult.Success -> {
        sessionStore.setAuthenticated(provision.value.id)
        AuthFlowOutcome.Authenticated
    }
    is NetworkResult.Failure -> AuthFlowOutcome.Failure(
        message = AuthErrorMessages.forLogin(provision.error),
        error = provision.error,
    )
    is NetworkResult.NetworkFailure -> AuthFlowOutcome.NetworkFailure(
        message = AuthErrorMessages.forNetworkFailure(),
    )
}
