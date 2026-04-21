package studio.nxtech.fujubank.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals

class ApiErrorCodeTest {

    @Test
    fun fromString_returns_matching_enum() {
        assertEquals(ApiErrorCode.VALIDATION_FAILED, ApiErrorCode.fromString("VALIDATION_FAILED"))
        assertEquals(ApiErrorCode.MFA_REQUIRED, ApiErrorCode.fromString("MFA_REQUIRED"))
        assertEquals(ApiErrorCode.INSUFFICIENT_BALANCE, ApiErrorCode.fromString("INSUFFICIENT_BALANCE"))
    }

    @Test
    fun fromString_returns_unknown_for_null() {
        assertEquals(ApiErrorCode.UNKNOWN, ApiErrorCode.fromString(null))
    }

    @Test
    fun fromString_returns_unknown_for_unmapped_value() {
        assertEquals(ApiErrorCode.UNKNOWN, ApiErrorCode.fromString("NOT_A_REAL_CODE"))
        assertEquals(ApiErrorCode.UNKNOWN, ApiErrorCode.fromString(""))
    }

    @Test
    fun fromString_is_case_sensitive() {
        assertEquals(ApiErrorCode.UNKNOWN, ApiErrorCode.fromString("validation_failed"))
    }
}
