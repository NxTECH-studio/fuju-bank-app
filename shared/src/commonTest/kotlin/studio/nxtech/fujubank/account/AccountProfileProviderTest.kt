package studio.nxtech.fujubank.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AccountProfileProviderTest {

    @Test
    fun dummyResetRestoresInitialProfileAfterUpdate() {
        val provider = DummyAccountProfileProvider()
        val initial = provider.profile.value

        provider.updateProfile(publicId = "other_user", email = "other@example.com")
        // 編集後の値は initial と異なる前提（テストの自己確認）。
        assertNotEquals(initial, provider.profile.value)

        provider.reset()
        assertEquals(initial, provider.profile.value)
    }

    @Test
    fun dummyResetIsIdempotent() {
        val provider = DummyAccountProfileProvider()
        val initial = provider.profile.value

        provider.reset()
        provider.reset()
        assertEquals(initial, provider.profile.value)
    }
}
