package studio.nxtech.fujubank.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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

actual class PersistentCookiesStorageFactory(private val context: Context) {
    actual fun create(): CookiesStorage = EncryptedPreferencesCookiesStorage(context)
}

/**
 * EncryptedSharedPreferences に Ktor の [Cookie] リストを JSON で保存する [CookiesStorage]。
 *
 * AuthCore の refresh_token cookie は `Max-Age=2592000` + `Expires=<RFC1123>` 付きで配送される。
 * 永続化と取り出しは Ktor 側 plugin が `addCookie`/`get` 経由で面倒を見るので、ここでは
 * 永続層（暗号化 prefs）への保存・読み出しと、リクエスト URL でのフィルタだけを担当する。
 */
private class EncryptedPreferencesCookiesStorage(context: Context) : CookiesStorage {
    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREF_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

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
            // Path / Domain が空のままだと CookiesStorage.matches() が要求する default が
            // 入らないので、request URL の値で埋めてから保存する（AcceptAllCookiesStorage と同等）。
            val withDefaults = cookie.fillDefaultsCompat(requestUrl)
            current.removeAll { existing ->
                existing.name == cookie.name && existing.matches(requestUrl)
            }
            current.add(withDefaults)
            writeAll(current)
        }
    }

    override fun close() {
        // EncryptedSharedPreferences はプロセスライフサイクルに紐づくので明示 close 不要。
    }

    private suspend fun readAll(): List<Cookie> = withContext(Dispatchers.IO) {
        runCatching {
            prefs.getString(KEY_COOKIES, null)?.let { json.decodeFromString(serializer, it) }.orEmpty()
        }.getOrElse { emptyList() }
    }

    private suspend fun writeAll(cookies: List<Cookie>) = withContext(Dispatchers.IO) {
        runCatching {
            prefs.edit().putString(KEY_COOKIES, json.encodeToString(serializer, cookies)).apply()
        }
        Unit
    }

    private companion object {
        const val PREF_FILE = "fuju_cookies"
        const val KEY_COOKIES = "auth_cookies"
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
