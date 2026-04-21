package studio.nxtech.fujubank.auth

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

actual class TokenStorageFactory {
    actual fun create(): TokenStorage = KeychainTokenStorage()
}

@OptIn(ExperimentalForeignApi::class)
private class KeychainTokenStorage : TokenStorage {
    override suspend fun getAccessToken(): String? = read(ACCOUNT_ACCESS)

    override suspend fun getRefreshToken(): String? = read(ACCOUNT_REFRESH)

    override suspend fun getSubject(): String? = read(ACCOUNT_SUBJECT)

    override suspend fun save(
        access: String,
        refresh: String,
        subject: String,
    ) {
        write(ACCOUNT_ACCESS, access)
        write(ACCOUNT_REFRESH, refresh)
        write(ACCOUNT_SUBJECT, subject)
    }

    override suspend fun clear() {
        delete(ACCOUNT_ACCESS)
        delete(ACCOUNT_REFRESH)
        delete(ACCOUNT_SUBJECT)
    }

    private fun read(account: String): String? = memScoped {
        val query = buildBaseQuery(account) ?: return@memScoped null
        CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        CFRelease(query)
        if (status != errSecSuccess) return@memScoped null
        val cfData: CFDataRef = result.value?.reinterpret() ?: return@memScoped null
        val length = CFDataGetLength(cfData).toInt()
        val bytePtr = CFDataGetBytePtr(cfData)
        val decoded = if (bytePtr != null && length > 0) {
            ByteArray(length) { bytePtr[it].toByte() }.decodeToString()
        } else {
            null
        }
        CFRelease(cfData)
        decoded
    }

    private fun write(
        account: String,
        value: String,
    ) {
        delete(account)
        memScoped {
            val query = buildBaseQuery(account) ?: return@memScoped
            val bytes = value.encodeToByteArray()
            val cfData = bytes.usePinned { pinned ->
                CFDataCreate(
                    kCFAllocatorDefault,
                    pinned.addressOf(0).reinterpret(),
                    bytes.size.convert(),
                )
            }
            if (cfData == null) {
                CFRelease(query)
                return@memScoped
            }
            CFDictionaryAddValue(query, kSecValueData, cfData)
            SecItemAdd(query, null)
            CFRelease(cfData)
            CFRelease(query)
        }
    }

    private fun delete(account: String) {
        memScoped {
            val query = buildBaseQuery(account) ?: return@memScoped
            SecItemDelete(query)
            CFRelease(query)
        }
    }

    private fun kotlinx.cinterop.MemScope.buildBaseQuery(account: String): CFMutableDictionaryRef? {
        val query = CFDictionaryCreateMutable(
            kCFAllocatorDefault,
            0,
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr,
        ) ?: return null
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        val serviceRef = CFStringCreateWithCString(kCFAllocatorDefault, SERVICE, kCFStringEncodingUTF8)
        val accountRef = CFStringCreateWithCString(kCFAllocatorDefault, account, kCFStringEncodingUTF8)
        if (serviceRef == null || accountRef == null) {
            serviceRef?.let { CFRelease(it) }
            accountRef?.let { CFRelease(it) }
            CFRelease(query)
            return null
        }
        CFDictionaryAddValue(query, kSecAttrService, serviceRef)
        CFDictionaryAddValue(query, kSecAttrAccount, accountRef)
        CFRelease(serviceRef)
        CFRelease(accountRef)
        return query
    }

    private companion object {
        const val SERVICE = "studio.nxtech.fujubank"
        const val ACCOUNT_ACCESS = "access"
        const val ACCOUNT_REFRESH = "refresh"
        const val ACCOUNT_SUBJECT = "subject"
    }
}
