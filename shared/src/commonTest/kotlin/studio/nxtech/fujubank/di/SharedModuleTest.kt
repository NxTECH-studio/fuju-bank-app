package studio.nxtech.fujubank.di

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SharedModuleTest {

    private val cableUrl = "wss://example.test/cable"

    @Test
    fun sharedModulesIncludesAllFeatureModules() {
        val modules = sharedModules(cableUrl)
        // realtimeModule は毎回新しいインスタンスになるため、authModule など val の
        // feature module は identity で、realtimeModule は存在数で確認する。
        assertTrue(authModule in modules)
        assertTrue(userModule in modules)
        assertTrue(ledgerModule in modules)
        assertTrue(artifactModule in modules)
        assertTrue(signupModule in modules)
        val staticModules = setOf(authModule, userModule, ledgerModule, artifactModule, signupModule)
        assertTrue(
            modules.any { it !in staticModules },
            "realtimeModule (dynamic) should also be included",
        )
    }

    @Test
    fun initKoinRejectsNonWebSocketCableUrl() {
        assertFailsWith<IllegalArgumentException> {
            initKoin(cableUrl = "https://example.test/cable")
        }
    }
}
