package studio.nxtech.fujubank.format

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class MintMetadataFormatterTest {

    @Test
    fun formatExposures_appends_suffix() {
        assertEquals("0 回", MintMetadataFormatter.formatExposures(0))
        assertEquals("3 回", MintMetadataFormatter.formatExposures(3))
        assertEquals("120 回", MintMetadataFormatter.formatExposures(120))
    }

    @Test
    fun formatTargetDate_zero_pads_month_and_day() {
        // 月日のゼロ埋め `YYYY/MM/DD`（既存 `formatTransactionDateTimeSlash` とは別仕様）。
        assertEquals("2026/05/10", MintMetadataFormatter.formatTargetDate(LocalDate(2026, 5, 10)))
        assertEquals("2026/01/01", MintMetadataFormatter.formatTargetDate(LocalDate(2026, 1, 1)))
        assertEquals("2026/12/31", MintMetadataFormatter.formatTargetDate(LocalDate(2026, 12, 31)))
    }

    @Test
    fun formatModelVersion_passes_through_string() {
        // 現状 identity だが、将来「`dummy_test` を非表示にする」等の判断が入った場合の
        // 集約点として関数化を維持する。
        assertEquals("dummy_test", MintMetadataFormatter.formatModelVersion("dummy_test"))
        assertEquals("v_2026-04-26", MintMetadataFormatter.formatModelVersion("v_2026-04-26"))
    }
}
