package studio.nxtech.fujubank.session

import studio.nxtech.fujubank.data.remote.ApiError
import studio.nxtech.fujubank.data.remote.ApiErrorCode

/**
 * 認証フローのエラーをユーザ向け日本語メッセージに変換する純粋関数群。
 *
 * UI 層（SwiftUI / Compose）からそのまま呼ぶことで iOS / Android の文言を揃える。
 * `Retry-After` などサーバヘッダ依存の値は呼び出し側で接頭・接尾するため、ここでは扱わない。
 */
object AuthErrorMessages {

    /**
     * `POST /v1/auth/login` のエラーをユーザ向け文言に変換する。
     */
    fun forLogin(error: ApiError): String = when (error.code) {
        ApiErrorCode.INVALID_CREDENTIALS ->
            "メールアドレス/公開ID または パスワードが間違っています"
        ApiErrorCode.ACCOUNT_LOCKED ->
            "ログイン試行が多すぎます。しばらく待ってから再試行してください"
        ApiErrorCode.RATE_LIMIT_EXCEEDED ->
            "リクエストが多すぎます。しばらく待ってから再試行してください"
        ApiErrorCode.VALIDATION_FAILED ->
            "入力内容を確認してください"
        else -> "ログインに失敗しました（${error.code.name}）"
    }

    /**
     * `POST /v1/auth/mfa/verify` のエラーをユーザ向け文言に変換する。
     */
    fun forMfa(error: ApiError): String = when (error.code) {
        ApiErrorCode.TOTP_CODE_INVALID ->
            "認証コードが正しくありません"
        ApiErrorCode.RECOVERY_CODE_INVALID ->
            "リカバリコードが正しくありません"
        ApiErrorCode.TOKEN_EXPIRED ->
            "認証セッションの有効期限が切れました。最初からやり直してください"
        ApiErrorCode.RATE_LIMIT_EXCEEDED ->
            "リクエストが多すぎます。しばらく待ってから再試行してください"
        else -> "認証に失敗しました（${error.code.name}）"
    }

    /**
     * 通信失敗時の文言。例外そのものは UI に出さず、再試行を促す定型文を返す。
     */
    fun forNetworkFailure(): String =
        "ネットワークエラーが発生しました。通信状況を確認して再試行してください"
}
