package studio.nxtech.fujubank.data.remote

import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed class NetworkResult<out T> {
    data class Success<T>(val value: T) : NetworkResult<T>()

    data class Failure(val error: ApiError) : NetworkResult<Nothing>()

    data class NetworkFailure(val cause: Throwable) : NetworkResult<Nothing>()
}

inline fun <T, R> NetworkResult<T>.map(transform: (T) -> R): NetworkResult<R> = when (this) {
    is NetworkResult.Success -> NetworkResult.Success(transform(value))
    is NetworkResult.Failure -> this
    is NetworkResult.NetworkFailure -> this
}

suspend fun <T> runCatchingNetwork(block: suspend () -> T): NetworkResult<T> {
    return try {
        NetworkResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: ResponseException) {
        NetworkResult.Failure(e.toApiError())
    } catch (e: Throwable) {
        NetworkResult.NetworkFailure(e)
    }
}

/**
 * バックエンドのエラー JSON を [ApiError] に変換する。
 *
 * bank API は `{"error": {"code": "X", "message": "Y"}}` のネスト形式、
 * AuthCore は `{"error": "X", "message": "Y"}` のフラット形式を返す。両方を受け入れる。
 * 構造が一致しない・パース失敗した場合は [ApiErrorCode.UNKNOWN] にフォールバックする。
 */
private suspend fun ResponseException.toApiError(): ApiError {
    val status = response.status.value
    return try {
        val obj = response.body<JsonElement>().jsonObject
        val (codeStr, msg) = parseErrorEnvelope(obj)
        ApiError(
            code = ApiErrorCode.fromString(codeStr.orEmpty()),
            message = msg.orEmpty(),
            httpStatus = status,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        ApiError(
            code = ApiErrorCode.UNKNOWN,
            message = message ?: "",
            httpStatus = status,
        )
    }
}

private fun parseErrorEnvelope(obj: JsonObject): Pair<String?, String?> {
    return when (val errorField = obj["error"]) {
        is JsonObject -> {
            // bank API のネスト形式。
            errorField["code"]?.jsonPrimitive?.contentOrNull to
                errorField["message"]?.jsonPrimitive?.contentOrNull
        }
        is JsonPrimitive -> {
            // AuthCore のフラット形式。`message` はトップレベルにある。
            errorField.contentOrNull to obj["message"]?.jsonPrimitive?.contentOrNull
        }
        else -> null to null
    }
}
