package studio.nxtech.fujubank.util

import kotlin.test.Test
import kotlin.test.assertEquals

class BalanceFormatterTest {

    @Test
    fun formatsZero() {
        assertEquals("0", formatBalanceFuju(0))
    }

    @Test
    fun formatsThreeDigitsWithoutComma() {
        assertEquals("123", formatBalanceFuju(123))
    }

    @Test
    fun formatsFourDigitsWithComma() {
        assertEquals("1,234", formatBalanceFuju(1234))
    }

    @Test
    fun formatsSevenDigitsAsExampleInDoc() {
        assertEquals("1,234,567", formatBalanceFuju(1_234_567))
    }

    @Test
    fun formatsLargeValue() {
        assertEquals("1,000,000,000,000", formatBalanceFuju(1_000_000_000_000L))
    }

    @Test
    fun formatsNegativeValue() {
        assertEquals("-1,234", formatBalanceFuju(-1234))
    }

    @Test
    fun maskedBalanceIsFixedString() {
        assertEquals("--,---,---,---", MASKED_BALANCE)
    }
}
