package studio.nxtech.fujubank.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import studio.nxtech.fujubank.domain.model.TransactionKind

@Serializable
data class TransactionListResponse(
    @SerialName("data")
    val data: List<TransactionDto>,
)

/**
 * server (`/users/:id/transactions`) が返す取引 1 件分のペイロード。
 *
 * direction / counterparty_user_id は server で確定済みのため、client 側で
 * `myUserId` と `from_user_id`/`to_user_id` を比較するロジックは廃止した。
 *
 * server の追加フィールド (`entry_id` / `metadata` / `created_at`) は
 * 現状利用していないため、`Json { ignoreUnknownKeys = true }` 経由で無視する。
 * `memo` のみ取引履歴 / 詳細表示で使うため field として受け取る。
 */
@Serializable
data class TransactionDto(
    @SerialName("transaction_id")
    val id: String,
    @SerialName("transaction_kind")
    val kind: TransactionKind,
    @SerialName("direction")
    val direction: TransactionDirectionWire,
    // server は entry の amount を絶対値で返す（常に正）。client 側は小数計算に
    // 関与しないため Long で受ける。
    val amount: Long,
    // 代理 mint (PR #80) では NULL。Bank 内 mint では artifact PK が入る。
    @SerialName("artifact_id")
    val artifactId: String?,
    // transfer のときだけ非 null。mint は常に null。
    @SerialName("counterparty_user_id")
    val counterpartyUserId: String?,
    // transfer のとき AuthCore 公開ハンドル (public_id) を返す。mint は null。
    // UI 表示は `@{publicId}` を採用するため bank PK ではなくこちらを使う。
    @SerialName("counterparty_public_id")
    val counterpartyPublicId: String?,
    // ISO8601 文字列。Instant への変換は Repository 層で行う。
    @SerialName("occurred_at")
    val occurredAt: String,
    // 送金時に付与される任意メモ（最大 80 文字）。mint や memo 未指定 transfer は null。
    @SerialName("memo")
    val memo: String? = null,
)

/**
 * server が返す `direction` の wire 表現。entry.amount > 0 なら `credit`、< 0 なら `debit`。
 * domain `TransactionDirection` への解決は Repository 層で `kind` と組み合わせて行う。
 */
@Serializable
enum class TransactionDirectionWire {
    @SerialName("credit")
    CREDIT,

    @SerialName("debit")
    DEBIT,
}
