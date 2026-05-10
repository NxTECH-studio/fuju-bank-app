package studio.nxtech.fujubank.data.remote.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.remote.dto.LoginRequest
import studio.nxtech.fujubank.data.remote.dto.MfaEnableRequest
import studio.nxtech.fujubank.data.remote.dto.MfaRegisterResponse
import studio.nxtech.fujubank.data.remote.dto.MfaVerifyRequest
import studio.nxtech.fujubank.data.remote.dto.PreTokenResponse
import studio.nxtech.fujubank.data.remote.dto.RegisterRequest
import studio.nxtech.fujubank.data.remote.dto.RegisterResponse
import studio.nxtech.fujubank.data.remote.dto.TokenResponse
import studio.nxtech.fujubank.data.remote.runCatchingNetwork

/**
 * AuthCore (`fuju-system-authentication`) の /v1/auth 配下エンドポイント呼び出し。
 *
 * - login は MFA 必須ユーザのとき `pre_token` 付きの別形態で 200 を返すため、
 *   呼び出し側で sealed branch を判別できるよう [LoginRawResponse] 経由で返す。
 * - login / refresh / logout / mfaVerify / register / mfaRegister / mfaEnable いずれも
 *   `Auth` プラグイン無しの専用クライアント（AUTHCORE_CLIENT_QUALIFIER）で叩く。
 *   理由: bank/AuthCore 共通の Auth プラグイン付きクライアントを使うと、refresh 401 時に
 *   `refreshTokens` ブロックが自己再帰起動し、AuthApi 自身が同じクライアント生成途中の
 *   依存に組み込まれて Koin が循環依存で死ぬ。
 * - mfaRegister / mfaEnable は Bearer 必須なので [authTokenProvider] から access_token を
 *   読んで Authorization ヘッダを **手動** で付ける（自動 refresh は走らないが、register
 *   直後 / login 直後の数秒以内に呼ぶ前提のため access_token は十分新しい）。
 */
class AuthApi(
    private val authCoreClient: HttpClient,
    private val authCoreBaseUrl: String,
    private val authTokenProvider: suspend () -> String? = { null },
) {
    /**
     * 既存テスト向けのコンビニエンスコンストラクタ。`client` 引数名で呼んでいる
     * テストとの互換維持のため残す。authTokenProvider は null 固定（mfaRegister /
     * mfaEnable を叩かないテストパスでだけ使うこと）。
     */
    constructor(client: HttpClient, authCoreBaseUrl: String) :
        this(authCoreClient = client, authCoreBaseUrl = authCoreBaseUrl, authTokenProvider = { null })

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    suspend fun login(
        identifier: String,
        password: String,
    ): NetworkResult<LoginRawResponse> = runCatchingNetwork {
        val element: JsonElement = authCoreClient.post("$authCoreBaseUrl/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(identifier = identifier, password = password))
        }.body()
        parseLoginResponse(element)
    }

    suspend fun mfaVerify(
        preToken: String,
        code: String? = null,
        recoveryCode: String? = null,
    ): NetworkResult<TokenResponse> = runCatchingNetwork {
        authCoreClient.post("$authCoreBaseUrl/v1/auth/mfa/verify") {
            contentType(ContentType.Application.Json)
            headers { append(HttpHeaders.Authorization, "Bearer $preToken") }
            setBody(MfaVerifyRequest(code = code, recoveryCode = recoveryCode))
        }.body()
    }

    suspend fun refresh(): NetworkResult<TokenResponse> = runCatchingNetwork {
        authCoreClient.post("$authCoreBaseUrl/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
        }.body()
    }

    suspend fun logout(): NetworkResult<Unit> = runCatchingNetwork {
        authCoreClient.post("$authCoreBaseUrl/v1/auth/logout") {
            contentType(ContentType.Application.Json)
        }
        Unit
    }

    /**
     * `POST /v1/auth/register` を叩いて新規アカウントを作成する。
     *
     * 認証ヘッダ不要。トークンは発行されないため、続けて [login] を呼んで access_token を
     * 取得する必要がある。
     */
    suspend fun register(request: RegisterRequest): NetworkResult<RegisterResponse> =
        runCatchingNetwork {
            authCoreClient.post("$authCoreBaseUrl/v1/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
        }

    /**
     * `POST /v1/auth/mfa/register` を叩いて TOTP secret + QR + recoveryCodes を取得する。
     *
     * Bearer access_token 必須。[authTokenProvider] から手動で Authorization を付ける。
     * **非べき等**: 呼び出すたびに新しい secret / QR / recoveryCodes を返し、旧 secret は
     * サーバ側で失効する。クライアントは「再生成」ボタンなど明示的トリガでのみ再呼び出しすること。
     */
    suspend fun mfaRegister(): NetworkResult<MfaRegisterResponse> = runCatchingNetwork {
        val accessToken = authTokenProvider()
        authCoreClient.post("$authCoreBaseUrl/v1/auth/mfa/register") {
            contentType(ContentType.Application.Json)
            if (accessToken != null) {
                headers { append(HttpHeaders.Authorization, "Bearer $accessToken") }
            }
        }.body()
    }

    /**
     * `POST /v1/auth/mfa/enable` を叩いて MFA を有効化する。
     *
     * Bearer access_token 必須。[authTokenProvider] から手動で Authorization を付ける。
     * 成功すると AuthCore 側で `mfa_enabled = true` がコミットされ、以降のログインで MFA
     * 入力が要求されるようになる。失敗時は `TOTP_CODE_INVALID`。
     */
    suspend fun mfaEnable(code: String): NetworkResult<Unit> = runCatchingNetwork {
        val accessToken = authTokenProvider()
        authCoreClient.post("$authCoreBaseUrl/v1/auth/mfa/enable") {
            contentType(ContentType.Application.Json)
            if (accessToken != null) {
                headers { append(HttpHeaders.Authorization, "Bearer $accessToken") }
            }
            setBody(MfaEnableRequest(code = code))
        }
        Unit
    }

    private fun parseLoginResponse(element: JsonElement): LoginRawResponse {
        val obj: JsonObject = element.jsonObject
        val mfaRequired = obj["mfa_required"]
        return if (mfaRequired != null) {
            LoginRawResponse.Mfa(json.decodeFromJsonElement(PreTokenResponse.serializer(), element))
        } else {
            LoginRawResponse.Token(json.decodeFromJsonElement(TokenResponse.serializer(), element))
        }
    }
}

/**
 * `/v1/auth/login` の 200 レスポンスを判別するための内部表現。
 * `mfa_required` キーの有無で 2 パターンに振り分ける。
 */
sealed class LoginRawResponse {
    data class Token(val response: TokenResponse) : LoginRawResponse()
    data class Mfa(val response: PreTokenResponse) : LoginRawResponse()
}
