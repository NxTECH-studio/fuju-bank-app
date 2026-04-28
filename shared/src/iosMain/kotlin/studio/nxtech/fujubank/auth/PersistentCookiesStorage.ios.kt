package studio.nxtech.fujubank.auth

import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.matches
import io.ktor.http.Cookie
import io.ktor.http.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

actual class PersistentCookiesStorageFactory {
    actual fun create(): CookiesStorage = KeychainCookiesStorage()
}

/**
 * iOS Keychain に Ktor の [Cookie] リストを JSON で保存する [CookiesStorage]。
 *
 * `URLSession` 系の cookie storage を使うと OS グローバルな cookie jar と
 * 混ざってしまうため、Ktor 独自の interface 実装で隔離する。
 */
private class KeychainCookiesStorage : CookiesStorage {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Cookie.serializer())

    override suspend fun get(requestUrl: Url): List<Cookie> = mutex.withLock {
        readAll().filter { it.matches(requestUrl) }
    }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        if (cookie.name.isBlank()) return
        mutex.withLock {
            val current = readAll().toMutableList()
            val withDefaults = cookie.fillDefaultsCompat(requestUrl)
            current.removeAll { existing ->
                existing.name == cookie.name && existing.matches(requestUrl)
            }
            current.add(withDefaults)
            writeAll(current)
        }
    }

    override fun close() {
        // Keychain アイテムは OS が管理するので明示 close 不要。
    }

    private suspend fun readAll(): List<Cookie> = withContext(Dispatchers.Default) {
        val raw = KeychainHelper.read(SERVICE, ACCOUNT) ?: return@withContext emptyList()
        runCatching { json.decodeFromString(serializer, raw) }.getOrElse { emptyList() }
    }

    private suspend fun writeAll(cookies: List<Cookie>) = withContext(Dispatchers.Default) {
        if (cookies.isEmpty()) {
            KeychainHelper.delete(SERVICE, ACCOUNT)
        } else {
            KeychainHelper.write(SERVICE, ACCOUNT, json.encodeToString(serializer, cookies))
        }
    }

    private companion object {
        const val SERVICE = "studio.nxtech.fujubank"
        const val ACCOUNT = "auth_cookies"
    }
}

private fun Cookie.fillDefaultsCompat(requestUrl: Url): Cookie {
    var result = this
    if (result.path?.startsWith("/") != true) {
        result = result.copy(path = requestUrl.encodedPath)
    }
    if (result.domain.isNullOrBlank()) {
        result = result.copy(domain = requestUrl.host)
    }
    return result
}
