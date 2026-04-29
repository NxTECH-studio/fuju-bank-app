package studio.nxtech.fujubank.signup

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignupWelcomePreferencesTest {

    @Test
    fun `初期値は false`() {
        val prefs = SignupWelcomePreferences(MapSettings())
        assertFalse(prefs.signupCompleted.value)
    }

    @Test
    fun `markCompleted で true になる`() {
        val prefs = SignupWelcomePreferences(MapSettings())
        prefs.markCompleted()
        assertTrue(prefs.signupCompleted.value)
    }

    @Test
    fun `同じ Settings から再生成しても永続化されている`() {
        val backing = MapSettings()
        SignupWelcomePreferences(backing).markCompleted()

        val restored = SignupWelcomePreferences(backing)
        assertTrue(restored.signupCompleted.value)
    }

    @Test
    fun `resetForDebug で false に戻る`() {
        val backing = MapSettings()
        val prefs = SignupWelcomePreferences(backing)
        prefs.markCompleted()
        prefs.resetForDebug()
        assertFalse(prefs.signupCompleted.value)
        assertEquals(0, backing.size)
    }
}
