package studio.nxtech.fujubank.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import studio.nxtech.fujubank.features.home.components.ActionTiles
import studio.nxtech.fujubank.features.home.components.BalanceCard
import studio.nxtech.fujubank.features.home.components.FujuBankHeader
import studio.nxtech.fujubank.theme.FujuBankColors

/**
 * ホーム画面 — Figma `89:12356` 準拠。
 *
 * - ヘッダー（fujupay ロゴ + 通知ベル）
 * - バーコード / QR / 残高カード（マスク表示トグル付き）
 * - 取引メニュー見出し + 4 アクション
 *
 * ボトムナビは [studio.nxtech.fujubank.features.shell.RootScaffold] が描画する。
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onTransactionHistory: () -> Unit,
    onSendReceive: () -> Unit,
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
                onToggleReveal = viewModel::toggleReveal,
                onTransactionHistory = onTransactionHistory,
                onSendReceive = onSendReceive,
                onScan = { onShowToast("スキャン機能は実装中です") },
                onCharge = { onShowToast("チャージ機能は実装中です") },
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
    onToggleReveal: () -> Unit,
    onTransactionHistory: () -> Unit,
    onSendReceive: () -> Unit,
    onScan: () -> Unit,
    onCharge: () -> Unit,
    onNotificationClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FujuBankHeader(
            onNotificationClick = onNotificationClick,
            modifier = Modifier.padding(top = 8.dp),
        )
        BalanceCard(
            publicId = state.profile.publicId,
            balanceFuju = state.profile.balanceFuju,
            revealed = state.revealed,
            onToggleReveal = onToggleReveal,
        )
        Text(
            text = "取引メニュー",
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = FujuBankColors.TextSecondary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, start = 8.dp),
        )
        ActionTiles(
            onTransactionHistory = onTransactionHistory,
            onSendReceive = onSendReceive,
            onScan = onScan,
            onCharge = onCharge,
        )
    }
}
