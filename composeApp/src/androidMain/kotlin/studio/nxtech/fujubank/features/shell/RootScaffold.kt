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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.koin.mp.KoinPlatform
import studio.nxtech.fujubank.R
import studio.nxtech.fujubank.account.AccountProfileProvider
import studio.nxtech.fujubank.account.NotificationSettingsPreferences
import studio.nxtech.fujubank.account.PrivacyContent
import studio.nxtech.fujubank.account.PrivacyPreferences
import studio.nxtech.fujubank.data.repository.AuthRepository
import studio.nxtech.fujubank.data.repository.LedgerRepository
import studio.nxtech.fujubank.data.repository.ProfileRepository
import studio.nxtech.fujubank.data.repository.UserRepository
import studio.nxtech.fujubank.domain.model.Transaction
import studio.nxtech.fujubank.features.account.AccountHubScreen
import studio.nxtech.fujubank.features.account.AccountHubViewModel
import studio.nxtech.fujubank.features.account.LegalDocumentScreen
import studio.nxtech.fujubank.features.account.NotificationSettingsScreen
import studio.nxtech.fujubank.features.account.NotificationSettingsViewModel
import studio.nxtech.fujubank.features.account.PasswordChangeScreen
import studio.nxtech.fujubank.features.account.PasswordChangeViewModel
import studio.nxtech.fujubank.features.account.PrivacySettingsScreen
import studio.nxtech.fujubank.features.account.PrivacySettingsViewModel
import studio.nxtech.fujubank.features.home.HomeScreen
import studio.nxtech.fujubank.features.home.HomeViewModel
import studio.nxtech.fujubank.features.send.SendAmountScreen
import studio.nxtech.fujubank.features.send.SendFlowViewModel
import studio.nxtech.fujubank.features.send.SendRecipientScreen
import studio.nxtech.fujubank.features.transactions.TransactionDetailScreen
import studio.nxtech.fujubank.features.transactions.TransactionDetailViewModel
import studio.nxtech.fujubank.features.transactions.TransactionListScreen
import studio.nxtech.fujubank.features.transactions.TransactionListViewModel
import studio.nxtech.fujubank.navigation.RootDestination
import studio.nxtech.fujubank.session.SessionState
import studio.nxtech.fujubank.session.SessionStore
import studio.nxtech.fujubank.theme.FujuBankColors
import studio.nxtech.fujubank.theme.NotoSansJP

/**
 * ログイン後のルートシェル。Scaffold の bottomBar に Figma `709:8658` / `697:7601` / `702:6440`
 * 共通のボトムナビ（白背景 / pt-8 px-48 / 84dp / 2 タブ均等配置）を描画する。
 *
 * MVP では Navigation Compose を導入せず、[RootDestination] を `rememberSaveable` で保持して切替える。
 * 取引詳細遷移時に対象の `Transaction` を別途 `remember` で保持する（Saver の実装コストを避ける）。
 */
