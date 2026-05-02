package studio.nxtech.fujubank.data.repository

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PublicIdValidationTest {

    @Test
    fun acceptsAlphaNumericUnderscoreHyphen() {
        assertTrue(isValidPublicId("abc_DEF-123"))
    }

    @Test
    fun acceptsTypicalUlid() {
        assertTrue(isValidPublicId("01HX4T8K7N9P2QABC0DEF12345"))
    }

    @Test
    fun rejectsBlank() {
        assertFalse(isValidPublicId(""))
    }

    @Test
    fun rejectsUrlScheme() {
        assertFalse(isValidPublicId("https://evil.example/"))
    }

    @Test
    fun rejectsCustomScheme() {
        assertFalse(isValidPublicId("paypay://send"))
    }

    @Test
    fun rejectsControlAndPunctuation() {
        assertFalse(isValidPublicId("abc;def"))
        assertFalse(isValidPublicId("abc def"))
        assertFalse(isValidPublicId("abc<def>"))
    }

    @Test
    fun rejectsTooLong() {
        assertFalse(isValidPublicId("a".repeat(65)))
    }
}
