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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
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
import studio.nxtech.fujubank.data.repository.UserRepository
import studio.nxtech.fujubank.domain.model.Transaction
import studio.nxtech.fujubank.features.account.AccountPlaceholderScreen
import studio.nxtech.fujubank.features.home.HomeScreen
import studio.nxtech.fujubank.features.home.HomeViewModel
import studio.nxtech.fujubank.features.placeholder.ComingSoonScreen
import studio.nxtech.fujubank.features.transactions.TransactionDetailScreen
import studio.nxtech.fujubank.features.transactions.TransactionDetailViewModel
import studio.nxtech.fujubank.features.transactions.TransactionListScreen
import studio.nxtech.fujubank.features.transactions.TransactionListViewModel
import studio.nxtech.fujubank.navigation.RootDestination
import studio.nxtech.fujubank.session.SessionStore
import studio.nxtech.fujubank.theme.FujuBankColors

/**
 * ログイン後のルートシェル。Scaffold の bottomBar に Figma `709:8658` / `697:7601` / `702:6440`
 * 共通のボトムナビ（白背景 / pt-8 px-48 / 84dp / 2 タブ均等配置）を描画する。
 *
 * MVP では Navigation Compose を導入せず、[RootDestination] を `rememberSaveable` で保持して切替える。
 * 取引詳細遷移時に対象の `Transaction` を別途 `remember` で保持する（Saver の実装コストを避ける）。
 */
@Composable
fun RootScaffold() {
    var destination: RootDestination by rememberSaveable(
        stateSaver = RootDestinationSaver,
    ) { mutableStateOf(RootDestination.Home) }

    // 取引詳細に遷移する際の対象。プロセス再生成時には失われ、自動で履歴へ戻す挙動になる。
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }

    val context = LocalContext.current
    val showToast: (String) -> Unit = { message ->
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    // フッター（ボトムナビ）はメインタブとサブ画面 (履歴/詳細) で表示する。
    val showBottomBar = destination != RootDestination.Send
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = FujuBankColors.Background,
        bottomBar = {
            if (showBottomBar) {
                BottomNav(
                    selected = destination,
                    onSelectHome = {
                        selectedTransaction = null
                        destination = RootDestination.Home
                    },
                    onSelectAccount = {
                        selectedTransaction = null
                        destination = RootDestination.Account
                    },
                )
            }
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
                RootDestination.TransactionHistory -> {
                    val viewModel: TransactionListViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer {
                                TransactionListViewModel(
                                    userRepository = KoinPlatform.getKoin().get<UserRepository>(),
                                    sessionStore = KoinPlatform.getKoin().get<SessionStore>(),
                                )
                            }
                        },
                    )
                    TransactionListScreen(
                        viewModel = viewModel,
                        onBack = { destination = RootDestination.Home },
                        onNotificationClick = { showToast("通知機能は実装中です") },
                        onTransactionClick = { transaction ->
                            selectedTransaction = transaction
                            destination = RootDestination.TransactionDetail
                        },
                    )
                }
                RootDestination.TransactionDetail -> {
                    val tx = selectedTransaction
                    if (tx == null) {
                        // プロセス再生成等で対象 Transaction が失われた場合は履歴へ戻す。
                        // composition 中の副作用は描画 1 回ぶん遅延させたいため LaunchedEffect で実行する。
                        LaunchedEffect(Unit) {
                            destination = RootDestination.TransactionHistory
                        }
                    } else {
                        // VM key を transaction.id にして、別取引タップ時に新しい VM が生成されるようにする
                        val viewModel: TransactionDetailViewModel = viewModel(
                            key = "TransactionDetail/${tx.id}",
                            factory = viewModelFactory {
                                initializer { TransactionDetailViewModel(transaction = tx) }
                            },
                        )
                        TransactionDetailScreen(
                            viewModel = viewModel,
                            onBack = { destination = RootDestination.TransactionHistory },
                            onNotificationClick = { showToast("通知機能は実装中です") },
                        )
                    }
                }
                RootDestination.Send -> ComingSoonScreen(
                    title = "送る・もらう",
                    onBack = { destination = RootDestination.Home },
                )
            }
        }
    }
}

@Composable
private fun BottomNav(
    selected: RootDestination,
    onSelectHome: () -> Unit,
    onSelectAccount: () -> Unit,
) {
    // ホーム家族に属する画面（履歴・詳細）でもホームタブを selected 表示にする
    val homeFamily = selected == RootDestination.Home ||
        selected == RootDestination.TransactionHistory ||
        selected == RootDestination.TransactionDetail ||
        selected == RootDestination.Send
    // Figma `709:8658` 等の bottomBar: 84dp、白背景、上端に 1dp ボーダー、pt-8 px-48、
    // 2 タブが均等の weight=1 で並び、それぞれ内側 64dp の余白で中央へ寄せる
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .background(FujuBankColors.Surface)
            .border(width = 1.dp, color = FujuBankColors.BottomBarBorder)
            .padding(top = 8.dp, start = 48.dp, end = 48.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
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
}

@Composable
private fun BottomTab(
    iconRes: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tabColor = if (selected) Color.Black else FujuBankColors.TextTertiary
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = label,
            modifier = Modifier.size(32.dp),
            colorFilter = ColorFilter.tint(tabColor),
        )
        Text(
            text = label,
            maxLines = 1,
            softWrap = false,
            style = TextStyle(
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = tabColor,
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
            RootDestination.TransactionDetail -> "transactionDetail"
            RootDestination.Send -> "send"
        }
    },
    restore = { key ->
        when (key) {
            "home" -> RootDestination.Home
            "account" -> RootDestination.Account
            "transactionHistory" -> RootDestination.TransactionHistory
            // 詳細はプロセス再生成時に対象 Transaction を保持しないため、復元時は履歴に降格させる
            "transactionDetail" -> RootDestination.TransactionHistory
            "send" -> RootDestination.Send
            else -> null
        }
    },
)
