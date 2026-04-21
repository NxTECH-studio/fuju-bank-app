package studio.nxtech.fujubank.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ArtifactResponse(
    val id: String,
    val title: String,
    @SerialName("creator_user_id")
    val creatorUserId: String,
    // backend 側でサムネイル未設定の場合は null。
    @SerialName("thumbnail_url")
    val thumbnailUrl: String?,
)
