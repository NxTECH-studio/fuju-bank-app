package studio.nxtech.fujubank.di

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedModuleTest {

    @Test
    fun sharedModulesAggregatesAllFeatureModules() {
        val modules = sharedModules(cableUrl = "wss://example.test/cable")
        assertEquals(5, modules.size)
    }
}
