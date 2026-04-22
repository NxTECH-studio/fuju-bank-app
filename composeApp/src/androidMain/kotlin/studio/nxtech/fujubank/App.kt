package studio.nxtech.fujubank

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.mp.KoinPlatform
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.remote.api.UserApi

import fujubankapp.composeapp.generated.resources.Res
import fujubankapp.composeapp.generated.resources.compose_multiplatform

@Composable
@Preview
fun App() {
    MaterialTheme {
        var showContent by remember { mutableStateOf(false) }
        // TODO: remove after smoke test
        val scope = rememberCoroutineScope()
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(onClick = { showContent = !showContent }) {
                Text("Click me!")
            }
            // TODO: remove after smoke test
            Button(onClick = { scope.launch { runUserApiSmokeTest() } }) {
                Text("Smoke test: UserApi.get")
            }
            AnimatedVisibility(showContent) {
                val greeting = remember { Greeting().greet() }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(painterResource(Res.drawable.compose_multiplatform), null)
                    Text("Compose: $greeting")
                }
            }
        }
    }
}

// TODO: remove after smoke test
private const val SMOKE_TEST_TAG = "FujuBankSmoke"

// TODO: remove after smoke test
private const val SMOKE_TEST_USER_ID = "00000000-0000-0000-0000-000000000000"

// TODO: remove after smoke test
private suspend fun runUserApiSmokeTest() {
    val api = KoinPlatform.getKoin().get<UserApi>()
    when (val result = api.get(SMOKE_TEST_USER_ID)) {
        is NetworkResult.Success -> Log.i(
            SMOKE_TEST_TAG,
            "user=${result.value.id} balance_fuju=${result.value.balanceFuju}",
        )
        is NetworkResult.Failure -> Log.w(
            SMOKE_TEST_TAG,
            "api error code=${result.error.code} status=${result.error.httpStatus}",
        )
        is NetworkResult.NetworkFailure -> Log.w(
            SMOKE_TEST_TAG,
            "network failure",
            result.cause,
        )
    }
}
