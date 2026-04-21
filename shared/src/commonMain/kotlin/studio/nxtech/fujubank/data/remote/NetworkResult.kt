package studio.nxtech.fujubank.data.remote

import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException

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

private suspend fun ResponseException.toApiError(): ApiError {
    val status = response.status.value
    return try {
        val envelope = response.body<ApiErrorEnvelope>()
        ApiError(
            code = ApiErrorCode.fromString(envelope.error.code),
            message = envelope.error.message,
            httpStatus = status,
        )
    } catch (_: Throwable) {
        ApiError(
            code = ApiErrorCode.UNKNOWN,
            message = message ?: "",
            httpStatus = status,
        )
    }
}
