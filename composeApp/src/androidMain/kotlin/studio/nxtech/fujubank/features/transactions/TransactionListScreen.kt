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
import androidx.compose.material3.HorizontalDivider
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
import studio.nxtech.fujubank.features.home.components.NotificationBellButton
import studio.nxtech.fujubank.theme.FujupayColors

/**
 * 取引履歴画面 — Figma `410:20343` 準拠。
 *
 * - ヘッダー: 戻る `<` / タイトル「取引履歴」/ 通知ベル
 * - 本文: LazyColumn + PullToRefreshBox。空状態は中央寄せ文言。
 *
 * MVP では pagination を持たず、サーバーが返す件数を全件表示する。
 * 親 (RootScaffold) のボトムナビは表示したまま、戻るで Home に復帰する。
 */
@Composable
fun TransactionListScreen(
    viewModel: TransactionListViewModel,
    onBack: () -> Unit,
    onNotificationClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FujupayColors.Background),
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
    // 横余白はホーム画面と揃えるため 16dp（HomeScreen の Column と同値）。
    // タイトルは中央寄せ、左右に 48dp の戻るボタン / 通知ベルを配置する。
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "取引履歴",
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = FujupayColors.TextPrimary,
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
        CircularProgressIndicator(color = FujupayColors.BrandPink)
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
                color = FujupayColors.TextPrimary,
            ),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = FujupayColors.BrandPink,
                contentColor = FujupayColors.Surface,
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
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.items, key = { it.id }) { transaction ->
                    TransactionRow(transaction = transaction)
                    HorizontalDivider(
                        color = FujupayColors.TransactionDivider,
                        thickness = 2.dp,
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
                color = FujupayColors.TextSecondary,
            ),
            textAlign = TextAlign.Center,
        )
    }
}
