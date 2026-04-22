package studio.nxtech.fujubank.data.repository

import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.remote.api.ArtifactApi
import studio.nxtech.fujubank.data.remote.dto.ArtifactResponse
import studio.nxtech.fujubank.data.remote.map
import studio.nxtech.fujubank.domain.model.Artifact

class ArtifactRepository(private val artifactApi: ArtifactApi) {

    suspend fun get(artifactId: String): NetworkResult<Artifact> =
        artifactApi.get(artifactId).map { it.toDomain() }
}

private fun ArtifactResponse.toDomain(): Artifact = Artifact(
    id = id,
    title = title,
    creatorUserId = creatorUserId,
    thumbnailUrl = thumbnailUrl,
)
