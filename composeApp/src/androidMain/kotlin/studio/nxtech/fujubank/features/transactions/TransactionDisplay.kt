package studio.nxtech.fujubank.features.transactions

// 取引一覧 / 取引詳細で共有する表示用定数。
//
// バックエンドの名前解決 API が無いため、相手 / アーティファクトの id は末尾 6 文字に
// 縮めて表示する。サブタイトル「18秒みつめられた」は Figma 上の固定文で、視線データ
// 統合は後続タスクで対応する。

internal const val SHORT_ID_LEN = 6

internal const val TRANSACTION_ROW_SUBTITLE_PLACEHOLDER = "18秒みつめられた"
