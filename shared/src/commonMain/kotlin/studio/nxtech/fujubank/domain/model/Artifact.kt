package studio.nxtech.fujubank.domain.model

data class Artifact(
    val id: String,
    val title: String,
    val creatorUserId: String,
    val thumbnailUrl: String?,
)
