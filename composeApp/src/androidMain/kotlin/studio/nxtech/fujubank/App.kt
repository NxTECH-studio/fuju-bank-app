package studio.nxtech.fujubank

import android.os.SystemClock
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform
import studio.nxtech.fujubank.data.repository.AuthRepository
import studio.nxtech.fujubank.data.repository.UserRepository
import studio.nxtech.fujubank.features.auth.LoginScreen
import studio.nxtech.fujubank.features.auth.LoginViewModel
import studio.nxtech.fujubank.features.auth.MfaVerifyScreen
import studio.nxtech.fujubank.features.auth.MfaVerifyViewModel
import studio.nxtech.fujubank.features.shell.RootScaffold
import studio.nxtech.fujubank.features.signup.MfaCodeScreen
import studio.nxtech.fujubank.features.signup.MfaQrScreen
import studio.nxtech.fujubank.features.signup.RecoveryCodesScreen
import studio.nxtech.fujubank.features.signup.SignUpAccountScreen
import studio.nxtech.fujubank.features.signup.SignUpFlowViewModel
import studio.nxtech.fujubank.features.signup.SignUpPhase
import studio.nxtech.fujubank.features.welcome.WelcomeScreen
import studio.nxtech.fujubank.session.SessionResetCoordinator
import studio.nxtech.fujubank.session.SessionState
import studio.nxtech.fujubank.session.SessionStore
import studio.nxtech.fujubank.session.TokenExpiryWatcher
import studio.nxtech.fujubank.signup.SignupCompletionSignal
import studio.nxtech.fujubank.signup.SignupWelcomePreferences
import studio.nxtech.fujubank.splash.SplashConfig
import studio.nxtech.fujubank.splash.SplashScreen
import studio.nxtech.fujubank.theme.FujuBankColors
import studio.nxtech.fujubank.theme.FujuBankMaterialTypography

/**
 * Android アプリのルート Composable。
 *
 * 起動時は SplashScreen を表示し、`SessionStore.bootstrap()` 完了 + min-duration を
 * 満たした時点で本体 UI に切り替える。本体 UI は SessionStore.state を観測して
 * `Unauthenticated → LoginScreen` / `MfaPending → MfaVerifyScreen` /
 * `MfaSetupRequired → MFA セットアップ画面群` /
 * `Authenticated → RootScaffold (or Welcome)` を切り替える。
 */
