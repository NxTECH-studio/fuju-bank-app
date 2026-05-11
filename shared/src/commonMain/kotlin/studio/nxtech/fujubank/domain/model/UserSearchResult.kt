package studio.nxtech.fujubank.domain.model

/**
 * 送金先候補の表示用ドメインモデル。
 *
 * - [id]: AuthCore の ULID (= bank の `external_user_id`、26 文字 Crockford Base32)。
 *   bank-backend PR #101 以降、`/users/search` のレスポンス `id` は AuthCore 由来 ID になった。
 *   `LedgerRepository.transfer` の `to` (= `to_user_id`) にそのまま渡す。
 *   bank 内部 PK 前提のエンドポイント (`/users/:id/transactions` 等) に渡してはいけない。
 * - [publicId]: ハンドル。送金候補リスト / bottom sheet では `@{publicId}` 形式で主表示する。
 * - [iconUrl]: 円形アバター URL。null 可（現状常に null）。
 */
data class UserSearchResult(
    val id: String,
    val publicId: String,
    val iconUrl: String?,
)
