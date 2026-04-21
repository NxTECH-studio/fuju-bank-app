package studio.nxtech.fujubank.data.remote.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.path
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.remote.dto.ArtifactResponse
import studio.nxtech.fujubank.data.remote.runCatchingNetwork

class ArtifactApi(private val client: HttpClient) {

    suspend fun get(artifactId: String): NetworkResult<ArtifactResponse> = runCatchingNetwork {
        client.get {
            url { path("artifacts", artifactId) }
        }.body()
    }
}
