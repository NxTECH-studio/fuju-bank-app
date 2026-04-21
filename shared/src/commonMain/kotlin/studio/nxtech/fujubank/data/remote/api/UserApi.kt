package studio.nxtech.fujubank.data.remote.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.remote.dto.CreateUserRequest
import studio.nxtech.fujubank.data.remote.dto.TransactionListResponse
import studio.nxtech.fujubank.data.remote.dto.UserResponse
import studio.nxtech.fujubank.data.remote.runCatchingNetwork

class UserApi(private val client: HttpClient) {

    suspend fun create(request: CreateUserRequest): NetworkResult<UserResponse> =
        runCatchingNetwork {
            client.post("/users") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
        }

    suspend fun get(userId: String): NetworkResult<UserResponse> =
        runCatchingNetwork {
            client.get("/users/$userId").body()
        }

    suspend fun transactions(userId: String): NetworkResult<TransactionListResponse> =
        runCatchingNetwork {
            client.get("/users/$userId/transactions").body()
        }
}
