package studio.nxtech.fujubank.features.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.nxtech.fujubank.domain.model.Transaction
import studio.nxtech.fujubank.domain.model.TransactionDirection
import studio.nxtech.fujubank.format.CurrencyFormatter
import studio.nxtech.fujubank.theme.FujuBankColors
import studio.nxtech.fujubank.util.formatTransactionDate

/**
 * 取引履歴の 1 行 — Figma `410:20343` 準拠。
 *
 * - 50dp 円形アイコン（暫定プレースホルダ。アイコン解決は将来対応）
 * - タイトル（取引相手 or 「発行」）と日時を 1 段目に並べる
 * - 下段に金額を 32sp で大きく表示し、`+` / `-` 記号と「ふじゅ〜」16sp サフィックスを付す
 *
 * 金額の色:
 * - Mint / Incoming: 緑（#0CD80C）— 残高プラス。
 * - Outgoing: 黒（#111111）— 残高マイナス。
 */
@Composable
fun TransactionRow(
    transaction: Transaction,
    modifier: Modifier = Modifier,
) {
    val variant = TransactionRowVariant.from(transaction)
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Avatar(color = variant.avatarColor)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = variant.title,
                        modifier = Modifier.weight(1f),
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = FujuBankColors.TextPrimary,
                        ),
                    )
                    Text(
                        text = formatTransactionDate(transaction.occurredAt),
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FujuBankColors.TransactionMeta,
                        ),
                    )
                }
                if (variant.subtitle != null) {
                    Text(
                        text = variant.subtitle,
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FujuBankColors.TransactionMeta,
                        ),
                    )
                }
            }
        }
        AmountText(sign = variant.sign, amount = transaction.amount, color = variant.amountColor)
    }
}

@Composable
private fun Avatar(color: Color) {
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun AmountText(sign: String, amount: Long, color: Color) {
    // sign / amount が変わらなければ AnnotatedString を再計算しない。
    val display: AnnotatedString = remember(sign, amount) {
        buildAnnotatedString {
            withStyle(SpanStyle(fontSize = 32.sp)) {
                append(sign)
                append(CurrencyFormatter.formatAmount(amount))
            }
            withStyle(SpanStyle(fontSize = 16.sp)) {
                append(CurrencyFormatter.UNIT)
            }
        }
    }
    Text(
        text = display,
        style = TextStyle(
            fontWeight = FontWeight.Bold,
            color = color,
        ),
    )
}

private data class TransactionRowVariant(
    val title: String,
    val subtitle: String?,
    val sign: String,
    val amountColor: Color,
    val avatarColor: Color,
) {
    companion object {
        fun from(transaction: Transaction): TransactionRowVariant = when (transaction.direction) {
            TransactionDirection.Mint -> TransactionRowVariant(
                title = "発行",
                subtitle = transaction.artifactId?.let { "アーティファクト ${it.takeLast(SHORT_ID_LEN)}" },
                sign = "+",
                amountColor = FujuBankColors.ActionGreen,
                avatarColor = FujuBankColors.AvatarArtifact,
            )
            TransactionDirection.Incoming -> {
                val from = transaction.counterpartyUserId?.takeLast(SHORT_ID_LEN) ?: "相手"
                TransactionRowVariant(
                    title = "${from}からもらいました",
                    subtitle = null,
                    sign = "+",
                    amountColor = FujuBankColors.ActionGreen,
                    avatarColor = FujuBankColors.AvatarPerson,
                )
            }
            TransactionDirection.Outgoing -> {
                val to = transaction.counterpartyUserId?.takeLast(SHORT_ID_LEN) ?: "相手"
                TransactionRowVariant(
                    title = "${to}に送りました",
                    subtitle = null,
                    sign = "-",
                    amountColor = FujuBankColors.TextPrimary,
                    avatarColor = FujuBankColors.AvatarPerson,
                )
            }
        }
    }
}

// 表示優先度の暫定対処: 取引相手やアーティファクトの名前解決 API が無いため、
// 末尾 6 文字に縮めて表示する。本番では UserApi / ArtifactRepository で解決する想定。
private const val SHORT_ID_LEN = 6
