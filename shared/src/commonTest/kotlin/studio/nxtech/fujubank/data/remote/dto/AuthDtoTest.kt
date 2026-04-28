package studio.nxtech.fujubank.data.remote.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthDtoTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun loginRequest_uses_identifier_field() {
        val encoded = json.encodeToString(
            LoginRequest.serializer(),
            LoginRequest(identifier = "user@example.com", password = "pw"),
        )
        assertEquals("""{"identifier":"user@example.com","password":"pw"}""", encoded)
    }

    @Test
    fun loginRequest_roundtrips() {
        val original = LoginRequest(identifier = "alice01", password = "p@ssw0rd")
        val encoded = json.encodeToString(LoginRequest.serializer(), original)
        val decoded = json.decodeFromString(LoginRequest.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun tokenResponse_deserializes_authcore_payload() {
        val payload = """
            {
              "access_token": "at-abc",
              "token_type": "Bearer",
              "expires_in": 900
            }
        """.trimIndent()

        val decoded = json.decodeFromString(TokenResponse.serializer(), payload)

        assertEquals("at-abc", decoded.accessToken)
        assertEquals("Bearer", decoded.tokenType)
        assertEquals(900L, decoded.expiresIn)
    }

    @Test
    fun preTokenResponse_deserializes_mfa_payload() {
        val payload = """
            {
              "pre_token": "pt-xyz",
              "mfa_required": true,
              "token_type": "Bearer",
              "expires_in": 600
            }
        """.trimIndent()

        val decoded = json.decodeFromString(PreTokenResponse.serializer(), payload)

        assertEquals("pt-xyz", decoded.preToken)
        assertEquals(true, decoded.mfaRequired)
        assertEquals(600L, decoded.expiresIn)
    }

    @Test
    fun mfaVerifyRequest_omits_null_fields() {
        val encoded = json.encodeToString(
            MfaVerifyRequest.serializer(),
            MfaVerifyRequest(code = "123456"),
        )
        // explicitNulls = false なので recovery_code は出ない。
        assertEquals("""{"code":"123456"}""", encoded)
    }

    @Test
    fun mfaVerifyRequest_uses_snake_case_for_recovery_code() {
        val encoded = json.encodeToString(
            MfaVerifyRequest.serializer(),
            MfaVerifyRequest(recoveryCode = "abcd-efgh"),
        )
        assertTrue(encoded.contains(""""recovery_code":"abcd-efgh""""), "encoded=$encoded")
    }
}