@Composable
fun RootScaffold() {
    // 内部の各 ViewModel（Home / TransactionList / Send / AccountHub など）は **ユーザー固有**
    // のデータを `_state` に保持している。デフォルトの `LocalViewModelStoreOwner`（= Activity）
    // を使うと、別ユーザでログインし直しても Activity が生存している限り前ユーザの VM インスタンス
    // がそのまま再利用され、`viewModel(...)` の冪等性により init { load() } も再実行されないため
    // 残高や取引履歴が前ユーザの値のまま表示される（client-bank-23 で報告された残留事象）。
    //
    // ここで bankUserId を key にした専用 [ViewModelStoreOwner] を `CompositionLocalProvider`
    // で差し込み、ユーザ切替時に古い VM 群をまとめて破棄して新規生成させる。`remember(bankUserId)`
    // で owner を回し、`DisposableEffect` の onDispose で旧 store を clear することでリークも防ぐ。
    //
    // `RootScaffold` は AppRoot 側で `Authenticated` の時だけ composition に入る前提なので、
    // bankUserId が空文字になるのは観測上の最初の 1 フレームの瞬間（state の collect 前）だけ。
    // 空文字 owner が一瞬使われても、直後の recomposition で正しい owner に差し替わる。
    val sessionStore = remember { KoinPlatform.getKoin().get<SessionStore>() }
    val sessionState by sessionStore.state.collectAsStateWithLifecycle()
    val bankUserId = (sessionState as? SessionState.Authenticated)?.bankUserId.orEmpty()
    val userScopedOwner = remember(bankUserId) {
        object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(userScopedOwner) {
        onDispose { userScopedOwner.viewModelStore.clear() }
    }

    CompositionLocalProvider(LocalViewModelStoreOwner provides userScopedOwner) {
        RootScaffoldContent()
    }
}

@Composable
private fun RootScaffoldContent() {
    var destination: RootDestination by rememberSaveable(
        stateSaver = RootDestinationSaver,
    ) { mutableStateOf(RootDestination.Home) }

    // 取引詳細に遷移する際の対象。プロセス再生成時には失われ、自動で履歴へ戻す挙動になる。
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }

    // 送金完了時に Home 側 ViewModel を再生成して残高 / 取引履歴を自動 refresh するためのキー。
    // 同じ Composition / ViewModelStore のままだと HomeViewModel.init が再走しないため、
    // 送金成功時に salt をインクリメントして HomeViewModel を破棄 → 新規生成させる。
    var homeKeySalt by rememberSaveable { mutableIntStateOf(0) }
    // 送金完了時に Home 側で出す Snackbar 用メッセージ（1 回限定）。
    var pendingHomeSnackbar by remember { mutableStateOf<String?>(null) }
    val homeSnackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(pendingHomeSnackbar, destination) {
        val message = pendingHomeSnackbar
        if (message != null && destination == RootDestination.Home) {
            homeSnackbarHostState.showSnackbar(message)
            pendingHomeSnackbar = null
        }
    }

    val context = LocalContext.current
    val showToast: (String) -> Unit = { message ->
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    // フッター（ボトムナビ）はメインタブとサブ画面 (履歴/詳細) で表示する。
    // 法的文書 (プライバシーポリシー / 利用規約) は本文が長く、フッターに被って読めなくなるため非表示。
    // 送金フロー（Step1/Step2）は誤タップ防止と画面集中を優先してフッターを非表示にする。
    val showBottomBar = when (destination) {
        RootDestination.Send,
        RootDestination.SendRecipient,
        RootDestination.SendAmount,
        RootDestination.PrivacyPolicy,
        RootDestination.TermsOfService,
        RootDestination.PasswordChange -> false
        else -> true
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = FujuBankColors.Background,
        snackbarHost = { SnackbarHost(hostState = homeSnackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                BottomNav(
                    selected = destination,
                    onSelectHome = {
                        selectedTransaction = null
                        destination = RootDestination.Home
                    },
                    onSelectSend = {
                        selectedTransaction = null
                        destination = RootDestination.SendRecipient
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
                    // 送金完了から戻ったタイミングで HomeViewModel を作り直し、init で残高 / 取引履歴を再取得する。
                    val viewModel: HomeViewModel = viewModel(
                        key = "Home/$homeKeySalt",
                        factory = viewModelFactory {
                            initializer {
                                HomeViewModel(
                                    profileRepository = KoinPlatform.getKoin().get<ProfileRepository>(),
                                    userRepository = KoinPlatform.getKoin().get<UserRepository>(),
                                    sessionStore = KoinPlatform.getKoin().get<SessionStore>(),
                                )
                            }
                        },
                    )
                    HomeScreen(
                        viewModel = viewModel,
                        onTransactionHistory = { destination = RootDestination.TransactionHistory },
                        onSendReceive = { destination = RootDestination.SendRecipient },
                        onShowToast = showToast,
                    )
                }
                RootDestination.Account -> {
                    val viewModel: AccountHubViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer {
                                AccountHubViewModel(
                                    profileProvider = KoinPlatform.getKoin().get<AccountProfileProvider>(),
                                    authRepository = KoinPlatform.getKoin().get<AuthRepository>(),
                                    sessionStore = KoinPlatform.getKoin().get<SessionStore>(),
                                )
                            }
                        },
                    )
                    AccountHubScreen(
                        viewModel = viewModel,
                        onNavigateNotifications = { destination = RootDestination.NotificationSettings },
                        onNavigatePrivacy = { destination = RootDestination.PrivacySettings },
                        onNavigatePasswordChange = { destination = RootDestination.PasswordChange },
                    )
                }
                RootDestination.NotificationSettings -> {
                    val viewModel: NotificationSettingsViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer {
                                NotificationSettingsViewModel(
                                    preferences = KoinPlatform.getKoin().get<NotificationSettingsPreferences>(),
                                )
                            }
                        },
                    )
                    NotificationSettingsScreen(
                        viewModel = viewModel,
                        onBack = { destination = RootDestination.Account },
                        onNotificationClick = { showToast("通知機能は実装中です") },
                    )
                }
                RootDestination.PrivacySettings -> {
                    val viewModel: PrivacySettingsViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer {
                                PrivacySettingsViewModel(
                                    preferences = KoinPlatform.getKoin().get<PrivacyPreferences>(),
                                )
                            }
                        },
                    )
                    PrivacySettingsScreen(
                        viewModel = viewModel,
                        onBack = { destination = RootDestination.Account },
                        onPrivacyPolicyClick = { destination = RootDestination.PrivacyPolicy },
                        onTermsOfServiceClick = { destination = RootDestination.TermsOfService },
                    )
                }
                RootDestination.PrivacyPolicy -> {
                    LegalDocumentScreen(
                        title = PrivacyContent.PRIVACY_POLICY_TITLE,
                        body = PrivacyContent.PRIVACY_POLICY_BODY,
                        onBack = { destination = RootDestination.PrivacySettings },
                    )
                }
                RootDestination.TermsOfService -> {
                    LegalDocumentScreen(
                        title = PrivacyContent.TERMS_OF_SERVICE_TITLE,
                        body = PrivacyContent.TERMS_OF_SERVICE_BODY,
                        onBack = { destination = RootDestination.PrivacySettings },
                    )
                }
                RootDestination.PasswordChange -> {
                    val viewModel: PasswordChangeViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer { PasswordChangeViewModel() }
                        },
                    )
                    PasswordChangeScreen(
                        viewModel = viewModel,
                        onBack = { destination = RootDestination.Account },
                        onSuccess = { showToast("パスワードを変更しました") },
                    )
                }
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
                RootDestination.Send -> {
                    // 旧プレースホルダ destination。フッタータブの初期遷移先として SendRecipient へ即移譲。
                    LaunchedEffect(Unit) { destination = RootDestination.SendRecipient }
                }
                RootDestination.SendRecipient -> {
                    val viewModel: SendFlowViewModel = sendFlowViewModel()
                    SendRecipientScreen(
                        viewModel = viewModel,
                        onBack = { destination = RootDestination.Home },
                        onProceedToAmount = { destination = RootDestination.SendAmount },
                    )
                }
                RootDestination.SendAmount -> {
                    val viewModel: SendFlowViewModel = sendFlowViewModel()
                    SendAmountScreen(
                        viewModel = viewModel,
                        onBack = { destination = RootDestination.SendRecipient },
                        onComplete = { _, _ ->
                            // 送金完了 → ホーム遷移時に Snackbar 表示 + HomeViewModel 再生成で残高再 fetch。
                            pendingHomeSnackbar = "送金しました"
                            homeKeySalt += 1
                            destination = RootDestination.Home
                        },
                    )
                }
            }
        }
    }
}

