package studio.nxtech.fujubank.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class TokenStorageFactory {
    actual fun create(): TokenStorage = KeychainTokenStorage()
}

private class KeychainTokenStorage : TokenStorage {
    override suspend fun loadAccess(): String? = withContext(Dispatchers.Default) {
        KeychainHelper.read(SERVICE, ACCOUNT_ACCESS)
    }

    override suspend fun loadExpiresAt(): Long? = withContext(Dispatchers.Default) {
        KeychainHelper.read(SERVICE, ACCOUNT_EXPIRES_AT)?.toLongOrNull()?.takeIf { it > 0L }
    }

    override suspend fun saveAccess(token: String, expiresAt: Long?) = withContext(Dispatchers.Default) {
        KeychainHelper.write(SERVICE, ACCOUNT_ACCESS, token)
        if (expiresAt != null) {
            KeychainHelper.write(SERVICE, ACCOUNT_EXPIRES_AT, expiresAt.toString())
        } else {
            KeychainHelper.delete(SERVICE, ACCOUNT_EXPIRES_AT)
        }
    }

    override suspend fun clear() = withContext(Dispatchers.Default) {
        KeychainHelper.delete(SERVICE, ACCOUNT_ACCESS)
        KeychainHelper.delete(SERVICE, ACCOUNT_EXPIRES_AT)
    }

    private companion object {
        const val SERVICE = "studio.nxtech.fujubank"
        const val ACCOUNT_ACCESS = "access"
        const val ACCOUNT_EXPIRES_AT = "access_expires_at"
    }
}
