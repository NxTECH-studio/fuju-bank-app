package studio.nxtech.fujubank.features.account

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.nxtech.fujubank.R
import studio.nxtech.fujubank.theme.FujuBankColors
import studio.nxtech.fujubank.theme.NotoSansJP

/**
 * プライバシーポリシー / 利用規約など、shared `PrivacyContent` で凍結された法的文書本文を
 * アプリ内で表示する汎用画面。Figma `798:12559` の「法的情報」セクションのドリルダウン先。
 *
 * ヘッダーは [PrivacySettingsScreen] / [NotificationSettingsScreen] と同じパターン
 * （戻る `<` + 中央タイトル 17sp Bold）。本文は白カード内にスクロール表示する。
 */
@Composable
fun LegalDocumentScreen(
    title: String,
    body: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FujuBankColors.Background),
    ) {
        Header(title = title, onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 4.dp,
                        shape = RoundedCornerShape(20.dp),
                        clip = false,
                    )
                    .clip(RoundedCornerShape(20.dp))
                    .background(FujuBankColors.Surface)
                    .padding(horizontal = 18.dp, vertical = 20.dp),
            ) {
                Text(
                    text = body,
                    style = TextStyle(
                        fontFamily = NotoSansJP,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 22.sp,
                        color = FujuBankColors.TextPrimary,
                    ),
                )
            }
        }
    }
}

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = TextStyle(
                fontFamily = NotoSansJP,
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
                .clickable(role = Role.Button, onClick = onBack)
                .semantics { contentDescription = "戻る" },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_chevron_left),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
