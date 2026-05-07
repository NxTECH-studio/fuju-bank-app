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
    // 旧スキーマでは AuthCore の external_user_id を `sub` として返していたが、
    // 現行 (2026-05) の `/users/me` は返さないため nullable にする。
    @SerialName("sub")
    val subject: String? = null,
    // bank サーバ側に表示名カラムが入る前提のフォワード互換。現状は未提供のため
    // nullable + default null で受け、AccountHub 側ではフォールバックで埋める。
    val name: String? = null,
    // bigint: クライアント側は小数計算に関与しないため Long で受ける。
    @SerialName("balance_fuju")
    val balanceFuju: Long,
    // ISO8601 文字列。Instant への変換は Repository 層で行う。
    @SerialName("created_at")
    val createdAt: String,
)
