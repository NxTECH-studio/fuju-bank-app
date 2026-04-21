package studio.nxtech.fujubank.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class ApiErrorEnvelope(
    val error: ApiErrorBody,
)

@Serializable
data class ApiErrorBody(
    val code: String,
    val message: String,
)

data class ApiError(
    val code: ApiErrorCode,
    val message: String,
    val httpStatus: Int,
)
