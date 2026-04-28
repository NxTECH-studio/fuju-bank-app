package studio.nxtech.fujubank.di

import io.ktor.client.HttpClient
import kotlin.test.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify
import studio.nxtech.fujubank.auth.PersistentCookiesStorageFactory
import studio.nxtech.fujubank.auth.TokenStorageFactory

class SharedModuleVerifyTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun sharedModulesHaveNoUnresolvedDependencies() {
        val modules = sharedModules(cableUrl = "wss://example.test/cable")
        // HttpClient / TokenStorageFactory / PersistentCookiesStorageFactory は
        // プラットフォーム側で登録されるため、ここでは external dependency として扱う。
        val extraTypes = listOf(
            HttpClient::class,
            TokenStorageFactory::class,
            PersistentCookiesStorageFactory::class,
        )
        modules.forEach { it.verify(extraTypes = extraTypes) }
    }
}