/**
 * 送金フローの 2 画面（[SendRecipientScreen] / [SendAmountScreen]）で **同一 VM** を共有する。
 * `key = "SendFlow"` を固定にすることで Step 1 → Step 2 → Step 1 と切替えても state を保持する。
 */
@Composable
private fun sendFlowViewModel(): SendFlowViewModel = viewModel(
    key = "SendFlow",
    factory = viewModelFactory {
        initializer {
            SendFlowViewModel(
                userRepository = KoinPlatform.getKoin().get<UserRepository>(),
                ledgerRepository = KoinPlatform.getKoin().get<LedgerRepository>(),
                profileRepository = KoinPlatform.getKoin().get<ProfileRepository>(),
                sessionStore = KoinPlatform.getKoin().get<SessionStore>(),
            )
        }
    },
)

@Composable
private fun BottomNav(
    selected: RootDestination,
    onSelectHome: () -> Unit,
    onSelectSend: () -> Unit,
    onSelectAccount: () -> Unit,
) {
    // ホーム家族に属する画面（履歴・詳細）でもホームタブを selected 表示にする
    val homeFamily = selected == RootDestination.Home ||
        selected == RootDestination.TransactionHistory ||
        selected == RootDestination.TransactionDetail
    // 送金家族（フッターは送金フロー中は非表示なので selected 判定はタブから入った直後のみ意味を持つ）
    val sendFamily = selected == RootDestination.Send ||
        selected == RootDestination.SendRecipient ||
        selected == RootDestination.SendAmount
    // アカウント家族に属する画面（通知設定・準備中サブ画面）でもアカウントタブを selected 表示にする
    val accountFamily = selected == RootDestination.Account ||
        selected == RootDestination.NotificationSettings ||
        selected == RootDestination.PrivacySettings ||
        selected == RootDestination.PrivacyPolicy ||
        selected == RootDestination.TermsOfService ||
        selected == RootDestination.PasswordChange
    // Figma `709:8658` 等の bottomBar: 84dp、白背景、上端に 1dp ボーダー、pt-8 px-48。
    // client-bank-22 で 3 タブに拡張。中央に「送金」タブを差し込み、3 列均等 weight=1 で並べる。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .background(FujuBankColors.Surface)
            .border(width = 1.dp, color = FujuBankColors.BottomBarBorder)
            .padding(top = 8.dp, start = 24.dp, end = 24.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            BottomTab(
                iconRes = R.drawable.ic_home,
                label = "ホーム",
                selected = homeFamily,
                onClick = onSelectHome,
            )
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            BottomTab(
                iconRes = R.drawable.ic_send,
                label = "送金",
                selected = sendFamily,
                onClick = onSelectSend,
            )
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            BottomTab(
                iconRes = R.drawable.ic_account_circle,
                label = "アカウント",
                selected = accountFamily,
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
                fontFamily = NotoSansJP,
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
            RootDestination.SendRecipient -> "sendRecipient"
            RootDestination.SendAmount -> "sendAmount"
            RootDestination.NotificationSettings -> "notificationSettings"
            RootDestination.PrivacySettings -> "privacySettings"
            RootDestination.PrivacyPolicy -> "privacyPolicy"
            RootDestination.TermsOfService -> "termsOfService"
            RootDestination.PasswordChange -> "passwordChange"
        }
    },
    restore = { key ->
        when (key) {
            "home" -> RootDestination.Home
            "account" -> RootDestination.Account
            "transactionHistory" -> RootDestination.TransactionHistory
            // 詳細はプロセス再生成時に対象 Transaction を保持しないため、復元時は履歴に降格させる
            "transactionDetail" -> RootDestination.TransactionHistory
            "send" -> RootDestination.SendRecipient
            "sendRecipient" -> RootDestination.SendRecipient
            // SendAmount は recipient state を ViewModel で持っており、プロセス再生成で
            // recipient が失われるため、復元時は Step 1 (SendRecipient) に降格させる。
            "sendAmount" -> RootDestination.SendRecipient
            "notificationSettings" -> RootDestination.NotificationSettings
            "privacySettings" -> RootDestination.PrivacySettings
            "privacyPolicy" -> RootDestination.PrivacyPolicy
            "termsOfService" -> RootDestination.TermsOfService
            "passwordChange" -> RootDestination.PasswordChange
            // client-bank-10 で削除した accountEdit キーは Account タブに降格させる
            "accountEdit" -> RootDestination.Account
            else -> null
        }
    },
)
