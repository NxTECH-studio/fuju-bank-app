package studio.nxtech.fujubank.features.home.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.alexzhirkevich.qrose.rememberQrCodePainter

/**
 * `qrose` を使って `content` の QR コードを描画する。
 *
 * iOS の `CIFilter.qrCodeGenerator()` と概ね同じ見た目になるよう、エラー訂正レベルは
 * デフォルト（M）に揃えている。マージンや色のカスタムは Figma 確認後に調整する想定。
 */
@Composable
fun QrCodeImage(
    content: String,
    modifier: Modifier = Modifier,
) {
    val painter = rememberQrCodePainter(content)
    // contentDescription に content (publicId) を含めると TalkBack で公開 ID が
    // 読み上げられるため、固定文言にする。
    Image(
        painter = painter,
        contentDescription = "支払い用 QR コード",
        modifier = modifier,
    )
}
