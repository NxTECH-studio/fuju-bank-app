import Foundation

/// 取引一覧 / 取引詳細で共有する表示用定数。
///
/// バックエンドの名前解決 API が無いため、相手 / アーティファクトの id は末尾 6 文字に
/// 縮めて表示する。サブタイトル「18秒みつめられた」は Figma 上の固定文で、視線データ
/// 統合は後続タスクで対応する。
enum TransactionDisplay {
    static let shortIdLength = 6
    static let subtitlePlaceholder = "18秒みつめられた"
}
