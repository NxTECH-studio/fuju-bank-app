package studio.nxtech.fujubank.data.remote

data class ApiError(
    val code: ApiErrorCode,
    val message: String,
    val httpStatus: Int,
)
