package studio.nxtech.fujubank.data.remote.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.remote.dto.TransferRequest
import studio.nxtech.fujubank.data.remote.dto.TransferResponse
import studio.nxtech.fujubank.data.remote.runCatchingNetwork
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class LedgerApi(
    private val client: HttpClient,
    private val idempotencyKeyFactory: () -> String = { Uuid.random().toString() },
) {

    suspend fun transfer(
        fromUserId: String,
        toUserId: String,
        amount: Long,
        memo: String? = null,
        idempotencyKey: String = idempotencyKeyFactory(),
    ): NetworkResult<TransferResponse> = runCatchingNetwork {
        client.post("/ledger/transfer") {
            contentType(ContentType.Application.Json)
            header("Idempotency-Key", idempotencyKey)
            setBody(
                TransferRequest(
                    fromUserId = fromUserId,
                    toUserId = toUserId,
                    amount = amount,
                    idempotencyKey = idempotencyKey,
                    memo = memo,
                ),
            )
        }.body()
    }
}