@Composable
@Preview
fun App() {
    val koin = remember { KoinPlatform.getKoin() }
    val sessionStore = remember { koin.get<SessionStore>() }
    val authRepository = remember { koin.get<AuthRepository>() }
    val userRepository = remember { koin.get<UserRepository>() }
    val signupCompletionSignal = remember { koin.get<SignupCompletionSignal>() }
    val signupWelcomePreferences = remember { koin.get<SignupWelcomePreferences>() }
    val sessionResetCoordinator = remember { koin.get<SessionResetCoordinator>() }
    val tokenExpiryWatcher = remember { koin.get<TokenExpiryWatcher>() }

    LaunchedEffect(Unit) {
        sessionResetCoordinator.start()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        sessionStore.scope.launch {
            tokenExpiryWatcher.checkNow()
        }
    }

    var splashFinished by rememberSaveable { mutableStateOf(false) }

    if (!splashFinished) {
        LaunchedEffect(Unit) {
            val startedAt = SystemClock.elapsedRealtime()
            sessionStore.bootstrap(authRepository, userRepository)
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            val remaining = SplashConfig.MIN_DURATION_MS - elapsed
            if (remaining > 0) {
                delay(remaining)
            }
            splashFinished = true
        }
    }

    val sessionState by sessionStore.state.collectAsStateWithLifecycle()
    val welcomePending by signupCompletionSignal.pending.collectAsStateWithLifecycle()
    val welcomeAlreadyShown by signupWelcomePreferences.signupCompleted.collectAsStateWithLifecycle()

    var bypassAuth by rememberSaveable { mutableStateOf(false) }

    var signupRoute by rememberSaveable { mutableStateOf(SignupRoute.None) }

    // Authenticated に遷移した時点で signupRoute をリセット。これがないと、ログアウトで
    // Unauthenticated に戻った瞬間に `signupRoute = .Active` が残っていて、しかも
    // SignUpFlowViewModel.phase が `.RecoveryCodes` のままなので RecoveryCodesScreen が
    // 再表示されてしまう。Authenticated 中は signupRoute を見ないので、ここでリセットして
    // 次回 Unauthenticated に戻った時に LoginScreen が出るようにする。
    // signupViewModel 自体は LoginScreen の onSignupClick 内で reset() されるため、
    // ここで触る必要は無い（UnauthenticatedRouter スコープにあって参照できない都合もある）。
    LaunchedEffect(sessionState) {
        if (sessionState is SessionState.Authenticated && signupRoute != SignupRoute.None) {
            signupRoute = SignupRoute.None
        }
    }

    val showRoot = bypassAuth ||
        (sessionState is SessionState.Authenticated && !(welcomePending && !welcomeAlreadyShown))

    MaterialTheme(typography = FujuBankMaterialTypography) {
        if (!splashFinished) {
            SplashScreen()
        } else if (showRoot) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = FujuBankColors.Background,
            ) {
                RootScaffold()
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = FujuBankColors.Background,
            ) {
                when (val state = sessionState) {
                    is SessionState.Unauthenticated -> {
                        UnauthenticatedRouter(
                            signupRoute = signupRoute,
                            onSignupRouteChange = { signupRoute = it },
                            authRepository = authRepository,
                            userRepository = userRepository,
                            sessionStore = sessionStore,
                            signupCompletionSignal = signupCompletionSignal,
                            onBypassAuth = { bypassAuth = true },
                        )
                    }
                    is SessionState.MfaPending -> {
                        val viewModel: MfaVerifyViewModel = viewModel(
                            key = state.preToken,
                            factory = viewModelFactory {
                                initializer {
                                    MfaVerifyViewModel(
                                        preToken = state.preToken,
                                        authRepository = authRepository,
                                        userRepository = userRepository,
                                        sessionStore = sessionStore,
                                    )
                                }
                            },
                        )
                        MfaVerifyScreen(viewModel)
                    }
                    is SessionState.MfaSetupRequired -> {
                        // 既存ユーザのログインで mfa_enabled = false が判明した経路。
                        // サインアップ動線と同じ MFA セットアップ画面群を再利用する。
                        // 入口を AccountInput ではなく MfaQr に固定するため、
                        // ViewModel 生成直後に startMfaSetup を呼ぶ。
                        val signupViewModel: SignUpFlowViewModel = viewModel(
                            factory = viewModelFactory {
                                initializer {
                                    SignUpFlowViewModel(
                                        authRepository = authRepository,
                                        userRepository = userRepository,
                                        sessionStore = sessionStore,
                                        signupCompletionSignal = signupCompletionSignal,
                                    )
                                }
                            },
                        )
                        LaunchedEffect(Unit) {
                            // QR が未取得なら mfa/register を発火。
                            // 失敗時は formError に文言が入り画面内で再生成可能。
                            signupViewModel.regenerateMfaQr()
                        }
                        MfaSetupRouter(viewModel = signupViewModel)
                    }
                    is SessionState.Authenticated -> {
                        WelcomeScreen(
                            onFinish = {
                                signupWelcomePreferences.markCompleted()
                                signupCompletionSignal.consume()
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Unauthenticated 状態における 2 値ナビゲーション。`Active` のときの実画面は
 * [SignUpFlowViewModel.state.phase] を見て決める（AccountInput / MfaQr / MfaCode / RecoveryCodes）。
 */
internal enum class SignupRoute { None, Active }

@Composable
private fun UnauthenticatedRouter(
    signupRoute: SignupRoute,
    onSignupRouteChange: (SignupRoute) -> Unit,
    authRepository: AuthRepository,
    userRepository: UserRepository,
    sessionStore: SessionStore,
    signupCompletionSignal: SignupCompletionSignal,
    onBypassAuth: () -> Unit,
) {
    val signupViewModel: SignUpFlowViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                SignUpFlowViewModel(
                    authRepository = authRepository,
                    userRepository = userRepository,
                    sessionStore = sessionStore,
                    signupCompletionSignal = signupCompletionSignal,
                )
            }
        },
    )
    val signupState by signupViewModel.state.collectAsStateWithLifecycle()

    when (signupRoute) {
        SignupRoute.None -> {
            val viewModel: LoginViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        LoginViewModel(
                            authRepository = authRepository,
                            userRepository = userRepository,
                            sessionStore = sessionStore,
                        )
                    }
                },
            )
            val onDebugSkip: (() -> Unit)? =
                if (BuildConfig.DEBUG) onBypassAuth else null
            LoginScreen(
                viewModel = viewModel,
                onSignupClick = {
                    signupViewModel.reset()
                    onSignupRouteChange(SignupRoute.Active)
                },
                onDebugSkip = onDebugSkip,
            )
        }
        SignupRoute.Active -> when (signupState.phase) {
            SignUpPhase.AccountInput -> SignUpAccountScreen(
                viewModel = signupViewModel,
                onBack = { onSignupRouteChange(SignupRoute.None) },
                onLoginRedirect = { onSignupRouteChange(SignupRoute.None) },
            )
            SignUpPhase.MfaQr -> MfaQrScreen(viewModel = signupViewModel)
            SignUpPhase.MfaCodeInput -> MfaCodeScreen(viewModel = signupViewModel)
            SignUpPhase.RecoveryCodes -> RecoveryCodesScreen(viewModel = signupViewModel)
        }
    }
}

/**
 * 既存ユーザの再ログイン → MFA 未セットアップが判明した場合の専用ルーター。
 *
 * 入口は MfaQr 固定。ViewModel.phase を観測して MfaCode / RecoveryCodes に進む。
 * AccountInput には戻らない（既にアカウントは存在するため）。
 */
@Composable
private fun MfaSetupRouter(
    viewModel: SignUpFlowViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (state.phase) {
        SignUpPhase.AccountInput, SignUpPhase.MfaQr -> MfaQrScreen(viewModel = viewModel)
        SignUpPhase.MfaCodeInput -> MfaCodeScreen(viewModel = viewModel)
        SignUpPhase.RecoveryCodes -> RecoveryCodesScreen(viewModel = viewModel)
    }
}
