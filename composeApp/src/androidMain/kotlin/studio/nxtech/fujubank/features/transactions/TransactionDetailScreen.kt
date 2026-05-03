package studio.nxtech.fujubank.features.transactions

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import studio.nxtech.fujubank.R
import studio.nxtech.fujubank.domain.model.Transaction
import studio.nxtech.fujubank.domain.model.TransactionDirection
import studio.nxtech.fujubank.features.home.components.NotificationBellButton
import studio.nxtech.fujubank.format.CurrencyFormatter
import studio.nxtech.fujubank.theme.FujuBankColors
import studio.nxtech.fujubank.util.formatTransactionDateTimeSlash

/**
 * 取引詳細画面 — Figma `702:6440` 準拠。
 *
 * 構成:
 * - ヘッダー: 戻る `<` / 「取引詳細」/ 通知ベル
 * - 大金額カード: 「+342,535 ふじゅ〜」をピンクで大きく表示（h=110、rounded 32）
 * - 取引行カード: アーティファクトアバター + タイトル/サブタイトル + 左上 X バッジ + 日時
 * - 感情データカード: 「感情データ (metadata)」見出し + 滞留時間 / 視線強度 の 2 行（暫定モック値）
 *
 * 感情データの値（`18 秒` / `0.94`）は Figma 上のモックそのまま。バックエンド統合は後続タスク。
 */
@Composable
fun TransactionDetailScreen(
    viewModel: TransactionDetailViewModel,
    onBack: () -> Unit,
    onNotificationClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FujuBankColors.Background),
    ) {
        Header(onBack = onBack, onNotificationClick = onNotificationClick)
        when (val current = state) {
            is TransactionDetailUiState.Loaded -> LoadedContent(transaction = current.transaction)
        }
    }
}

@Composable
private fun Header(onBack: () -> Unit, onNotificationClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "取引詳細",
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
private fun LoadedContent(transaction: Transaction) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        AmountCard(transaction = transaction)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DetailTransactionRow(transaction = transaction)
            EmotionMetadataCard()
        }
    }
}

@Composable
private fun AmountCard(transaction: Transaction) {
    val sign: String
    val amountColor: Color
    when (transaction.direction) {
        TransactionDirection.Mint, TransactionDirection.Incoming -> {
            sign = "+"
            amountColor = FujuBankColors.BrandPink
        }
        TransactionDirection.Outgoing -> {
            sign = "-"
            amountColor = FujuBankColors.TextPrimary
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(32.dp), clip = false)
            .clip(RoundedCornerShape(32.dp))
            .background(FujuBankColors.Surface)
            .padding(horizontal = 36.dp)
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        // 「+」/「-」記号 (40sp) は数値 (48sp) の行高内で中央に揃える。
        // 数値・単位は底揃え、記号だけ Modifier.align(CenterVertically) で行内中央に上書きする。
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = sign,
                modifier = Modifier.align(Alignment.CenterVertically),
                style = TextStyle(
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = amountColor,
                ),
            )
            Text(
                text = CurrencyFormatter.formatAmount(transaction.amount),
                style = TextStyle(
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = amountColor,
                ),
            )
            Text(
                text = CurrencyFormatter.UNIT,
                modifier = Modifier.padding(bottom = 6.dp),
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = amountColor,
                ),
            )
        }
    }
}

@Composable
private fun DetailTransactionRow(transaction: Transaction) {
    val title = when (transaction.direction) {
        TransactionDirection.Mint -> transaction.artifactId
            ?.let { "アーティファクト ${it.takeLast(SHORT_ID_LEN)}" }
            ?: "発行"
        TransactionDirection.Incoming -> transaction.counterpartyUserId
            ?.let { "${it.takeLast(SHORT_ID_LEN)} からもらいました" }
            ?: "入金"
        TransactionDirection.Outgoing -> transaction.counterpartyUserId
            ?.let { "${it.takeLast(SHORT_ID_LEN)} に送りました" }
            ?: "送金"
    }
    // Figma `702:6440` 準拠: アバター = アーティファクト画像、左上 X バッジ = SNS 出典 (X) という
    // 役割分担。アーティファクト画像取得は将来タスクのため、アバターは `AvatarArtifact` 色のプレースホルダで仮置きし、
    // SNS 出典バッジは Figma 通り左上 8dp に絶対配置で描画する。
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 4.dp, clip = false)
            .background(FujuBankColors.Surface)
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(FujuBankColors.AvatarArtifact),
                )
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = title,
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FujuBankColors.TextPrimary,
                        ),
                    )
                    Text(
                        text = "18秒みつめられた",
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = FujuBankColors.TextSecondary,
                        ),
                    )
                }
            }
            Text(
                text = formatTransactionDateTimeSlash(transaction.occurredAt),
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = FujuBankColors.TextSecondary,
                ),
            )
        }
        // SNS 出典バッジ (X)。Figma の `absolute left-8 top-8` を Box の TopStart 配置で再現。
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(24.dp)
                .clip(RoundedCornerShape(4.5.dp))
                .background(FujuBankColors.Surface)
                .border(
                    width = 1.dp,
                    color = FujuBankColors.Hairline,
                    shape = RoundedCornerShape(4.5.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_x_logo),
                contentDescription = "X (旧 Twitter)",
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun EmotionMetadataCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(FujuBankColors.Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "感情データ (metadata)",
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = FujuBankColors.TextPrimary,
            ),
        )
        EmotionMetadataRow(label = "滞留時間", value = "18 秒")
        EmotionMetadataRow(label = "視線強度", value = "0.94")
    }
}

@Composable
private fun EmotionMetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = FujuBankColors.TextTertiary,
            ),
        )
        Text(
            text = value,
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = FujuBankColors.TextPrimary,
            ),
        )
    }
}

private const val SHORT_ID_LEN = 6
