package studio.nxtech.fujubank.domain.model

/**
 * 送金先候補の表示用ドメインモデル。
 *
 * - [id]: bank 内部の user 主キー（文字列化済み）。`LedgerRepository.transfer` の `to` に渡す。
 * - [publicId]: ハンドル。送金候補リスト / bottom sheet では `@{publicId}` 形式で主表示する。
 * - [iconUrl]: 円形アバター URL。null 可（現状常に null）。
 */
data class UserSearchResult(
    val id: String,
    val publicId: String,
    val iconUrl: String?,
)
