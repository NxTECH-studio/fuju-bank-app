package studio.nxtech.fujubank.format

import kotlin.test.Test
import kotlin.test.assertEquals

class CurrencyFormatterTest {

    @Test
    fun formatsZero() {
        assertEquals("0 ふじゅ〜", CurrencyFormatter.formatFujus(0))
    }

    @Test
    fun formatsHundred() {
        assertEquals("100 ふじゅ〜", CurrencyFormatter.formatFujus(100))
    }

    @Test
    fun formatsThreeDigitsBoundary() {
        assertEquals("999 ふじゅ〜", CurrencyFormatter.formatFujus(999))
    }

    @Test
    fun formatsFourDigitsWithComma() {
        assertEquals("1,000 ふじゅ〜", CurrencyFormatter.formatFujus(1_000))
    }

    @Test
    fun formatsSevenDigits() {
        assertEquals("1,234,567 ふじゅ〜", CurrencyFormatter.formatFujus(1_234_567))
    }

    @Test
    fun formatsNegativeValue() {
        assertEquals("-1,234 ふじゅ〜", CurrencyFormatter.formatFujus(-1_234))
    }

    @Test
    fun formatAmountReturnsNumberOnly() {
        // 数値部分のみが必要な UI（残高カード / 取引履歴行）の経路を検証する。
        assertEquals("1,234,567", CurrencyFormatter.formatAmount(1_234_567))
        assertEquals("-1,234", CurrencyFormatter.formatAmount(-1_234))
    }
}
