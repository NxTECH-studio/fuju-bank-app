package studio.nxtech.fujubank.features.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.nxtech.fujubank.R
import studio.nxtech.fujubank.format.CurrencyFormatter
import studio.nxtech.fujubank.theme.FujuBankColors
import studio.nxtech.fujubank.theme.NotoSansJP

/**
 * ホーム画面の「最近の取引履歴」セクション — Figma `709:8658` 準拠。
 *
 * セクションヘッダー（タイトル + もっとみる）と、白背景・角丸 20dp の取引カード 3 枚を縦に並べる。
 * バックエンド連携は本タスクのスコープ外のため、表示するアイテムは呼び出し側からモックを渡す。
 */
@Composable
fun RecentTransactionsSection(
    items: List<RecentTransactionItem>,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader(onMore = onMore)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items.forEach { item ->
                RecentTransactionCard(item = item)
            }
        }
    }
}

@Composable
private fun SectionHeader(onMore: () -> Unit) {
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
        Row(
            modifier = Modifier.clickable(onClick = onMore),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "もっとみる",
                style = TextStyle(
                    fontFamily = NotoSansJP,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = FujuBankColors.LinkBlue,
                ),
            )
            Image(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                colorFilter = ColorFilter.tint(FujuBankColors.LinkBlue),
            )
        }
    }
}

@Composable
private fun RecentTransactionCard(item: RecentTransactionItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(20.dp),
                clip = false,
            )
            .clip(RoundedCornerShape(20.dp))
            .background(FujuBankColors.Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = item.title,
                style = TextStyle(
                    fontFamily = NotoSansJP,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FujuBankColors.TextPrimary,
                ),
            )
            Text(
                text = "${item.sign}${CurrencyFormatter.formatAmount(item.amount)} ${CurrencyFormatter.UNIT}",
                style = TextStyle(
                    fontFamily = NotoSansJP,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = FujuBankColors.BrandPink,
                ),
            )
        }
        Text(
            text = item.timestamp,
            modifier = Modifier.fillMaxWidth(),
            style = TextStyle(
                fontFamily = NotoSansJP,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = FujuBankColors.TextSecondary,
            ),
        )
    }
}

/**
 * ホーム画面で表示する「最近の取引履歴」1 件分の表示モデル。
 * バックエンド統合前のため、文字列とサインを呼び出し側で組み立ててそのまま流し込む形に留める。
 */
data class RecentTransactionItem(
    val title: String,
    val amount: Long,
    val sign: String,
    val timestamp: String,
)
