package studio.nxtech.fujubank.data.remote.api

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import studio.nxtech.fujubank.data.remote.dto.CableEnvelope
import studio.nxtech.fujubank.data.remote.dto.CableIdentifier
import studio.nxtech.fujubank.data.remote.dto.CreditEventDto

class UserChannelClient(
    private val client: HttpClient,
    private val cableUrl: String,
    // アプリスコープ（Android: ProcessLifecycle, iOS: MainScope）を Koin から注入する。
    // 現状 Flow の lifetime は collector 側に委ねているが、将来 shareIn する際に使う。
    @Suppress("unused") private val scope: CoroutineScope,
    private val json: Json = DefaultJson,
) {

    fun subscribe(userId: String): Flow<CreditEventDto> = channelFlow {
        // WebSocket セッション内では send(Frame) と外側 ProducerScope.send(CreditEventDto)
        // が名前衝突するため、Flow への送出は this@channelFlow 経由で明示する。
        val downstream = this
        client.webSocket(cableUrl) {
            val identifier = json.encodeToString(
                CableIdentifier.serializer(),
                CableIdentifier(channel = USER_CHANNEL, userId = userId),
            )
            val subscribeCommand = buildJsonObject {
                put("command", "subscribe")
                put("identifier", identifier)
            }
            send(Frame.Text(json.encodeToString(JsonObject.serializer(), subscribeCommand)))

            for (frame in incoming) {
                if (frame !is Frame.Text) continue
                val credit = parseCreditEvent(frame.readText()) ?: continue
                downstream.send(credit)
            }
        }
    }
        .retryWhen { cause, attempt ->
            if (cause is CancellationException) return@retryWhen false
            // 指数バックオフ: 1s, 2s, 4s, 8s, 16s, 30s(上限)
            val backoffMillis = minOf(MAX_BACKOFF_MILLIS, 1_000L shl attempt.coerceAtMost(5).toInt())
            delay(backoffMillis)
            true
        }
        .buffer()

    internal fun parseCreditEvent(text: String): CreditEventDto? {
        val envelope = runCatching { json.decodeFromString(CableEnvelope.serializer(), text) }
            .getOrNull() ?: return null
        // welcome / ping / confirm_subscription / disconnect などの制御フレームは type が入り、message は null。
        if (envelope.type != null) return null
        val message = envelope.message ?: return null
        val credit = runCatching {
            json.decodeFromJsonElement(CreditEventDto.serializer(), message)
        }.getOrNull() ?: return null
        return credit.takeIf { it.type == CREDIT_EVENT_TYPE }
    }

    private companion object {
        const val USER_CHANNEL = "UserChannel"
        const val CREDIT_EVENT_TYPE = "credit"
        const val MAX_BACKOFF_MILLIS = 30_000L

        val DefaultJson = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}
