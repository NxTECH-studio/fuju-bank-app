package studio.nxtech.fujubank.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class TokenStorageFactory(private val context: Context) {
    actual fun create(): TokenStorage = EncryptedPreferencesTokenStorage(context)
}

private class EncryptedPreferencesTokenStorage(context: Context) : TokenStorage {
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

    override suspend fun getAccessToken(): String? = read(KEY_ACCESS)

    override suspend fun getRefreshToken(): String? = read(KEY_REFRESH)

    override suspend fun getSubject(): String? = read(KEY_SUBJECT)

    override suspend fun save(
        access: String,
        refresh: String,
        subject: String,
    ) = withContext(Dispatchers.IO) {
        runCatching {
            prefs.edit()
                .putString(KEY_ACCESS, access)
                .putString(KEY_REFRESH, refresh)
                .putString(KEY_SUBJECT, subject)
                .apply()
        }
        Unit
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        runCatching { prefs.edit().clear().apply() }
        Unit
    }

    private suspend fun read(key: String): String? = withContext(Dispatchers.IO) {
        runCatching { prefs.getString(key, null) }.getOrNull()
    }

    private companion object {
        const val PREF_FILE = "fuju_tokens"
        const val KEY_ACCESS = "access"
        const val KEY_REFRESH = "refresh"
        const val KEY_SUBJECT = "subject"
    }
}
