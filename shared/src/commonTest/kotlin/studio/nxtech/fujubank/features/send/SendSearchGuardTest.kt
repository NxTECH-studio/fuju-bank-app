package studio.nxtech.fujubank.features.send

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `classifySendSearchQuery` の分類仕様を検証する。サーバ側 public_id 規約
 * (`/\A[a-zA-Z0-9]+\z/` `2..32`) との整合を 1 箇所で担保するためのテスト。
 *
 * Android / iOS 双方の ViewModel が同じ関数を参照する設計なので、ここを green に保てば
 * 両プラットフォームの入力ガード挙動を同時に担保できる。
 */
class SendSearchGuardTest {

    @Test
    fun length_bounds_match_server_contract() {
        // バックエンド `/users/search` の public_id 仕様は 2..32 文字。
        assertEquals(2, SEND_SEARCH_QUERY_MIN_LENGTH)
        assertEquals(32, SEND_SEARCH_QUERY_MAX_LENGTH)
    }

    @Test
    fun empty_or_whitespace_only_returns_empty() {
        assertEquals(SendSearchQueryClassification.EMPTY, classifySendSearchQuery(""))
        assertEquals(SendSearchQueryClassification.EMPTY, classifySendSearchQuery("   "))
        // タブ・改行混じりの空白は IME や貼り付けで稀に流入する。
        assertEquals(SendSearchQueryClassification.EMPTY, classifySendSearchQuery("\t\n"))
    }

    @Test
    fun one_character_returns_too_short() {
        // 1 文字英数は短すぎ扱い（前後空白は trim される）。
        assertEquals(SendSearchQueryClassification.TOO_SHORT, classifySendSearchQuery("a"))
        assertEquals(SendSearchQueryClassification.TOO_SHORT, classifySendSearchQuery("1"))
        assertEquals(SendSearchQueryClassification.TOO_SHORT, classifySendSearchQuery(" Z "))
    }

    @Test
    fun english_alphanumeric_within_bounds_returns_valid() {
        assertEquals(SendSearchQueryClassification.VALID, classifySendSearchQuery("ab"))
        assertEquals(SendSearchQueryClassification.VALID, classifySendSearchQuery("abc123"))
        assertEquals(SendSearchQueryClassification.VALID, classifySendSearchQuery("AbC123XYZ"))
        // ちょうど最大長 (32) も Valid。
        assertEquals(
            SendSearchQueryClassification.VALID,
            classifySendSearchQuery("a".repeat(SEND_SEARCH_QUERY_MAX_LENGTH)),
        )
        // 前後の空白は trim 後に評価されるため Valid。
        assertEquals(SendSearchQueryClassification.VALID, classifySendSearchQuery("  abc  "))
    }

    @Test
    fun japanese_characters_return_invalid() {
        // IME 入力中の日本語は API を発火させない（サーバ側 422 を未然に防ぐ）。
        assertEquals(SendSearchQueryClassification.INVALID, classifySendSearchQuery("あい"))
        assertEquals(SendSearchQueryClassification.INVALID, classifySendSearchQuery("田中"))
        assertEquals(SendSearchQueryClassification.INVALID, classifySendSearchQuery("abcあ"))
    }

    @Test
    fun symbols_and_punctuation_return_invalid() {
        // public_id は英数字のみ。`-` / `_` / 空白挿入 / 記号は禁止。
        assertEquals(SendSearchQueryClassification.INVALID, classifySendSearchQuery("ab-cd"))
        assertEquals(SendSearchQueryClassification.INVALID, classifySendSearchQuery("ab_cd"))
        assertEquals(SendSearchQueryClassification.INVALID, classifySendSearchQuery("ab cd"))
        assertEquals(SendSearchQueryClassification.INVALID, classifySendSearchQuery("ab!cd"))
        assertEquals(SendSearchQueryClassification.INVALID, classifySendSearchQuery("@ab"))
    }

    @Test
    fun over_max_length_returns_invalid() {
        // 33 文字以上は regex 内の `{2,32}` で弾かれる（defense in depth）。
        val tooLong = "a".repeat(SEND_SEARCH_QUERY_MAX_LENGTH + 1)
        assertEquals(SendSearchQueryClassification.INVALID, classifySendSearchQuery(tooLong))
        // ペースト想定の極端な長さでも Invalid に倒れる（API 発火を抑止）。
        assertEquals(
            SendSearchQueryClassification.INVALID,
            classifySendSearchQuery("a".repeat(1000)),
        )
    }
}
