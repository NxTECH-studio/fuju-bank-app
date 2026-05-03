package studio.nxtech.fujubank.features.transactions

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import studio.nxtech.fujubank.R
import studio.nxtech.fujubank.domain.model.Transaction
import studio.nxtech.fujubank.domain.model.TransactionDirection
import studio.nxtech.fujubank.format.CurrencyFormatter
import studio.nxtech.fujubank.theme.FujuBankColors
import studio.nxtech.fujubank.theme.NotoSansJP
import studio.nxtech.fujubank.util.formatTransactionDateTimeSlash

/**
 * 取引履歴の 1 行 — Figma `697:7601` 準拠。
 *
 * - 白カード（角丸 0、影 drop-shadow）。LazyColumn で `Arrangement.spacedBy(2.dp)` を想定
 * - 上段: 54dp アバター（白背景 + 中央 32dp X ロゴ） + タイトル + サブタイトル
 * - 下段: 左に日時（yyyy/M/d HH:mm:ss）、右にピンクの金額（`+42 ふじゅ〜` / `-...`）
 *
 * バックエンドからアーティファクト画像を取れる仕組みが整うまでは X ロゴで仮置きし、画像取得後に
 * 「画像 + 左上 X バッジ」バリアントを差し込めるようにこの 1 ファイルにまとめておく。
 *
 * サブタイトル `18秒みつめられた` は Figma 上の固定文。視線データ統合は後続タスクで対応。
 */
@Composable
fun TransactionRow(
    transaction: Transaction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val variant = TransactionRowVariant.from(transaction)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 4.dp, clip = false)
            .background(FujuBankColors.Surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                ArtifactAvatar()
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(
                        text = variant.title,
                        style = TextStyle(
                            fontFamily = NotoSansJP,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FujuBankColors.TextPrimary,
                        ),
                    )
                    Text(
                        text = TRANSACTION_ROW_SUBTITLE_PLACEHOLDER,
                        style = TextStyle(
                            fontFamily = NotoSansJP,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = FujuBankColors.TextSecondary,
                        ),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTransactionDateTimeSlash(transaction.occurredAt),
                modifier = Modifier.weight(1f),
                style = TextStyle(
                    fontFamily = NotoSansJP,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = FujuBankColors.TextSecondary,
                ),
            )
            Text(
                text = "${variant.sign}${CurrencyFormatter.formatAmount(transaction.amount)} ${CurrencyFormatter.UNIT}",
                style = TextStyle(
                    fontFamily = NotoSansJP,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = variant.amountColor,
                ),
            )
        }
    }
}

/**
 * 54dp の白角丸ボックスに 32dp の X ロゴを中央配置したアーティファクトプレースホルダ。
 * Figma `697:7601` の Credit 9/10/11 等の「画像が無いアーティファクト」表示にあたる。
 */
@Composable
private fun ArtifactAvatar() {
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(FujuBankColors.Surface),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_x_logo),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
        )
    }
}

private data class TransactionRowVariant(
    val title: String,
    val sign: String,
    val amountColor: Color,
) {
    companion object {
        fun from(transaction: Transaction): TransactionRowVariant = when (transaction.direction) {
            TransactionDirection.Mint -> TransactionRowVariant(
                title = transaction.artifactId?.let { "アーティファクト ${it.takeLast(SHORT_ID_LEN)}" } ?: "発行",
                sign = "+",
                amountColor = FujuBankColors.BrandPink,
            )
            TransactionDirection.Incoming -> TransactionRowVariant(
                title = transaction.counterpartyUserId
                    ?.let { "${it.takeLast(SHORT_ID_LEN)} からもらいました" }
                    ?: "入金",
                sign = "+",
                amountColor = FujuBankColors.BrandPink,
            )
            TransactionDirection.Outgoing -> TransactionRowVariant(
                title = transaction.counterpartyUserId
                    ?.let { "${it.takeLast(SHORT_ID_LEN)} に送りました" }
                    ?: "送金",
                sign = "-",
                amountColor = FujuBankColors.TextPrimary,
            )
        }
    }
}

