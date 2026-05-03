package studio.nxtech.fujubank.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import studio.nxtech.fujubank.features.home.components.BalanceCard
import studio.nxtech.fujubank.features.home.components.FujuBankHeader
import studio.nxtech.fujubank.features.home.components.RecentTransactionItem
import studio.nxtech.fujubank.features.home.components.RecentTransactionsSection
import studio.nxtech.fujubank.theme.FujuBankColors

/**
 * ホーム画面 — Figma `709:8658` 準拠（Android 先行）。
 *
 * - ヘッダー（左 48dp 空 / 中央 fuju 銀行 ロゴ + chevron / 右 通知ベル）
 * - 残高カード（48sp の数値 + 「ふじゅ〜」単位、QR / バーコード / マスクトグルは旧デザインから撤去）
 * - 「最近の取引履歴」セクション（モック 3 件 + もっとみる）
 *
 * ボトムナビは [studio.nxtech.fujubank.features.shell.RootScaffold] が描画する。
 *
 * 注: 取引履歴のモック表示は Figma `709:8658` の見た目を再現するための暫定。
 *     バックエンドからの最近の取引取得 API は本タスクのスコープ外（後続タスクで対応）。
 *
 * 注: `onSendReceive` / `onShowToast` は旧 ActionTiles 用のコールバック。新デザインでは
 *     画面内で発火する箇所がないが、`HomeScreen` の API シグネチャを変えない方針のため引数として残している。
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onTransactionHistory: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onSendReceive: () -> Unit,
    onShowToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(FujuBankColors.Background),
    ) {
        when (val current = state) {
            HomeUiState.Loading -> LoadingContent()
            is HomeUiState.Error -> ErrorContent(
                message = current.message,
                onRetry = viewModel::refresh,
            )
            is HomeUiState.Loaded -> LoadedContent(
                state = current,
                onTransactionHistory = onTransactionHistory,
                onNotificationClick = { onShowToast("通知機能は実装中です") },
            )
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = FujuBankColors.BrandPink)
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = FujuBankColors.TextPrimary,
            ),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = FujuBankColors.BrandPink,
                contentColor = FujuBankColors.Surface,
            ),
        ) {
            Text("再試行")
        }
    }
}

@Composable
private fun LoadedContent(
    state: HomeUiState.Loaded,
    onTransactionHistory: () -> Unit,
    onNotificationClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FujuBankHeader(onNotificationClick = onNotificationClick)
        BalanceCard(balanceFuju = state.profile.balanceFuju)
        RecentTransactionsSection(
            items = MOCK_RECENT_TRANSACTIONS,
            onMore = onTransactionHistory,
        )
    }
}

// Figma `709:8658` の見本値そのまま。バックエンド統合は後続タスクで実施。
private val MOCK_RECENT_TRANSACTIONS: List<RecentTransactionItem> = List(3) {
    RecentTransactionItem(
        title = "トマトのイラスト",
        amount = 42,
        sign = "+",
        timestamp = "2025/3/4 12:03:03",
    )
}
