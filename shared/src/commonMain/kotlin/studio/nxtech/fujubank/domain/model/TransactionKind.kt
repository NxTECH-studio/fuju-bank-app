package studio.nxtech.fujubank.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// 取引種別のドメイン表現。@SerialName でワイヤ上の表現（snake_case）も
// 保持しているため、DTO からはそのままシリアライザ経由でデコードできる。
@Serializable
enum class TransactionKind {
    @SerialName(WIRE_MINT)
    MINT,

    @SerialName(WIRE_TRANSFER)
    TRANSFER,

    ;

    companion object {
        fun fromWireName(name: String): TransactionKind? = when (name) {
            WIRE_MINT -> MINT
            WIRE_TRANSFER -> TRANSFER
            else -> null
        }
    }
}

// @SerialName と fromWireName で同じリテラルを共有し、ワイヤ形式の
// 文字列を 1 箇所で管理する。
private const val WIRE_MINT = "mint"
private const val WIRE_TRANSFER = "transfer"
