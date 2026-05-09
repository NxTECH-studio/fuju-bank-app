package studio.nxtech.fujubank.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import studio.nxtech.fujubank.features.home.components.BalanceCard
import studio.nxtech.fujubank.features.home.components.FujuBankHeader
import studio.nxtech.fujubank.features.home.components.RecentTransactionsSection
import studio.nxtech.fujubank.theme.FujuBankColors
import studio.nxtech.fujubank.theme.NotoSansJP

/**
 * ホーム画面 — Figma `709:8658` 準拠（Android 先行）。
 *
 * - ヘッダー（左 48dp 空 / 中央 fuju 銀行 ロゴ + chevron / 右 通知ベル）
 * - 残高カード（48sp の数値 + 「ふじゅ〜」単位、QR / バーコード / マスクトグルは旧デザインから撤去）
 * - 「最近の取引履歴」セクション（API 取得済み 3 件 + もっとみる）
 *
 * ボトムナビは [studio.nxtech.fujubank.features.shell.RootScaffold] が描画する。
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
                onRecentRetry = viewModel::refreshRecent,
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
                fontFamily = NotoSansJP,
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
    onRecentRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FujuBankHeader(onNotificationClick = onNotificationClick)
        BalanceCard(balanceFuju = state.profile.balanceFuju)
        when (val recent = state.recentTransactions) {
            RecentTransactionsState.Loading -> RecentLoadingPlaceholder()
            is RecentTransactionsState.Ready -> {
                if (recent.items.isEmpty()) {
                    RecentEmptyPlaceholder(onMore = onTransactionHistory)
                } else {
                    RecentTransactionsSection(
                        items = recent.items,
                        onMore = onTransactionHistory,
                    )
                }
            }
            is RecentTransactionsState.Error -> RecentErrorPlaceholder(
                message = recent.message,
                onRetry = onRecentRetry,
                onMore = onTransactionHistory,
            )
        }
    }
}

@Composable
private fun RecentLoadingPlaceholder() {
    // セクション内に小さい CircularProgressIndicator を出す。ready 時の縦サイズと
    // 大きく食い違わないよう、カード相当の高さ 96dp を確保する。
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeaderPlaceholder()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                color = FujuBankColors.BrandPink,
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
private fun RecentEmptyPlaceholder(onMore: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeaderPlaceholder(onMore = onMore)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 4.dp, shape = RoundedCornerShape(20.dp), clip = false)
                .clip(RoundedCornerShape(20.dp))
                .background(FujuBankColors.Surface)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "取引履歴はまだありません",
                style = TextStyle(
                    fontFamily = NotoSansJP,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = FujuBankColors.TextSecondary,
                ),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun RecentErrorPlaceholder(
    message: String,
    onRetry: () -> Unit,
    onMore: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeaderPlaceholder(onMore = onMore)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 4.dp, shape = RoundedCornerShape(20.dp), clip = false)
                .clip(RoundedCornerShape(20.dp))
                .background(FujuBankColors.Surface)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = message,
                style = TextStyle(
                    fontFamily = NotoSansJP,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = FujuBankColors.TextSecondary,
                ),
                textAlign = TextAlign.Center,
            )
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
}

/**
 * Loading / Empty / Error 共通の最近の取引セクションヘッダ。
 * `RecentTransactionsSection` 内のヘッダと見た目を揃えるため、ここに切り出す。
 */
@Composable
private fun SectionHeaderPlaceholder(onMore: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "最近の取引履歴",
            style = TextStyle(
                fontFamily = NotoSansJP,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = FujuBankColors.TextPrimary,
            ),
        )
        if (onMore != null) {
            Text(
                text = "もっとみる",
                modifier = Modifier.clickable(onClick = onMore),
                style = TextStyle(
                    fontFamily = NotoSansJP,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = FujuBankColors.LinkBlue,
                ),
            )
        }
    }
}
