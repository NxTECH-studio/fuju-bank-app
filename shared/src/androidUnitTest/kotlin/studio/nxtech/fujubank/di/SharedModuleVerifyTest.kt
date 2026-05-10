package studio.nxtech.fujubank.di

import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify
import studio.nxtech.fujubank.account.AccountProfileProvider
import studio.nxtech.fujubank.auth.PersistentCookiesStorageFactory
import studio.nxtech.fujubank.auth.TokenStorage
import studio.nxtech.fujubank.auth.TokenStorageFactory
import studio.nxtech.fujubank.data.repository.AuthRepository
import studio.nxtech.fujubank.data.repository.ProfileRepository
import studio.nxtech.fujubank.session.SessionStore

class SharedModuleVerifyTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun sharedModulesHaveNoUnresolvedDependencies() {
        val modules = sharedModules(cableUrl = "wss://example.test/cable")
        // HttpClient / TokenStorageFactory / PersistentCookiesStorageFactory は
        // プラットフォーム側で登録されるため、ここでは external dependency として扱う。
        // Settings は signupModule 側で 1 度だけ登録され accountModule から共有参照されるため、
        // 単一モジュールごとに verify する都合上 external 扱いにする。
        // ProfileRepository (userModule 提供) / CoroutineScope (realtimeModule 提供) も
        // accountModule の RemoteAccountProfileProvider から横断的に参照するため external 扱い。
        // AccountProfileProvider は accountModule 提供で sessionModule の
        // SessionResetCoordinator が横断参照するため external 扱い。
        // AuthRepository / TokenStorage は authModule 提供で sessionModule の
        // TokenExpiryWatcher が横断参照するため external 扱い。
        // SessionStore は sessionModule 提供で userModule の UserRepository
        // (送金先検索の自分除外ロジック) が横断参照するため external 扱い。
        val extraTypes = listOf(
            HttpClient::class,
            TokenStorageFactory::class,
            PersistentCookiesStorageFactory::class,
            Settings::class,
            ProfileRepository::class,
            CoroutineScope::class,
            AccountProfileProvider::class,
            AuthRepository::class,
            TokenStorage::class,
            SessionStore::class,
        )
        modules.forEach { it.verify(extraTypes = extraTypes) }
    }
}
