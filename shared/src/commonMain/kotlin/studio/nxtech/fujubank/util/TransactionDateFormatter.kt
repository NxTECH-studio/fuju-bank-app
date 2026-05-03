package studio.nxtech.fujubank.util

import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * 取引履歴の発生日時を Figma 準拠の文字列に整形する。
 *
 * - 表記: `M月d日 HH時mm分`（例: `12月13日 12時24分`）。
 * - タイムゾーン: バックエンドは UTC で `occurred_at` を返し、UI はユーザーのローカル時刻を
 *   表示する想定。海外ローミングや TZ 詐称端末を考えると `Asia/Tokyo` 固定にするより
 *   `TimeZone.currentSystemDefault()` の方が無難。
 *
 * commonMain では `LocalizedDateFormatter` 等のロケール依存 API が使えないため、
 * 純粋なロジックで組み立てる。
 */
fun formatTransactionDate(
    instant: Instant,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val ldt = instant.toLocalDateTime(timeZone)
    return buildString {
        append(ldt.month.number)
        append("月")
        append(ldt.day)
        append("日 ")
        append(ldt.hour.toString().padStart(2, '0'))
        append("時")
        append(ldt.minute.toString().padStart(2, '0'))
        append("分")
    }
}

/**
 * Figma 銀行版（`697:7601` / `702:6440`）準拠の取引日時表記:
 * `yyyy/M/d HH:mm:ss`（例: `2025/3/4 12:03:03`）。
 *
 * 月・日はゼロ埋めしないが、時・分・秒は 2 桁ゼロ埋め。
 */
fun formatTransactionDateTimeSlash(
    instant: Instant,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val ldt = instant.toLocalDateTime(timeZone)
    return buildString {
        append(ldt.year)
        append('/')
        append(ldt.month.number)
        append('/')
        append(ldt.day)
        append(' ')
        append(ldt.hour.toString().padStart(2, '0'))
        append(':')
        append(ldt.minute.toString().padStart(2, '0'))
        append(':')
        append(ldt.second.toString().padStart(2, '0'))
    }
}
