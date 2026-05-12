package studio.nxtech.fujubank.format

import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/**
 * mint 取引の `metadata`（mining 側 `{n_exposures, target_date, model_version}`）を
 * UI 表示用文字列に整形する commonMain 完結のフォーマッタ。
 *
 * - `formatExposures`: 接触回数。`"$value 回"` の形式（例: `3 回`）。
 * - `formatTargetDate`: 集計対象日。`YYYY/MM/DD`（ゼロ埋め、例: `2026/05/10`）。
 *   既存 [studio.nxtech.fujubank.util.formatTransactionDateTimeSlash] は月日ゼロ埋め
 *   「しない」仕様だが、`target_date` はカレンダー表記寄りの意味合いなのでゼロ埋め採用。
 * - `formatModelVersion`: モデルバージョン文字列。現在は identity だが、将来「`dummy_test` を
 *   「デモ」と表示する」等のプロダクト判断が入ったときに集約点として残す。
 *
 * iOS 側からは `MintMetadataFormatterKt.formatExposures(value:)` 等で呼ぶ
 * （Kotlin/Native の Obj-C 公開で関数が `Kt` suffix 付きクラスに収まる）。
 */
object MintMetadataFormatter {

    fun formatExposures(value: Int): String = "$value 回"

    fun formatTargetDate(value: LocalDate): String = buildString {
        append(value.year)
        append('/')
        append(value.month.number.toString().padStart(2, '0'))
        append('/')
        append(value.day.toString().padStart(2, '0'))
    }

    fun formatModelVersion(value: String): String = value
}
