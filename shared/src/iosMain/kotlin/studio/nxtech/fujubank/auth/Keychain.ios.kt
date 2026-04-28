package studio.nxtech.fujubank.auth

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
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
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * iOS Keychain の Generic Password アイテムを `service` × `account` で読み書きする内部ヘルパ。
 *
 * `TokenStorageFactory.ios.kt` と `PersistentCookiesStorage.ios.kt` で共有する。
 * Keychain の `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` を使い
 * バックアップから別端末に持ち出されないようにしている。
 */
@OptIn(ExperimentalForeignApi::class)
internal object KeychainHelper {
    fun read(service: String, account: String): String? = memScoped {
        val query = buildBaseQuery(service, account) ?: return@memScoped null
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

    fun write(service: String, account: String, value: String) {
        delete(service, account)
        memScoped {
            val query = buildBaseQuery(service, account) ?: return@memScoped
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

    fun delete(service: String, account: String) {
        memScoped {
            val query = buildBaseQuery(service, account) ?: return@memScoped
            SecItemDelete(query)
            CFRelease(query)
        }
    }

    private fun MemScope.buildBaseQuery(service: String, account: String): CFMutableDictionaryRef? {
        val query = CFDictionaryCreateMutable(
            kCFAllocatorDefault,
            0,
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr,
        ) ?: return null
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(query, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        val serviceRef = CFStringCreateWithCString(kCFAllocatorDefault, service, kCFStringEncodingUTF8)
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
}
