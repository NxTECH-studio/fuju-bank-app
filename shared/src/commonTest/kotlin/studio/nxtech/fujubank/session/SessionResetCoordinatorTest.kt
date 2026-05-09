package studio.nxtech.fujubank.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import studio.nxtech.fujubank.account.AccountProfile
import studio.nxtech.fujubank.account.AccountProfileProvider
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionResetCoordinatorTest {

    /** reset() の呼び出し回数を数える fake。profile/updateProfile は本テストでは未使用。 */
    private class FakeAccountProfileProvider : AccountProfileProvider {
        var resetCount: Int = 0
        private val _profile = MutableStateFlow(
            AccountProfile(displayName = "", email = "", accountId = ""),
        )
        override val profile: StateFlow<AccountProfile> = _profile.asStateFlow()
        override fun updateProfile(displayName: String, email: String) = Unit
        override fun reset() {
            resetCount += 1
        }
    }

    private fun coordinator(
        sessionStore: SessionStore,
        provider: FakeAccountProfileProvider,
        scope: CoroutineScope,
    ): SessionResetCoordinator =
        SessionResetCoordinator(
            sessionStore = sessionStore,
            accountProfileProvider = provider,
            scope = scope,
        )

    @Test
    fun authenticatedToUnauthenticatedTriggersResetExactlyOnce() = runTest {
        val store = SessionStore()
        val provider = FakeAccountProfileProvider()
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        coordinator(store, provider, testScope).start()
        testScheduler.runCurrent()

        store.setAuthenticated("u1")
        testScheduler.runCurrent()
        store.clear()
        testScheduler.runCurrent()

        assertEquals(1, provider.resetCount)
    }

    @Test
    fun initialUnauthenticatedDoesNotTriggerReset() = runTest {
        val store = SessionStore()
        val provider = FakeAccountProfileProvider()
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        coordinator(store, provider, testScope).start()
        testScheduler.runCurrent()

        // 初期値の Unauthenticated emit のみで遷移無し。
        assertEquals(0, provider.resetCount)
    }

    @Test
    fun unauthenticatedToMfaPendingDoesNotTriggerReset() = runTest {
        val store = SessionStore()
        val provider = FakeAccountProfileProvider()
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        coordinator(store, provider, testScope).start()
        testScheduler.runCurrent()

        store.setMfaPending("pt_1")
        testScheduler.runCurrent()

        assertEquals(0, provider.resetCount)
    }

    @Test
    fun authenticatedToMfaPendingDoesNotTriggerReset() = runTest {
        val store = SessionStore()
        val provider = FakeAccountProfileProvider()
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        coordinator(store, provider, testScope).start()
        testScheduler.runCurrent()

        store.setAuthenticated("u1")
        testScheduler.runCurrent()
        store.setMfaPending("pt_1")
        testScheduler.runCurrent()

        assertEquals(0, provider.resetCount)
    }

    @Test
    fun multipleLogoutCyclesTriggerResetEachTime() = runTest {
        val store = SessionStore()
        val provider = FakeAccountProfileProvider()
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        coordinator(store, provider, testScope).start()
        testScheduler.runCurrent()

        repeat(3) {
            store.setAuthenticated("u$it")
            testScheduler.runCurrent()
            store.clear()
            testScheduler.runCurrent()
        }

        assertEquals(3, provider.resetCount)
    }

    @Test
    fun startIsIdempotent() = runTest {
        val store = SessionStore()
        val provider = FakeAccountProfileProvider()
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        val coord = coordinator(store, provider, testScope)
        val first = coord.start()
        val second = coord.start()
        testScheduler.runCurrent()

        // 2 回目の start() は no-op で null を返す。
        kotlin.test.assertNotNull(first)
        kotlin.test.assertNull(second)

        store.setAuthenticated("u1")
        testScheduler.runCurrent()
        store.clear()
        testScheduler.runCurrent()

        // collector が 1 つだけ走っていることを reset 回数で確認。
        assertEquals(1, provider.resetCount)
    }
}
