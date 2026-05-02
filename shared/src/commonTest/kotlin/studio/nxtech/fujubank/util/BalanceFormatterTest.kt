package studio.nxtech.fujubank.util

import kotlin.test.Test
import kotlin.test.assertEquals

class BalanceFormatterTest {

    @Test
    fun maskedBalanceIsFixedString() {
        assertEquals("--,---,---,---", MASKED_BALANCE)
    }

    @Test
    fun maskedBalanceFunctionMatchesConstant() {
        assertEquals(MASKED_BALANCE, maskedBalance())
    }
}
