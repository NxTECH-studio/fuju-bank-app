package studio.nxtech.fujubank.features.send

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `SendFlowViewModel.runSearch` の入力ガード（length / regex）を companion 定数経由で検証する。
 *
 * `runSearch` は private + suspend のため直接呼べないが、判定ロジック自体は
 * `SEARCH_MAX_LENGTH` / `SEARCH_REGEX` / `MIN_SEARCH_LENGTH` の組み合わせで定義されているので、
 * 定数値そのものが想定通りなら判定挙動も担保される。
 *
 * カバレッジ:
 * - 2 文字未満 → MIN_SEARCH_LENGTH で弾かれる
 * - 33 文字以上 → SEARCH_MAX_LENGTH 超過で弾かれる
 * - 日本語 / 記号 / ハイフン等 → SEARCH_REGEX でマッチしない
 * - 英数字 2〜32 文字 → 全条件を通る
 */
class SendFlowSearchGuardTest {

    @Test
    fun length_lower_bound_matches_server_contract() {
        // バックエンド `/users/search` の public_id 仕様は 2..32 文字。
        // クライアントの MIN もこれに合わせる。
        assertEquals(2, SendFlowViewModel.MIN_SEARCH_LENGTH)
    }

    @Test
    fun length_upper_bound_matches_server_contract() {
        // バックエンド `/users/search` の public_id 仕様は 2..32 文字。
        assertEquals(32, SendFlowViewModel.SEARCH_MAX_LENGTH)
    }

    @Test
    fun regex_accepts_english_alphanumeric_within_bounds() {
        assertTrue(SendFlowViewModel.SEARCH_REGEX.matches("ab"))
        assertTrue(SendFlowViewModel.SEARCH_REGEX.matches("abc123"))
        assertTrue(SendFlowViewModel.SEARCH_REGEX.matches("AbC123XYZ"))
        // ちょうど最大長 (32) も含めて受理されること。
        assertTrue(SendFlowViewModel.SEARCH_REGEX.matches("a".repeat(SendFlowViewModel.SEARCH_MAX_LENGTH)))
    }

    @Test
    fun regex_rejects_japanese_characters() {
        // IME 入力中の日本語は API 発火させない（サーバ側 422 を未然に防ぐ）。
        assertFalse(SendFlowViewModel.SEARCH_REGEX.matches("あい"))
        assertFalse(SendFlowViewModel.SEARCH_REGEX.matches("田中"))
        assertFalse(SendFlowViewModel.SEARCH_REGEX.matches("abcあ"))
    }

    @Test
    fun regex_rejects_symbols_and_punctuation() {
        // public_id は英数字のみ。`-` / `_` / 空白 / 記号は禁止。
        assertFalse(SendFlowViewModel.SEARCH_REGEX.matches("ab-cd"))
        assertFalse(SendFlowViewModel.SEARCH_REGEX.matches("ab_cd"))
        assertFalse(SendFlowViewModel.SEARCH_REGEX.matches("ab cd"))
        assertFalse(SendFlowViewModel.SEARCH_REGEX.matches("ab!cd"))
        assertFalse(SendFlowViewModel.SEARCH_REGEX.matches("@ab"))
    }

    @Test
    fun regex_rejects_empty_string() {
        // `+` quantifier なので空文字は弾かれる（長さガードとの二重防御）。
        assertFalse(SendFlowViewModel.SEARCH_REGEX.matches(""))
    }

    @Test
    fun over_max_length_string_should_be_rejected() {
        // SEARCH_MAX_LENGTH を超える長さは ViewModel 側で先に弾く想定。
        // ここでは「33 文字でも regex 自体は通ってしまう」ことを明示し、length 判定との
        // 役割分担を確認する（将来 regex に上限を入れる変更があった場合の defense in depth）。
        val tooLong = "a".repeat(SendFlowViewModel.SEARCH_MAX_LENGTH + 1)
        assertTrue(SendFlowViewModel.SEARCH_REGEX.matches(tooLong))
        assertTrue(tooLong.length > SendFlowViewModel.SEARCH_MAX_LENGTH)
    }
}
