package studio.nxtech.fujubank.data.remote.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArtifactDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun artifactResponse_deserializes_full_payload() {
        val payload = """
            {
              "id": "art_01HZY8X2B7",
              "title": "夜の森",
              "creator_user_id": "usr_01HZY8X2B7",
              "thumbnail_url": "https://cdn.example.com/art_01HZY8X2B7.png"
            }
        """.trimIndent()

        val decoded = json.decodeFromString(ArtifactResponse.serializer(), payload)

        assertEquals("art_01HZY8X2B7", decoded.id)
        assertEquals("夜の森", decoded.title)
        assertEquals("usr_01HZY8X2B7", decoded.creatorUserId)
        assertEquals("https://cdn.example.com/art_01HZY8X2B7.png", decoded.thumbnailUrl)
    }

    @Test
    fun artifactResponse_deserializes_null_thumbnail() {
        val payload = """
            {
              "id": "art_02HZY8X2B7",
              "title": "無題",
              "creator_user_id": "usr_02HZY8X2B7",
              "thumbnail_url": null
            }
        """.trimIndent()

        val decoded = json.decodeFromString(ArtifactResponse.serializer(), payload)

        assertEquals("art_02HZY8X2B7", decoded.id)
        assertNull(decoded.thumbnailUrl)
    }

    @Test
    fun artifactResponse_roundtrips() {
        val original = ArtifactResponse(
            id = "art_01HZY8X2B7",
            title = "夜の森",
            creatorUserId = "usr_01HZY8X2B7",
            thumbnailUrl = "https://cdn.example.com/art_01HZY8X2B7.png",
        )
        val encoded = json.encodeToString(ArtifactResponse.serializer(), original)
        val decoded = json.decodeFromString(ArtifactResponse.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun artifactResponse_ignores_unknown_fields() {
        val payload = """
            {
              "id": "art_03HZY8X2B7",
              "title": "将来フィールド入り",
              "creator_user_id": "usr_03HZY8X2B7",
              "thumbnail_url": null,
              "description": "まだクライアントで未使用",
              "created_at": "2026-04-21T00:00:00Z"
            }
        """.trimIndent()

        val decoded = json.decodeFromString(ArtifactResponse.serializer(), payload)

        assertEquals("art_03HZY8X2B7", decoded.id)
        assertEquals("将来フィールド入り", decoded.title)
    }
}
