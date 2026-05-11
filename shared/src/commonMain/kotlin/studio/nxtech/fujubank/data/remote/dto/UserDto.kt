package studio.nxtech.fujubank.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateUserRequest(
    @SerialName("sub")
    val subject: String,
)

@Serializable
data class UserResponse(
    // bank 側のユーザー主キーは整数。クライアントの domain `User.id` は文字列で扱うため、
    // `toDomain()` 側で `id.toString()` に変換する。
    val id: Long,
    // AuthCore の external_user_id (ULID, 26 文字 Crockford Base32)。bank-backend は
    // 2026-05 以降 `serialize_user` に `sub: user.external_user_id` を含めるよう揃った
    // (server-bank-24 / PR #103) ため、`/users/me` / `/users/:id` のいずれも必ず返す。
    // null で来た場合は deserialize 時に MissingFieldException → NetworkFailure に倒れる
    // (fail-closed)。SessionStore.userId の源泉として `/ledger/transfer` 等で使う。
    @SerialName("sub")
    val subject: String,
    // bigint: クライアント側は小数計算に関与しないため Long で受ける。
    @SerialName("balance_fuju")
    val balanceFuju: Long,
    // ISO8601 文字列。Instant への変換は Repository 層で行う。
    @SerialName("created_at")
    val createdAt: String,
)
