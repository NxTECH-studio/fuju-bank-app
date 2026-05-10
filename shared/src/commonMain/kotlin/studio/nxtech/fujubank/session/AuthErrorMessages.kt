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
        else -> "ログインに失敗しました。時間をおいて再試行してください"
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
        else -> "認証に失敗しました。時間をおいて再試行してください"
    }

    /**
     * 通信失敗時の文言。例外そのものは UI に出さず、再試行を促す定型文を返す。
     */
    fun forNetworkFailure(): String =
        "ネットワークエラーが発生しました。通信状況を確認して再試行してください"

    /**
     * `POST /v1/auth/register` のエラーをユーザ向け文言に変換する。
     *
     * 409 系（USER_ALREADY_EXISTS / PUBLIC_ID_ALREADY_EXISTS / PUBLIC_ID_RESERVED）は
     * 入力欄インライン表示用の文言を返す。UI 側で「どの欄に出すか」は呼び出し側が判断する。
     */
    fun forRegister(error: ApiError): String = when (error.code) {
        ApiErrorCode.USER_ALREADY_EXISTS ->
            "このメールアドレスは既に登録されています"
        ApiErrorCode.PUBLIC_ID_ALREADY_EXISTS ->
            "このユーザー ID は既に使われています"
        ApiErrorCode.PUBLIC_ID_RESERVED ->
            "このユーザー ID は使用できません"
        ApiErrorCode.PUBLIC_ID_INVALID ->
            "ユーザー ID は半角英数字 4〜16 文字で入力してください"
        ApiErrorCode.VALIDATION_FAILED ->
            "入力内容を確認してください"
        ApiErrorCode.RATE_LIMIT_EXCEEDED ->
            "リクエストが多すぎます。しばらく待ってから再試行してください"
        else -> "アカウント作成に失敗しました。時間をおいて再試行してください"
    }

    /**
     * `POST /v1/auth/mfa/register` のエラーをユーザ向け文言に変換する。
     *
     * register 直後の自動呼び出しでも失敗する可能性があるため、再試行・再生成を促す定型文に倒す。
     */
    fun forMfaSetup(error: ApiError): String = when (error.code) {
        ApiErrorCode.MFA_ALREADY_ENABLED ->
            "二段階認証は既に有効です"
        ApiErrorCode.UNAUTHENTICATED, ApiErrorCode.TOKEN_EXPIRED, ApiErrorCode.TOKEN_INVALID ->
            "認証セッションの有効期限が切れました。最初からやり直してください"
        ApiErrorCode.RATE_LIMIT_EXCEEDED ->
            "リクエストが多すぎます。しばらく待ってから再試行してください"
        else -> "二段階認証の準備に失敗しました。時間をおいて再試行してください"
    }

    /**
     * `POST /v1/auth/mfa/enable` のエラーをユーザ向け文言に変換する。
     */
    fun forMfaEnable(error: ApiError): String = when (error.code) {
        ApiErrorCode.TOTP_CODE_INVALID ->
            "認証コードが正しくありません"
        ApiErrorCode.MFA_ALREADY_ENABLED ->
            "二段階認証は既に有効です"
        ApiErrorCode.UNAUTHENTICATED, ApiErrorCode.TOKEN_EXPIRED, ApiErrorCode.TOKEN_INVALID ->
            "認証セッションの有効期限が切れました。最初からやり直してください"
        ApiErrorCode.RATE_LIMIT_EXCEEDED ->
            "リクエストが多すぎます。しばらく待ってから再試行してください"
        else -> "二段階認証の有効化に失敗しました。時間をおいて再試行してください"
    }
}
