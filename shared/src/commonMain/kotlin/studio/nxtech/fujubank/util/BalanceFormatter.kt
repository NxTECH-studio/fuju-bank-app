package studio.nxtech.fujubank.util

/**
 * 残高マスク表示時の固定文字列。
 *
 * 数値の整形ロジックは [studio.nxtech.fujubank.format.CurrencyFormatter] に
 * 一元化したため、本ファイルにはマスク表示用の定数のみを残している。
 */
const val MASKED_BALANCE: String = "--,---,---,---"

/**
 * Swift / Obj-C ブリッジ用の関数版。K/N の `const val` は ObjC エクスポート時に
 * 静的プロパティ扱いになるが、安定して呼べる関数経由を Swift 側では推奨する。
 */
fun maskedBalance(): String = MASKED_BALANCE
