package studio.nxtech.fujubank.data.remote.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArtifactApiTest {

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
    fun get_returns_success_on_200() = runTest {
        val client = buildClient { request ->
            assertEquals("/artifacts/art_01HZY8X2B7", request.url.encodedPath)
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
        val api = ArtifactApi(client)

        val result = api.get("art_01HZY8X2B7")

        assertTrue(result is NetworkResult.Success, "expected Success but was $result")
        assertEquals("art_01HZY8X2B7", result.value.id)
        assertEquals("夜の森", result.value.title)
        assertEquals("usr_01HZY8X2B7", result.value.creatorUserId)
        assertEquals("https://cdn.example.com/art_01HZY8X2B7.png", result.value.thumbnailUrl)
    }

    @Test
    fun get_returns_failure_on_404() = runTest {
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
        val api = ArtifactApi(client)

        val result = api.get("art_missing")

        assertTrue(result is NetworkResult.Failure, "expected Failure but was $result")
        assertEquals(ApiErrorCode.NOT_FOUND, result.error.code)
        assertEquals(404, result.error.httpStatus)
        assertEquals("artifact not found", result.error.message)
    }
}
