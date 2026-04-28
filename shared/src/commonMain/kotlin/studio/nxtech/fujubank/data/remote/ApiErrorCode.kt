package studio.nxtech.fujubank.data.remote

enum class ApiErrorCode {
    VALIDATION_FAILED,
    NOT_FOUND,
    INSUFFICIENT_BALANCE,
    UNAUTHENTICATED,
    TOKEN_INACTIVE,
    AUTHCORE_UNAVAILABLE,

    // AuthCore (`fuju-system-authentication` README §3.3) のエラーコード。
    // クライアントは少なくとも以下を分岐できる必要がある。
    INVALID_CREDENTIALS,
    ACCOUNT_LOCKED,
    MFA_REQUIRED,
    TOTP_CODE_INVALID,
    RECOVERY_CODE_INVALID,
    RATE_LIMIT_EXCEEDED,
    TOKEN_EXPIRED,
    TOKEN_INVALID,
    TOKEN_REVOKED,
    MFA_NOT_ENABLED,
    MFA_ALREADY_ENABLED,

    UNKNOWN,
    ;

    companion object {
        fun fromString(raw: String?): ApiErrorCode {
            if (raw == null) return UNKNOWN
            return entries.firstOrNull { it.name == raw } ?: UNKNOWN
        }
    }
}
