package studio.nxtech.fujubank.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform
import studio.nxtech.fujubank.data.remote.ApiError
import studio.nxtech.fujubank.data.remote.ApiErrorCode
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.remote.api.AuthCoreUserApi
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
        } finally {
            // scope cancel / 例外いずれでも clear と onComplete を必ず通す。
            // SessionStore.clear() は同期的な state 更新のみで suspend しないため
            // CancellationException 中でも安全に実行できる。
            sessionStore.clear()
            onComplete()
        }
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
        // mfa_enabled = false の既存ユーザは MFA セットアップ画面群に誘導する
        // （client-bank-21 の resume 経路）。getProfile 失敗は安全側に倒し、Authenticated に進める。
        val authCoreUserApi: AuthCoreUserApi = KoinPlatform.getKoin().get()
        val needsSetup = when (val profile = authCoreUserApi.getProfile()) {
            is NetworkResult.Success -> !profile.value.mfaEnabled
            is NetworkResult.Failure, is NetworkResult.NetworkFailure -> false
        }
        if (needsSetup) {
            sessionStore.setMfaSetupRequired()
        } else {
            sessionStore.setAuthenticated(provision.value.id)
        }
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

// --- Signup + MFA setup フロー（client-bank-21）-----------------------------

/**
 * 新規アカウント作成 → 自動 login → provisionMe までの結果を Swift に伝える型。
 *
 * - [Started]: register + 裏で login + provisionMe が成功し、access_token も取得済み。
 *   呼び出し側は続けて [startMfaSetup] を呼んで QR を取得する。SessionStore は **触らない**
 *   （Authenticated に倒すと AppRoot がホームに飛んでしまうため）。
 * - [LoginAfterRegisterFailed]: register は成功したが裏での login が失敗。アカウント自体は
 *   できているので、UI 側はログイン画面に戻して再ログインを促す。次回 login 時に
 *   `mfa_enabled = false` のため [SessionState.MfaSetupRequired] 経路で MFA セットアップに進む。
 * - [Failure] / [NetworkFailure]: register 自体の失敗。message は AuthErrorMessages 経由で
 *   日本語化済み。UI は入力欄インライン表示などに使う。
 */
sealed class RegisterOutcome {
    object Started : RegisterOutcome()
    object LoginAfterRegisterFailed : RegisterOutcome()
    data class Failure(val message: String, val error: ApiError) : RegisterOutcome()
    data class NetworkFailure(val message: String) : RegisterOutcome()
}

/** [startMfaSetup] の結果。Ready なら base64 QR を SwiftUI / Compose で描画する。 */
sealed class MfaSetupOutcome {
    data class Ready(
        val secret: String,
        val qrPngBase64: String,
        val recoveryCodes: List<String>,
    ) : MfaSetupOutcome()

    data class Failure(val message: String, val error: ApiError) : MfaSetupOutcome()
    data class NetworkFailure(val message: String) : MfaSetupOutcome()
}

/** [enableMfaAndProvision] の結果。Enabled で provisionMe まで完了し、ホーム遷移可能になる。 */
sealed class MfaEnableOutcome {
    data class Enabled(val userId: String) : MfaEnableOutcome()
    data class Failure(val message: String, val error: ApiError) : MfaEnableOutcome()
    data class NetworkFailure(val message: String) : MfaEnableOutcome()
}

/**
 * Swift 側から呼ぶ「アカウント作成 → 自動 login → provisionMe」のファサード。
 *
 * register 成功 → login 成功 → provisionMe 成功までを直列で実行し、SessionStore は **触らない**。
 * これにより AppRoot は引き続き Unauthenticated/サインアップ動線を維持し、続く MFA セットアップ
 * 画面群にローカルナビゲーションで進める。Authenticated への伝搬は [enableMfaAndProvision] で行う。
 */
