package studio.nxtech.fujubank.domain.model

/**
 * ホーム画面で表示するユーザープロフィール。AuthCore と bank の 2 つの API レスポンスを
 * マージしたドメインモデル。
 *
 * - AuthCore (`GET /v1/user/profile`): [authCoreId] / [email] / [publicId] / [iconUrl] / [mfaEnabled]
 * - bank (`GET /users/me`): [bankUserId] / [balanceFuju]
 *
 * QR / バーコードは [publicId] をエンコードする。残高表示は [balanceFuju]。
 */
data class UserProfile(
    val authCoreId: String,
    val bankUserId: String,
    val publicId: String,
    val email: String?,
    val iconUrl: String?,
    val mfaEnabled: Boolean,
    val balanceFuju: Long,
)
