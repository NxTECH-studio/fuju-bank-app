package studio.nxtech.fujubank.features.send

/**
 * 送金フロー Step 1 の送金先候補検索 (`GET /users/search?q=...`) で使う、クエリ入力ガードの
 * 中央定義。Android (`SendFlowViewModel`) と iOS (`ObservableSendFlowViewModel`) の双方から
 * 参照することで「サーバ側 public_id 規約と齟齬がない」状態を 1 箇所で担保する。
 *
 * サーバ側仕様 (`fuju-bank-backend` `User#public_id`): `/\A[a-zA-Z0-9]+\z/` かつ `2..32` 文字。
 *
 * 用途:
 * - クライアントで明らかに弾けるクエリ（空文字 / 短すぎ / 非英数 / 長すぎ）は API を叩く前に
 *   分類して UI 状態を切り替える。
 * - IME composition と相性が悪いため、`onQueryChange` 時点ではなく debounce 後の検索 trigger
 *   段階で判定する（妥協案）。
 */

/** クエリ最小長。サーバ側仕様 (`2..32`) の下限と同じ。 */
const val SEND_SEARCH_QUERY_MIN_LENGTH: Int = 2

/** クエリ最大長。サーバ側仕様 (`2..32`) の上限と同じ。 */
const val SEND_SEARCH_QUERY_MAX_LENGTH: Int = 32

/**
 * サーバ側仕様 `/\A[a-zA-Z0-9]+\z/` と一致する許容文字集合 + 長さ範囲。
 *
 * 長さ範囲を regex 内にも組み込んでおくと、length チェックを忘れて regex だけで判定した
 * 場合でも 33 文字以上 / 1 文字以下を弾ける（defense in depth）。
 */
private val SEND_SEARCH_QUERY_REGEX: Regex =
    Regex("^[a-zA-Z0-9]{$SEND_SEARCH_QUERY_MIN_LENGTH,$SEND_SEARCH_QUERY_MAX_LENGTH}$")

/**
 * 検索クエリ入力ガードの分類結果。
 *
 * - [EMPTY]: 空文字 / 空白のみ。UI は「公開IDを入力してください」ヒントを出す。
 * - [TOO_SHORT]: trim 後 2 文字未満。UI は「2 文字以上で検索してください」ヒントを出す。
 * - [INVALID]: 英数字以外を含む / 32 文字超。UI は「英数字のみ、2〜32 文字」ヒントを出す。
 * - [VALID]: API を叩いて良い形。`SendFlowViewModel.runSearch` はこのケースだけ
 *   `userRepository.searchByPublicId(query.trim())` を発火させる。
 *
 * Kotlin/Native の Obj-C 出力は PascalCase の enum 名を全文字小文字 (例: `TooShort` →
 * `tooshort`) に変換するため、Swift 側で `.tooShort` として参照できるよう
 * SCREAMING_SNAKE_CASE で宣言している (`TOO_SHORT` → `tooShort`)。
 */
enum class SendSearchQueryClassification {
    EMPTY,
    TOO_SHORT,
    INVALID,
    VALID,
}

/**
 * クエリを分類する。`query.trim()` 後の値で判定するため、UI 側で前後空白を気にする必要は無い。
 *
 * Android / iOS 双方の ViewModel から呼ばれることを想定している。Swift 側からは
 * `SendSearchGuardKt.classifySendSearchQuery(query: ...)` の形でアクセスする。
 */
fun classifySendSearchQuery(query: String): SendSearchQueryClassification {
    val trimmed = query.trim()
    return when {
        trimmed.isEmpty() -> SendSearchQueryClassification.EMPTY
        trimmed.length < SEND_SEARCH_QUERY_MIN_LENGTH -> SendSearchQueryClassification.TOO_SHORT
        !SEND_SEARCH_QUERY_REGEX.matches(trimmed) -> SendSearchQueryClassification.INVALID
        else -> SendSearchQueryClassification.VALID
    }
}
