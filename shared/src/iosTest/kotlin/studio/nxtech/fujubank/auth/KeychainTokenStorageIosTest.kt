package studio.nxtech.fujubank.auth

import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * iOS actual の `KeychainTokenStorage` 最小スモーク。
 *
 * 計画書（client-bank-20）の方針:
 * - SecItem の race / セキュリティ属性検証までは踏み込まない。
 * - iosSimulatorArm64Test で確認できるのは下記の最低限のみ:
 *   - `TokenStorageFactory.create()` が actual を解決できる
 *   - `loadAccess()` / `loadExpiresAt()` / `saveAccess()` / `clear()` が
 *     例外を投げず suspend 関数として実行できる
 *
 * シミュレータ環境では keychain-access-groups entitlement が無く `SecItemAdd` が
 * `errSecMissingEntitlement (-34018)` を返すケースがある。本物のラウンドトリップ確認は
 * 実機（または entitlement が整った Xcode テストターゲット = client-bank-22 想定）に委ねる。
 */
class KeychainTokenStorageIosTest {

    private val factory = TokenStorageFactory()
    private val storage: TokenStorage = factory.create()

    @AfterTest
    fun tearDown() = runTest {
        // 後続テストへの汚染防止。失敗しても無視。
        runCatching { storage.clear() }
    }

    @Test
    fun factory_creates_actual_token_storage() {
        // expect/actual が iOS 側で解決され、interface 実装が手に入ること。
        assertNotNull(storage, "TokenStorageFactory.create() が actual を返すこと")
    }

    @Test
    fun load_methods_do_not_throw_when_empty() = runTest {
        // 何も保存していない状態で load を呼んでも例外で落ちず、null か値を返すこと。
        // シミュレータで Keychain 書き込みに失敗する環境でも、`load*` だけは安定して動く。
        storage.clear()
        // null か非 null どちらでも OK（ここでは throw しないことだけを確認）。
        storage.loadAccess()
        storage.loadExpiresAt()
    }

    @Test
    fun save_then_clear_does_not_throw() = runTest {
        // SecItem 書き込みが成功する／しないに関わらず、API 呼び出しが例外で
        // 落ちないこと。これにより iosMain の actual が KeychainHelper の戻り値を
        // 正しくハンドリング（runCatching せず errSecXXX を握り潰す）していることを保証する。
        storage.saveAccess("at_smoke", expiresAt = 1L)
        storage.clear()
    }
}
