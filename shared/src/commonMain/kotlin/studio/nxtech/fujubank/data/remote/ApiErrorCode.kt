package studio.nxtech.fujubank.data.remote

enum class ApiErrorCode {
    VALIDATION_FAILED,
    NOT_FOUND,
    INSUFFICIENT_BALANCE,
    UNAUTHENTICATED,
    TOKEN_INACTIVE,
    AUTHCORE_UNAVAILABLE,
    MFA_REQUIRED,
    UNKNOWN,
    ;

    companion object {
        fun fromString(raw: String?): ApiErrorCode {
            if (raw == null) return UNKNOWN
            return entries.firstOrNull { it.name == raw } ?: UNKNOWN
        }
    }
}
