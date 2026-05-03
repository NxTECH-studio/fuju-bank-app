package studio.nxtech.fujubank.features.transactions

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import studio.nxtech.fujubank.R
import studio.nxtech.fujubank.domain.model.Transaction
import studio.nxtech.fujubank.features.home.components.NotificationBellButton
import studio.nxtech.fujubank.theme.FujuBankColors

/**
 * 取引履歴画面 — Figma `697:7601` 準拠。
 *
 * - ヘッダー: 戻る `<` (左 48dp) / タイトル「取引履歴」(中央 17sp Bold) / 通知ベル (右 48dp)
 * - 本文: LazyColumn + PullToRefreshBox。各カードは [TransactionRow]、間隔 2dp。
 *
 * 親 (RootScaffold) のボトムナビは表示したまま、戻るで Home に復帰する。
 */
@Composable
fun TransactionListScreen(
    viewModel: TransactionListViewModel,
    onBack: () -> Unit,
    onNotificationClick: () -> Unit,
    onTransactionClick: (Transaction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FujuBankColors.Background),
    ) {
        Header(
            onBack = onBack,
            onNotificationClick = onNotificationClick,
        )
        Box(modifier = Modifier.fillMaxSize()) {
            when (val current = state) {
                TransactionListUiState.Loading -> LoadingContent()
                is TransactionListUiState.Error -> ErrorContent(
                    message = current.message,
                    onRetry = viewModel::refresh,
                )
                is TransactionListUiState.Loaded -> LoadedContent(
                    state = current,
                    onRefresh = viewModel::refresh,
                    onTransactionClick = onTransactionClick,
                )
            }
        }
    }
}

@Composable
private fun Header(
    onBack: () -> Unit,
    onNotificationClick: () -> Unit,
) {
    // Figma 697:7601 contents wrapper の p-10 に合わせて水平・垂直 10dp。
    // タイトルは中央寄せ、左右に 48dp の戻るボタン / 通知ベル。
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "取引履歴",
            style = TextStyle(
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = FujuBankColors.TextPrimary,
            ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_chevron_left),
                contentDescription = "戻る",
                modifier = Modifier.size(24.dp),
            )
        }
        NotificationBellButton(
            onClick = onNotificationClick,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = FujuBankColors.BrandPink)
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
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
    state: TransactionListUiState.Loaded,
    onRefresh: () -> Unit,
    onTransactionClick: (Transaction) -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (state.items.isEmpty()) {
            EmptyContent()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(state.items, key = { it.id }) { transaction ->
                    TransactionRow(
                        transaction = transaction,
                        onClick = { onTransactionClick(transaction) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "まだ取引がありません",
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = FujuBankColors.TextSecondary,
            ),
            textAlign = TextAlign.Center,
        )
    }
}
