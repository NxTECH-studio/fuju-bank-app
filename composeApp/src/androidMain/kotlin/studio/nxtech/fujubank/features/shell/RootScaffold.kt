package studio.nxtech.fujubank.features.shell

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.koin.mp.KoinPlatform
import studio.nxtech.fujubank.R
import studio.nxtech.fujubank.data.repository.ProfileRepository
import studio.nxtech.fujubank.features.account.AccountPlaceholderScreen
import studio.nxtech.fujubank.features.home.HomeScreen
import studio.nxtech.fujubank.features.home.HomeViewModel
import studio.nxtech.fujubank.features.placeholder.ComingSoonScreen
import studio.nxtech.fujubank.navigation.RootDestination
import studio.nxtech.fujubank.theme.FujupayColors

/**
 * ログイン後のルートシェル。Scaffold の bottomBar に NavigationBar 風のタブと、
 * その上に重なる中央 FAB（マゼンタの「支払い」ボタン）を Box で重ね合わせる。
 *
 * MVP では Navigation Compose を導入せず、[RootDestination] を `rememberSaveable` で
 * 保持して切替える。A4 / A5 で本格的な NavGraph に置き換える想定。
 */
@Composable
fun RootScaffold() {
    var destination: RootDestination by rememberSaveable(
        stateSaver = RootDestinationSaver,
    ) { mutableStateOf(RootDestination.Home) }

    val context = LocalContext.current
    val showToast: (String) -> Unit = { message ->
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = FujupayColors.Background,
        bottomBar = {
            BottomNavWithFab(
                selected = destination,
                onSelectHome = { destination = RootDestination.Home },
                onSelectAccount = { destination = RootDestination.Account },
                onPayClick = { showToast("支払い機能は実装中です") },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (destination) {
                RootDestination.Home -> {
                    val viewModel: HomeViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer {
                                HomeViewModel(
                                    profileRepository = KoinPlatform.getKoin().get<ProfileRepository>(),
                                )
                            }
                        },
                    )
                    HomeScreen(
                        viewModel = viewModel,
                        onTransactionHistory = { destination = RootDestination.TransactionHistory },
                        onSendReceive = { destination = RootDestination.Send },
                        onShowToast = showToast,
                    )
                }
                RootDestination.Account -> AccountPlaceholderScreen()
                RootDestination.TransactionHistory -> ComingSoonScreen(
                    title = "取引履歴",
                    onBack = { destination = RootDestination.Home },
                )
                RootDestination.Send -> ComingSoonScreen(
                    title = "送る・もらう",
                    onBack = { destination = RootDestination.Home },
                )
            }
        }
    }
}

@Composable
private fun BottomNavWithFab(
    selected: RootDestination,
    onSelectHome: () -> Unit,
    onSelectAccount: () -> Unit,
    onPayClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        // 下段：ホーム / 中央スペーサ / アカウント
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(FujupayColors.Surface),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            BottomNavItem(
                iconRes = R.drawable.ic_home,
                label = "ホーム",
                selected = selected == RootDestination.Home || selected == RootDestination.TransactionHistory || selected == RootDestination.Send,
                onClick = onSelectHome,
            )
            // 中央 FAB のためのスペース。
            Box(modifier = Modifier.size(72.dp))
            BottomNavItem(
                iconRes = R.drawable.ic_account_circle,
                label = "アカウント",
                selected = selected == RootDestination.Account,
                onClick = onSelectAccount,
            )
        }
        // 中央 FAB（タブの上にせり出す）
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-13).dp)
                .size(56.dp)
                .shadow(elevation = 6.dp, shape = CircleShape, clip = false)
                .clip(CircleShape)
                .background(FujupayColors.BrandPink)
                .clickable(onClick = onPayClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_pay_qr),
                contentDescription = "支払い",
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    iconRes: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (selected) FujupayColors.BrandPink else FujupayColors.TextTertiary
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = tint,
            ),
        )
    }
}

private val RootDestinationSaver = androidx.compose.runtime.saveable.Saver<RootDestination, String>(
    save = { value ->
        when (value) {
            RootDestination.Home -> "home"
            RootDestination.Account -> "account"
            RootDestination.TransactionHistory -> "transactionHistory"
            RootDestination.Send -> "send"
        }
    },
    restore = { key ->
        when (key) {
            "home" -> RootDestination.Home
            "account" -> RootDestination.Account
            "transactionHistory" -> RootDestination.TransactionHistory
            "send" -> RootDestination.Send
            else -> null
        }
    },
)
