package studio.nxtech.fujubank.features.transactions

import studio.nxtech.fujubank.domain.model.Transaction
import studio.nxtech.fujubank.domain.model.TransactionDirection

// 取引一覧 / 取引詳細で共有する表示用ユーティリティ。
//
// バックエンドの名前解決 API が無いため、相手 / アーティファクトの id は末尾 6 文字に
// 縮めて表示する。サブタイトル文言は `direction` から派生させる（視線秒数を載せる
// 拡張は後続タスクで対応）。

internal const val SHORT_ID_LEN = 6

/**
 * 取引行 / 取引詳細のサブタイトル文言。
 *
 * fuju-bank-backend PR #80 で代理 mint (artifact_id = NULL) と Bank 内発行 mint
 * (artifact_id 有り) が同じ Mint kind に混ざるようになったため、artifactId の有無で
 * fuju からの受け取り / アーティファクト発行を区別する。
 */
internal fun transactionRowSubtitle(transaction: Transaction): String = when (transaction.direction) {
    TransactionDirection.Mint ->
        if (transaction.artifactId == null) "fuju からの受け取り" else "発行されたアーティファクト"
    TransactionDirection.Incoming -> "受け取り"
    TransactionDirection.Outgoing -> "送金"
}
