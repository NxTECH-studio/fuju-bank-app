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

    override suspend fun loadAccess(): String? = withContext(Dispatchers.IO) {
        runCatching { prefs.getString(KEY_ACCESS, null) }.getOrNull()
    }

    override suspend fun loadExpiresAt(): Long? = withContext(Dispatchers.IO) {
        runCatching {
            val raw = prefs.getLong(KEY_EXPIRES_AT, -1L)
            if (raw <= 0L) null else raw
        }.getOrNull()
    }

    override suspend fun saveAccess(token: String, expiresAt: Long?) = withContext(Dispatchers.IO) {
        runCatching {
            prefs.edit().apply {
                putString(KEY_ACCESS, token)
                if (expiresAt != null) {
                    putLong(KEY_EXPIRES_AT, expiresAt)
                } else {
                    remove(KEY_EXPIRES_AT)
                }
            }.apply()
        }
        Unit
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        runCatching { prefs.edit().clear().apply() }
        Unit
    }

    private companion object {
        const val PREF_FILE = "fuju_tokens"
        const val KEY_ACCESS = "access"
        const val KEY_EXPIRES_AT = "expires_at"
    }
}