fun registerAndAutoLogin(
    authRepository: AuthRepository,
    userRepository: UserRepository,
    sessionStore: SessionStore,
    email: String,
    password: String,
    publicId: String,
    onResult: (RegisterOutcome) -> Unit,
) {
    sessionStore.scope.launch {
        val outcome = when (val register = authRepository.register(email, password, publicId)) {
            is NetworkResult.Success -> {
                when (val login = authRepository.login(email, password)) {
                    is NetworkResult.Success -> when (val value = login.value) {
                        is LoginResult.Authenticated -> {
                            // provisionMe は失敗しても致命傷ではないが、後段の mfa/register が
                            // bank user 行を要求するため、ここで成功させておく。
                            when (userRepository.provisionMe()) {
                                is NetworkResult.Success -> RegisterOutcome.Started
                                is NetworkResult.Failure, is NetworkResult.NetworkFailure ->
                                    RegisterOutcome.LoginAfterRegisterFailed
                            }
                        }
                        // register 直後なので mfa_enabled = false。NeedsMfa は通常返らない想定だが、
                        // 万一返ってきたら login 失敗扱いにしてユーザに再ログインを促す。
                        is LoginResult.NeedsMfa -> RegisterOutcome.LoginAfterRegisterFailed
                    }
                    is NetworkResult.Failure, is NetworkResult.NetworkFailure ->
                        RegisterOutcome.LoginAfterRegisterFailed
                }
            }
            is NetworkResult.Failure -> RegisterOutcome.Failure(
                message = AuthErrorMessages.forRegister(register.error),
                error = register.error,
            )
            is NetworkResult.NetworkFailure -> RegisterOutcome.NetworkFailure(
                message = AuthErrorMessages.forNetworkFailure(),
            )
        }
        onResult(outcome)
    }
}

/**
 * Swift 側から呼ぶ「MFA セットアップ開始（QR 取得）」のファサード。
 *
 * 非べき等のため、UI 側で「再生成」ボタン以外で再呼び出ししないこと。
 */
fun startMfaSetup(
    authRepository: AuthRepository,
    sessionStore: SessionStore,
    onResult: (MfaSetupOutcome) -> Unit,
) {
    sessionStore.scope.launch {
        val outcome = when (val setup = authRepository.setupMfa()) {
            is NetworkResult.Success -> MfaSetupOutcome.Ready(
                secret = setup.value.secret,
                qrPngBase64 = setup.value.qrPngBase64,
                recoveryCodes = setup.value.recoveryCodes,
            )
            is NetworkResult.Failure -> MfaSetupOutcome.Failure(
                message = AuthErrorMessages.forMfaSetup(setup.error),
                error = setup.error,
            )
            is NetworkResult.NetworkFailure -> MfaSetupOutcome.NetworkFailure(
                message = AuthErrorMessages.forNetworkFailure(),
            )
        }
        onResult(outcome)
    }
}

/**
 * Swift 側から呼ぶ「TOTP コード送信 → 有効化 → provisionMe」のファサード。
 *
 * 成功時は userId を返すが SessionStore は **触らない**。recovery codes 表示画面まで進ませた後、
 * ユーザが「保存しました」CTA をタップしたタイミングで Swift 側が `setAuthenticated(userId)` を
 * 呼ぶ契約。これにより MFA セットアップ完了 → ホーム遷移のタイミングを UI 側で制御できる。
 *
 * 既に `mfa_enabled = true` の場合（戻る等の異常系）は Enabled 扱いに倒す。
 */
fun enableMfaAndProvision(
    authRepository: AuthRepository,
    userRepository: UserRepository,
    sessionStore: SessionStore,
    code: String,
    onResult: (MfaEnableOutcome) -> Unit,
) {
    sessionStore.scope.launch {
        val outcome = when (val enable = authRepository.enableMfa(code)) {
            is NetworkResult.Success -> provisionToMfaEnableOutcome(userRepository)
            is NetworkResult.Failure -> {
                if (enable.error.code == ApiErrorCode.MFA_ALREADY_ENABLED) {
                    // 既に有効化されているなら provisionMe → Enabled に倒す（再入時の救済）。
                    provisionToMfaEnableOutcome(userRepository)
                } else {
                    MfaEnableOutcome.Failure(
                        message = AuthErrorMessages.forMfaEnable(enable.error),
                        error = enable.error,
                    )
                }
            }
            is NetworkResult.NetworkFailure -> MfaEnableOutcome.NetworkFailure(
                message = AuthErrorMessages.forNetworkFailure(),
            )
        }
        onResult(outcome)
    }
}

private suspend fun provisionToMfaEnableOutcome(
    userRepository: UserRepository,
): MfaEnableOutcome = when (val provision = userRepository.provisionMe()) {
    is NetworkResult.Success -> MfaEnableOutcome.Enabled(userId = provision.value.id)
    is NetworkResult.Failure -> MfaEnableOutcome.Failure(
        message = AuthErrorMessages.forMfaEnable(provision.error),
        error = provision.error,
    )
    is NetworkResult.NetworkFailure -> MfaEnableOutcome.NetworkFailure(
        message = AuthErrorMessages.forNetworkFailure(),
    )
}
