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
 * - refresh / logout / mfaVerify / register は cookie 認証または認証不要なので
 *   `Auth` プラグイン無しの専用クライアント [authCoreClient] (= AUTHCORE_CLIENT_QUALIFIER)
 *   を使う。これを Auth 付きクライアントで叩くと refresh が 401 を返したときに
 *   `refreshTokens` ブロックが自己再帰起動して deadlock する。
 * - mfaRegister / mfaEnable は **既に access_token を発行済みのユーザ** が叩くため、
 *   `Auth` プラグイン付きの bank/AuthCore 共通クライアント [bearerClient] を使い、
 *   Authorization ヘッダを自動付与させる。逆を選ぶと 401→refresh ループに入る。
 */
class AuthApi(
    private val authCoreClient: HttpClient,
    private val bearerClient: HttpClient,
    private val authCoreBaseUrl: String,
) {
    /**
     * 単一の HttpClient で両ロールを兼ねるテスト向けコンビニエンスコンストラクタ。
     *
     * 本番では [AuthModule] が AUTHCORE_CLIENT_QUALIFIER 付きクライアントと
     * 通常クライアントを別々に渡すこと（refresh 自己再帰防止のため）。
     */
    constructor(client: HttpClient, authCoreBaseUrl: String) :
        this(authCoreClient = client, bearerClient = client, authCoreBaseUrl = authCoreBaseUrl)

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
     * 認証ヘッダ不要。トークンは発行されないため、続けて `login()` を呼んで access_token を
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
     * Bearer access_token 必須。サーバ側は呼び出すたびに新しい secret を生成し旧 secret を
     * 失効させる（非べき等）。クライアントは戻るボタンで再入する UX を作らないこと。
     */
    suspend fun mfaRegister(): NetworkResult<MfaRegisterResponse> = runCatchingNetwork {
        bearerClient.post("$authCoreBaseUrl/v1/auth/mfa/register") {
            contentType(ContentType.Application.Json)
        }.body()
    }

    /**
     * `POST /v1/auth/mfa/enable` を叩いて MFA を有効化する。
     *
     * Bearer access_token 必須。`mfa/register` で取得した secret に対する 6 桁 TOTP が
     * 一致すると `mfa_enabled = true` がコミットされる。失敗時は `TOTP_CODE_INVALID`。
     */
    suspend fun mfaEnable(code: String): NetworkResult<Unit> = runCatchingNetwork {
        bearerClient.post("$authCoreBaseUrl/v1/auth/mfa/enable") {
            contentType(ContentType.Application.Json)
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
