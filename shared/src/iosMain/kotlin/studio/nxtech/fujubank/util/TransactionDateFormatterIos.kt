package studio.nxtech.fujubank.util

import kotlin.time.Instant

/**
 * Swift から取引日時を整形するための簡易ファサード。
 * commonMain の `formatTransactionDate` は default 引数を持つが、Kotlin/Native の
 * Swift エクスポート時には default が落ちるため、引数無しで呼べる薄いラッパを提供する。
 */
fun formatTransactionDateForIos(instant: Instant): String = formatTransactionDate(instant)
