package studio.nxtech.fujubank.features.shell

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
 * ログイン後のルートシェル。Scaffold の bottomBar に Figma `89:12356` `43:258` 準拠の
 * カスタムボトムナビ（白背景 / pt-8 px-48 / 84dp）と、その上に重なる中央 FAB（pink 円形 + 内側ラベル）を
 * Box で重ね合わせる。
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
    val homeFamily = selected == RootDestination.Home ||
        selected == RootDestination.TransactionHistory ||
        selected == RootDestination.Send
    // 親 Box の高さは 84dp(バー) + 13dp(FAB せり出し) = 97dp。FAB を offset で
    // バーの外に出すと Compose の hit test が layout bounds 外を拾わずタップが
    // 取れなくなるため、FAB をこの Box 内に収めてバー側を 13dp 下げる。
    Box(modifier = Modifier.fillMaxWidth().height(97.dp)) {
        // 下段 84dp バー（上端 13dp 下げる）。pt-8 px-48 / 中央は FAB スペース。
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(84.dp)
                .background(FujupayColors.Surface)
                .border(width = 1.dp, color = FujupayColors.BottomBarBorder)
                .padding(top = 8.dp, start = 48.dp, end = 48.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // 左：ホーム（右寄せ、右に 64dp 余白で中央 FAB と離す）
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 64.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                BottomTab(
                    iconRes = R.drawable.ic_home,
                    label = "ホーム",
                    selected = homeFamily,
                    onClick = onSelectHome,
                )
            }
            // 右：アカウント（左寄せ、左に 64dp 余白）
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 64.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BottomTab(
                    iconRes = R.drawable.ic_account_circle,
                    label = "アカウント",
                    selected = selected == RootDestination.Account,
                    onClick = onSelectAccount,
                )
            }
        }
        // 中央 pink 円形 FAB（親 Box の TopCenter に置き、バー上端から 13dp 上にせり出す）。
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(64.dp)
                .shadow(elevation = 6.dp, shape = CircleShape, clip = false)
                .clip(CircleShape)
                .background(FujupayColors.BrandPink)
                .clickable(onClick = onPayClick)
                .padding(top = 10.dp, bottom = 22.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // Text "支払い" でラベル読み上げするので Image 側は cd=null にして二重読み上げを避ける。
                Image(
                    painter = painterResource(R.drawable.ic_pay_qr),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    text = "支払い",
                    style = TextStyle(
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    ),
                )
            }
        }
    }
}

@Composable
private fun BottomTab(
    iconRes: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val labelColor = if (selected) Color.Black else FujupayColors.TextTertiary
    Column(
        modifier = Modifier
            .width(32.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = label,
            modifier = Modifier.size(32.dp),
        )
        Text(
            text = label,
            style = TextStyle(
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = labelColor,
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
