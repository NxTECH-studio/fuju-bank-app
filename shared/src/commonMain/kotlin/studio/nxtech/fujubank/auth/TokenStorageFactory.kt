package studio.nxtech.fujubank.auth

expect class TokenStorageFactory {
    fun create(): TokenStorage
}
