package studio.nxtech.fujubank.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import studio.nxtech.fujubank.data.remote.ApiErrorCode
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.remote.api.ArtifactApi
import studio.nxtech.fujubank.domain.model.Artifact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArtifactRepositoryTest {

    private fun buildClient(handler: MockRequestHandler): HttpClient = HttpClient(MockEngine(handler)) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                },
            )
        }
        defaultRequest {
            url("https://api.example.com/")
            headers.append(HttpHeaders.Accept, "application/json")
        }
    }

    @Test
    fun get_maps_response_to_domain_on_success() = runTest {
        val client = buildClient {
            respond(
                content = """
                    {
                      "id": "art_01HZY8X2B7",
                      "title": "夜の森",
                      "creator_user_id": "usr_01HZY8X2B7",
                      "thumbnail_url": "https://cdn.example.com/art_01HZY8X2B7.png"
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val repository = ArtifactRepository(ArtifactApi(client))

        val result = repository.get("art_01HZY8X2B7")

        assertTrue(result is NetworkResult.Success, "expected Success but was $result")
        assertEquals(
            Artifact(
                id = "art_01HZY8X2B7",
                title = "夜の森",
                creatorUserId = "usr_01HZY8X2B7",
                thumbnailUrl = "https://cdn.example.com/art_01HZY8X2B7.png",
            ),
            result.value,
        )
    }

    @Test
    fun get_maps_null_thumbnail_to_null() = runTest {
        val client = buildClient {
            respond(
                content = """
                    {
                      "id": "art_01HZY8X2B7",
                      "title": "夜の森",
                      "creator_user_id": "usr_01HZY8X2B7",
                      "thumbnail_url": null
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val repository = ArtifactRepository(ArtifactApi(client))

        val result = repository.get("art_01HZY8X2B7")

        assertTrue(result is NetworkResult.Success, "expected Success but was $result")
        assertEquals(null, result.value.thumbnailUrl)
    }

    @Test
    fun get_propagates_api_failure() = runTest {
        val client = buildClient {
            respond(
                content = """
                    {
                      "error": {
                        "code": "NOT_FOUND",
                        "message": "artifact not found"
                      }
                    }
                """.trimIndent(),
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val repository = ArtifactRepository(ArtifactApi(client))

        val result = repository.get("art_missing")

        assertTrue(result is NetworkResult.Failure, "expected Failure but was $result")
        assertEquals(ApiErrorCode.NOT_FOUND, result.error.code)
        assertEquals(404, result.error.httpStatus)
    }

    @Test
    fun get_propagates_network_failure() = runTest {
        val client = buildClient {
            respondError(HttpStatusCode.InternalServerError, content = "")
        }
        val repository = ArtifactRepository(ArtifactApi(client))

        val result = repository.get("art_01HZY8X2B7")

        assertTrue(result is NetworkResult.Failure, "expected Failure but was $result")
        assertEquals(500, result.error.httpStatus)
    }
}
