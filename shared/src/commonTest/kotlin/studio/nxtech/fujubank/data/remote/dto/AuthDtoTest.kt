package studio.nxtech.fujubank.data.remote.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun loginRequest_roundtrips() {
        val original = LoginRequest(email = "user@example.com", password = "p@ssw0rd")
        val encoded = json.encodeToString(LoginRequest.serializer(), original)
        val decoded = json.decodeFromString(LoginRequest.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun loginRequest_uses_camelCase_field_names() {
        val encoded = json.encodeToString(
            LoginRequest.serializer(),
            LoginRequest(email = "a@b.co", password = "pw"),
        )
        assertEquals("""{"email":"a@b.co","password":"pw"}""", encoded)
    }

    @Test
    fun refreshRequest_uses_snake_case() {
        val encoded = json.encodeToString(
            RefreshRequest.serializer(),
            RefreshRequest(refreshToken = "rt-123"),
        )
        assertEquals("""{"refresh_token":"rt-123"}""", encoded)
    }

    @Test
    fun refreshRequest_roundtrips() {
        val original = RefreshRequest(refreshToken = "rt-123")
        val encoded = json.encodeToString(RefreshRequest.serializer(), original)
        val decoded = json.decodeFromString(RefreshRequest.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun tokenResponse_deserializes_snake_case_payload() {
        val payload = """
            {
              "access_token": "at-abc",
              "refresh_token": "rt-xyz",
              "subject": "01HZY8X2B7K3J4M5N6P7Q8R9ST",
              "expires_in": 3600
            }
        """.trimIndent()

        val decoded = json.decodeFromString(TokenResponse.serializer(), payload)

        assertEquals("at-abc", decoded.accessToken)
        assertEquals("rt-xyz", decoded.refreshToken)
        assertEquals("01HZY8X2B7K3J4M5N6P7Q8R9ST", decoded.subject)
        assertEquals(3600L, decoded.expiresIn)
    }

    @Test
    fun tokenResponse_roundtrips() {
        val original = TokenResponse(
            accessToken = "at-abc",
            refreshToken = "rt-xyz",
            subject = "01HZY8X2B7K3J4M5N6P7Q8R9ST",
            expiresIn = 3600L,
        )
        val encoded = json.encodeToString(TokenResponse.serializer(), original)
        val decoded = json.decodeFromString(TokenResponse.serializer(), encoded)
        assertEquals(original, decoded)
    }
}
