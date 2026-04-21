package studio.nxtech.fujubank.auth

interface TokenStorage {
    suspend fun getAccessToken(): String?

    suspend fun getRefreshToken(): String?

    suspend fun getSubject(): String?

    suspend fun save(
        access: String,
        refresh: String,
        subject: String,
    )

    suspend fun clear()
}
