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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.delay
import org.koin.mp.KoinPlatform
import studio.nxtech.fujubank.data.repository.AuthRepository
import studio.nxtech.fujubank.data.repository.UserRepository
import studio.nxtech.fujubank.features.auth.LoginScreen
import studio.nxtech.fujubank.features.auth.LoginViewModel
import studio.nxtech.fujubank.features.auth.MfaVerifyScreen
import studio.nxtech.fujubank.features.auth.MfaVerifyViewModel
import studio.nxtech.fujubank.features.shell.RootScaffold
import studio.nxtech.fujubank.features.signup.SignUpCreateScreen
import studio.nxtech.fujubank.features.signup.SignUpFlowViewModel
import studio.nxtech.fujubank.features.signup.SignUpOtpScreen
import studio.nxtech.fujubank.features.signup.SignUpSuccessScreen
import studio.nxtech.fujubank.features.welcome.WelcomeScreen
import studio.nxtech.fujubank.session.SessionState
import studio.nxtech.fujubank.session.SessionStore
import studio.nxtech.fujubank.signup.SignupCompletionSignal
import studio.nxtech.fujubank.signup.SignupWelcomePreferences
import studio.nxtech.fujubank.splash.SplashConfig
import studio.nxtech.fujubank.splash.SplashScreen
import studio.nxtech.fujubank.theme.FujupayColors

/**
 * Android アプリのルート Composable。
 *
 * 起動時は SplashScreen を表示し、`SessionStore.bootstrap()` 完了 + min-duration を
 * 満たした時点で本体 UI に切り替える。本体 UI は SessionStore.state を観測して
 * `Unauthenticated → LoginScreen` / `MfaPending → MfaVerifyScreen` /
 * `Authenticated → 暫定ホームスタブ` を切り替える。ホーム本体は A3 で作るので
 * ここでは残高とサインアウト導線を持たないプレースホルダ。
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

    // 画面回転で Activity が再生成されても Splash を再表示しないよう rememberSaveable で保持。
    // SystemClock.elapsedRealtime() は端末スリープ中も進むため、最低表示時間を厳密に保証する。
    var splashFinished by rememberSaveable { mutableStateOf(false) }

    if (!splashFinished) {
        LaunchedEffect(Unit) {
            val startedAt = SystemClock.elapsedRealtime()
            // bootstrap は 2 回目以降の呼び出しが冪等。アプリ初回起動時に保存済み
            // トークン or refresh cookie でセッション復元を試みる。
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

    // debug ビルド専用の認証スキップフラグ。SessionStore は触らず UI 層だけで強制的に
    // AuthenticatedPlaceholder を出す。回転で消えると煩わしいので rememberSaveable。
    var bypassAuth by rememberSaveable { mutableStateOf(false) }

    // サインアップフローの現在地。Unauthenticated 中のローカルナビゲーションとして扱う。
    // ログイン成功時は SessionState.Authenticated 経由でこの画面群は出なくなるため、
    // 完了画面の「次へ」では None に戻すだけで良い。
    var signupRoute by rememberSaveable { mutableStateOf(SignupRoute.None) }

    // 親 Surface には safeContentPadding を掛けず edge-to-edge にする。各画面側で
    // 自前の bg を fillMaxSize で塗り、内側コンテンツに systemBarsPadding を入れて
    // status bar / nav bar を避ける構成。RootScaffold は内部 Scaffold が
    // WindowInsets を管理するため同じく edge-to-edge で OK。
    val showRoot = bypassAuth ||
        (sessionState is SessionState.Authenticated && !(welcomePending && !welcomeAlreadyShown))

    MaterialTheme {
        if (!splashFinished) {
            SplashScreen()
        } else if (showRoot) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = FujupayColors.Background,
            ) {
                RootScaffold()
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = FujupayColors.Background,
            ) {
                when (val state = sessionState) {
                    is SessionState.Unauthenticated -> {
                        UnauthenticatedRouter(
                            signupRoute = signupRoute,
                            onSignupRouteChange = { signupRoute = it },
                            authRepository = authRepository,
                            userRepository = userRepository,
                            sessionStore = sessionStore,
                            onBypassAuth = { bypassAuth = true },
                        )
                    }
                    is SessionState.MfaPending -> {
                        // pre_token が変わるたびに ViewModel を作り直すため key 化する。
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
                    is SessionState.Authenticated -> {
                        // showRoot 分岐から漏れたケース = Welcome 表示中のみ。
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

private enum class SignupRoute { None, Create, Otp, Success }

@Composable
private fun UnauthenticatedRouter(
    signupRoute: SignupRoute,
    onSignupRouteChange: (SignupRoute) -> Unit,
    authRepository: AuthRepository,
    userRepository: UserRepository,
    sessionStore: SessionStore,
    onBypassAuth: () -> Unit,
) {
    val signupViewModel: SignUpFlowViewModel = viewModel()

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
            // release ビルドでは null を渡し、debug ビルド限定のスキップ CTA を完全に
            // 合成対象外にする。BuildConfig.DEBUG はコンパイル時定数のため、release では
            // 常に null 経路となり LoginScreen 内の if (onDebugSkip != null) がデッドコード化する。
            val onDebugSkip: (() -> Unit)? =
                if (BuildConfig.DEBUG) onBypassAuth else null
            LoginScreen(
                viewModel = viewModel,
                onSignupClick = {
                    signupViewModel.reset()
                    onSignupRouteChange(SignupRoute.Create)
                },
                onDebugSkip = onDebugSkip,
            )
        }
        SignupRoute.Create -> SignUpCreateScreen(
            viewModel = signupViewModel,
            onNext = { onSignupRouteChange(SignupRoute.Otp) },
            onBack = { onSignupRouteChange(SignupRoute.None) },
            onLoginRedirect = { onSignupRouteChange(SignupRoute.None) },
        )
        SignupRoute.Otp -> SignUpOtpScreen(
            viewModel = signupViewModel,
            onConfirm = { onSignupRouteChange(SignupRoute.Success) },
            onBack = { onSignupRouteChange(SignupRoute.Create) },
        )
        SignupRoute.Success -> SignUpSuccessScreen(
            onFinish = {
                signupViewModel.reset()
                onSignupRouteChange(SignupRoute.None)
            },
        )
    }
}

