package studio.nxtech.fujubank.data.remote.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class UserDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun createUserRequest_uses_sub_snake_case() {
        val encoded = json.encodeToString(
            CreateUserRequest.serializer(),
            CreateUserRequest(subject = "01HZY8X2B7K3J4M5N6P7Q8R9ST"),
        )
        assertEquals("""{"sub":"01HZY8X2B7K3J4M5N6P7Q8R9ST"}""", encoded)
    }

    @Test
    fun createUserRequest_roundtrips() {
        val original = CreateUserRequest(subject = "01HZY8X2B7K3J4M5N6P7Q8R9ST")
        val encoded = json.encodeToString(CreateUserRequest.serializer(), original)
        val decoded = json.decodeFromString(CreateUserRequest.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun userResponse_deserializes_snake_case_payload() {
        val payload = """
            {
              "id": "usr_01HZY8X2B7",
              "sub": "01HZY8X2B7K3J4M5N6P7Q8R9ST",
              "balance_fuju": 1000000,
              "created_at": "2026-04-21T12:34:56Z"
            }
        """.trimIndent()

        val decoded = json.decodeFromString(UserResponse.serializer(), payload)

        assertEquals("usr_01HZY8X2B7", decoded.id)
        assertEquals("01HZY8X2B7K3J4M5N6P7Q8R9ST", decoded.subject)
        assertEquals(1_000_000L, decoded.balanceFuju)
        assertEquals("2026-04-21T12:34:56Z", decoded.createdAt)
    }

    @Test
    fun userResponse_handles_bigint_balance() {
        // bigint の範囲を確認（Int では溢れる値）。
        val payload = """
            {
              "id": "usr_1",
              "sub": "s",
              "balance_fuju": 9223372036854775807,
              "created_at": "2026-04-21T00:00:00Z"
            }
        """.trimIndent()

        val decoded = json.decodeFromString(UserResponse.serializer(), payload)

        assertEquals(Long.MAX_VALUE, decoded.balanceFuju)
    }

    @Test
    fun userResponse_roundtrips() {
        val original = UserResponse(
            id = "usr_01HZY8X2B7",
            subject = "01HZY8X2B7K3J4M5N6P7Q8R9ST",
            balanceFuju = 1_000_000L,
            createdAt = "2026-04-21T12:34:56Z",
        )
        val encoded = json.encodeToString(UserResponse.serializer(), original)
        val decoded = json.decodeFromString(UserResponse.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun userResponse_ignores_unknown_fields() {
        val payload = """
            {
              "id": "usr_1",
              "sub": "s",
              "balance_fuju": 0,
              "created_at": "2026-04-21T00:00:00Z",
              "updated_at": "2026-04-21T00:00:00Z"
            }
        """.trimIndent()

        val decoded = json.decodeFromString(UserResponse.serializer(), payload)

        assertEquals("usr_1", decoded.id)
    }
}
