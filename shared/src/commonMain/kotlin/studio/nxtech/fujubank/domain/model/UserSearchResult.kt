package studio.nxtech.fujubank.domain.model

/**
 * 送金先候補の表示用ドメインモデル。
 *
 * - [id]: bank 内部の user 主キー（文字列化済み）。`LedgerRepository.transfer` の `to` に渡す。
 * - [publicId]: 送金候補リスト / bottom sheet で末尾 4 桁を識別子として表示する。
 * - [name]: 表示名。
 * - [iconUrl]: 円形アバター URL。null 可。
 */
data class UserSearchResult(
    val id: String,
    val publicId: String,
    val name: String,
    val iconUrl: String?,
)
