package studio.nxtech.fujubank.data.remote

enum class ApiErrorCode {
    VALIDATION_FAILED,
    NOT_FOUND,
    INSUFFICIENT_BALANCE,
    // 送金 API（POST /ledger/transfer）が宛先 user を解決できなかった際に返す。
    // bank backend 側で RECIPIENT_NOT_FOUND として実装される想定。クライアントは
    // 「送り先が見つかりません」のような UI を出してユーザーに ID 再入力を促す。
    RECIPIENT_NOT_FOUND,
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
