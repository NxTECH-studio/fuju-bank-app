package studio.nxtech.fujubank

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.mp.KoinPlatform
import studio.nxtech.fujubank.data.repository.AuthRepository
import studio.nxtech.fujubank.data.repository.UserRepository
import studio.nxtech.fujubank.features.auth.LoginScreen
import studio.nxtech.fujubank.features.auth.LoginViewModel
import studio.nxtech.fujubank.features.auth.MfaVerifyScreen
import studio.nxtech.fujubank.features.auth.MfaVerifyViewModel
import studio.nxtech.fujubank.session.SessionState
import studio.nxtech.fujubank.session.SessionStore

/**
 * Android アプリのルート Composable。
 *
 * SessionStore.state を観測して `Unauthenticated → LoginScreen` /
 * `MfaPending → MfaVerifyScreen` / `Authenticated → 暫定ホームスタブ` を切り替える。
 * ホーム本体は A3 で作るのでここでは残高とサインアウト導線を持たないプレースホルダ。
 */
@Composable
@Preview
fun App() {
    val koin = remember { KoinPlatform.getKoin() }
    val sessionStore = remember { koin.get<SessionStore>() }
    val authRepository = remember { koin.get<AuthRepository>() }
    val userRepository = remember { koin.get<UserRepository>() }

    // アプリ初回起動時に保存済みトークン or refresh cookie でセッション復元を試みる。
    LaunchedEffect(Unit) {
        sessionStore.bootstrap(authRepository, userRepository)
    }

    val sessionState by sessionStore.state.collectAsStateWithLifecycle()

    MaterialTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .safeContentPadding(),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (val state = sessionState) {
                is SessionState.Unauthenticated -> {
                    val viewModel = remember {
                        LoginViewModel(
                            authRepository = authRepository,
                            userRepository = userRepository,
                            sessionStore = sessionStore,
                        )
                    }
                    LoginScreen(viewModel)
                }
                is SessionState.MfaPending -> {
                    // pre_token が変わるたびに ViewModel を作り直すため key 化する。
                    val viewModel = remember(state.preToken) {
                        MfaVerifyViewModel(
                            preToken = state.preToken,
                            authRepository = authRepository,
                            userRepository = userRepository,
                            sessionStore = sessionStore,
                        )
                    }
                    MfaVerifyScreen(viewModel)
                }
                is SessionState.Authenticated -> AuthenticatedPlaceholder(userId = state.userId)
            }
        }
    }
}

@Composable
private fun AuthenticatedPlaceholder(userId: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "ログイン済み: $userId\n（A3 でホーム画面を実装します）",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
