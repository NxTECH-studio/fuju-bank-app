import Foundation
import Shared

/// 取引一覧 / 取引詳細で共有する表示用ユーティリティ。
///
/// バックエンドの名前解決 API が無いため、相手 / アーティファクトの id は末尾 6 文字に
/// 縮めて表示する。サブタイトル文言は `direction` から派生させる（視線秒数を載せる
/// 拡張は後続タスクで対応）。
enum TransactionDisplay {
    static let shortIdLength = 6

    /// 取引行 / 取引詳細のサブタイトル文言。
    ///
    /// fuju-bank-backend PR #80 で代理 mint (artifact_id = NULL) と Bank 内発行 mint
    /// (artifact_id 有り) が同じ Mint kind に混ざるようになったため、artifactId の有無で
    /// fuju からの受け取り / アーティファクト発行を区別する。Android の `transactionRowSubtitle`
    /// と完全に一致させる。
    static func rowSubtitle(transaction: Shared.Transaction) -> String {
        let direction = transaction.direction
        if direction == TransactionDirection.mint {
            return transaction.artifactId == nil ? "fuju からの受け取り" : "発行されたアーティファクト"
        } else if direction == TransactionDirection.incoming {
            return "受け取り"
        } else {
            return "送金"
        }
    }
}
