package studio.nxtech.fujubank.data.remote.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * MFA まわりの DTO 境界値テスト。`AuthDtoTest` で網羅し切れていない
 * `mfa_required` の真偽分岐や `code` / `recovery_code` の排他性を補う。
 */
class MfaDtoTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun preTokenResponse_with_mfa_required_false_still_decodes() {
        // AuthCore の文書上 mfa_required は true で返る前提だが、true/false の
        // どちらでも DTO としては decode できることを保証する。判別は AuthApi 側。
        val payload = """
            {
              "pre_token": "pt-1",
              "mfa_required": false,
              "token_type": "Bearer",
              "expires_in": 600
            }
        """.trimIndent()

        val decoded = json.decodeFromString(PreTokenResponse.serializer(), payload)
        assertEquals(false, decoded.mfaRequired)
        assertEquals("pt-1", decoded.preToken)
    }

    @Test
    fun preTokenResponse_with_zero_expires_in_decodes() {
        // 期限切れ間際の境界。
        val payload = """
            {
              "pre_token": "pt-zero",
              "mfa_required": true,
              "token_type": "Bearer",
              "expires_in": 0
            }
        """.trimIndent()

        val decoded = json.decodeFromString(PreTokenResponse.serializer(), payload)
        assertEquals(0L, decoded.expiresIn)
    }

    @Test
    fun mfaVerifyRequest_with_both_fields_serializes_both() {
        // サーバ側で `VALIDATION_FAILED` を返す前提だが、クライアントは両方付けても
        // 送信できる（バリデーションは UI 側責務）。
        val encoded = json.encodeToString(
            MfaVerifyRequest.serializer(),
            MfaVerifyRequest(code = "123456", recoveryCode = "abcd-efgh"),
        )
        assertTrue(encoded.contains(""""code":"123456""""), "encoded=$encoded")
        assertTrue(encoded.contains(""""recovery_code":"abcd-efgh""""), "encoded=$encoded")
    }

    @Test
    fun mfaVerifyRequest_with_no_fields_serializes_to_empty_object() {
        // explicitNulls = false なので両方 null は `{}` になる。サーバが
        // `VALIDATION_FAILED` を返す前提。
        val encoded = json.encodeToString(
            MfaVerifyRequest.serializer(),
            MfaVerifyRequest(),
        )
        assertEquals("{}", encoded)
    }

    @Test
    fun mfaVerifyRequest_decodes_camelCase_recovery_code_is_ignored() {
        // 万一 サーバから snake_case でなく camelCase が返っても、ignoreUnknownKeys=true なので
        // 無視される（recoveryCode は null）。code 側だけ拾う。
        val payload = """
            {
              "code": "123456",
              "recoveryCode": "should-be-ignored"
            }
        """.trimIndent()

        val decoded = json.decodeFromString(MfaVerifyRequest.serializer(), payload)
        assertEquals("123456", decoded.code)
        // snake_case でないキーは untyped に落ち、recoveryCode は null のまま。
        assertEquals(null, decoded.recoveryCode)
    }

    @Test
    fun mfaVerifyRequest_six_digit_code_does_not_cause_serialization_issue() {
        // 6 桁数字コードの典型ケース。先頭ゼロ含む文字列をそのまま保持できること。
        val encoded = json.encodeToString(
            MfaVerifyRequest.serializer(),
            MfaVerifyRequest(code = "000123"),
        )
        assertTrue(encoded.contains(""""code":"000123""""), "encoded=$encoded")
        assertFalse(encoded.contains("\"code\":123"), "code は string で送られる必要あり: $encoded")
    }
}
