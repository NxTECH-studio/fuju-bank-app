package studio.nxtech.fujubank.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import studio.nxtech.fujubank.account.AccountProfile
import studio.nxtech.fujubank.account.AccountProfileProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SessionResetCoordinatorTest {

    /** reset() / ensureLoaded() の呼び出し回数を数える fake。 */
    private class FakeAccountProfileProvider : AccountProfileProvider {
        var resetCount: Int = 0
        var ensureLoadedCount: Int = 0
        private val _profile = MutableStateFlow(
            AccountProfile(publicId = "", email = "", accountId = ""),
        )
        override val profile: StateFlow<AccountProfile> = _profile.asStateFlow()
        override fun updateProfile(publicId: String, email: String) = Unit
        override fun reset() {
            resetCount += 1
        }
        override suspend fun ensureLoaded() {
            ensureLoadedCount += 1
        }
    }

    @Test
    fun authenticatedToUnauthenticatedTriggersResetExactlyOnce() = runTest {
        val store = SessionStore()
        val provider = FakeAccountProfileProvider()
        SessionResetCoordinator(store, provider, scope = backgroundScope).start()
        testScheduler.runCurrent()

        store.setAuthenticated(userId = "u1", bankUserId = "1")
        testScheduler.runCurrent()
        store.clear()
        testScheduler.runCurrent()

        assertEquals(1, provider.resetCount)
        // Coordinator は ensureLoaded を呼ばない（lazy load 担当は AccountHub 側）。
        assertEquals(0, provider.ensureLoadedCount)
    }

    @Test
    fun initialUnauthenticatedDoesNotTriggerReset() = runTest {
        val store = SessionStore()
        val provider = FakeAccountProfileProvider()
        SessionResetCoordinator(store, provider, scope = backgroundScope).start()
        testScheduler.runCurrent()

        // 初期値の Unauthenticated emit のみで遷移無し。
        assertEquals(0, provider.resetCount)
        assertEquals(0, provider.ensureLoadedCount)
    }

    @Test
    fun unauthenticatedToMfaPendingDoesNotTriggerReset() = runTest {
        val store = SessionStore()
        val provider = FakeAccountProfileProvider()
        SessionResetCoordinator(store, provider, scope = backgroundScope).start()
        testScheduler.runCurrent()

        store.setMfaPending("pt_1")
        testScheduler.runCurrent()

        assertEquals(0, provider.resetCount)
    }

    @Test
    fun authenticatedToMfaPendingDoesNotTriggerReset() = runTest {
        val store = SessionStore()
        val provider = FakeAccountProfileProvider()
        SessionResetCoordinator(store, provider, scope = backgroundScope).start()
        testScheduler.runCurrent()

        store.setAuthenticated(userId = "u1", bankUserId = "1")
        testScheduler.runCurrent()
        store.setMfaPending("pt_1")
        testScheduler.runCurrent()

        assertEquals(0, provider.resetCount)
    }

    @Test
    fun multipleLogoutCyclesTriggerResetEachTime() = runTest {
        val store = SessionStore()
        val provider = FakeAccountProfileProvider()
        SessionResetCoordinator(store, provider, scope = backgroundScope).start()
        testScheduler.runCurrent()

        repeat(3) {
            store.setAuthenticated(userId = "u$it", bankUserId = "$it")
            testScheduler.runCurrent()
            store.clear()
            testScheduler.runCurrent()
        }

        assertEquals(3, provider.resetCount)
    }

    @Test
    fun onLogoutBoundaryIsInvokedOnLogoutTransition() = runTest {
        // logout 境界では AccountProfileProvider.reset と並んで onLogoutBoundary も
        // 呼ばれる。Koin では `RealtimeRepository.clearCache` がここに繋がる。
        val store = SessionStore()
        val provider = FakeAccountProfileProvider()
        var boundaryCalls = 0
        SessionResetCoordinator(
            sessionStore = store,
            accountProfileProvider = provider,
            onLogoutBoundary = { boundaryCalls += 1 },
            scope = backgroundScope,
        ).start()
        testScheduler.runCurrent()

        store.setAuthenticated(userId = "u1", bankUserId = "1")
        testScheduler.runCurrent()
        store.clear()
        testScheduler.runCurrent()

        assertEquals(1, provider.resetCount)
        assertEquals(1, boundaryCalls)
    }

    @Test
    fun onLogoutBoundaryIsNotInvokedOnInitialUnauthenticated() = runTest {
        // アプリ起動直後の Unauthenticated emit を logout と誤認しない契約は
        // onLogoutBoundary にも適用される。
        val store = SessionStore()
        val provider = FakeAccountProfileProvider()
        var boundaryCalls = 0
        SessionResetCoordinator(
            sessionStore = store,
            accountProfileProvider = provider,
            onLogoutBoundary = { boundaryCalls += 1 },
            scope = backgroundScope,
        ).start()
        testScheduler.runCurrent()

        assertEquals(0, boundaryCalls)
    }

    @Test
    fun startIsIdempotent() = runTest {
        val store = SessionStore()
        val provider = FakeAccountProfileProvider()
        val coord = SessionResetCoordinator(store, provider, scope = backgroundScope)
        val first = coord.start()
        val second = coord.start()
        testScheduler.runCurrent()

        // 2 回目の start() は no-op で null を返す。
        assertNotNull(first)
        assertNull(second)

        store.setAuthenticated(userId = "u1", bankUserId = "1")
        testScheduler.runCurrent()
        store.clear()
        testScheduler.runCurrent()

        // collector が 1 つだけ走っていることを reset 回数で確認。
        assertEquals(1, provider.resetCount)
    }
}
