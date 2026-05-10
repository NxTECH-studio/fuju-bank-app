package studio.nxtech.fujubank.navigation

/**
 * RootScaffold が扱うトップレベルのナビゲーション宛先。
 *
 * - [Home] / [Account] はボトムタブで切替える 2 つのタブ画面。
 * - [TransactionHistory] / [TransactionDetail] / [Send] はサブ画面（戻るで Home / TransactionHistory に復帰）。
 *
 * MVP では Navigation Compose を導入せず、`var current by remember`
 * の形で簡易にスタックを表現する。タップ対象の `Transaction` は別途 `remember`
 * で保持し、本 enum には含めない（rememberSaveable Saver の複雑さを避ける）。
 */
sealed interface RootDestination {
    data object Home : RootDestination
    data object Account : RootDestination
    data object TransactionHistory : RootDestination
    data object TransactionDetail : RootDestination

    /** 送金フロー Step 1: 送金先選択（表示名検索 + bottom sheet 確認モーダル）。 */
    data object SendRecipient : RootDestination

    /** 送金フロー Step 2: 金額入力（プレビュー + 確認 AlertDialog）。 */
    data object SendAmount : RootDestination

    /** 旧プレースホルダ画面の名残。フッタータブから入った直後の初期画面として SendRecipient を出す。 */
    data object Send : RootDestination

    /** 通知設定（Figma `718:7332`）。Account タブ配下のサブ画面。 */
    data object NotificationSettings : RootDestination

    /** プライバシー設定（準備中画面）。Account タブ配下のサブ画面。 */
    data object PrivacySettings : RootDestination

    /** プライバシーポリシー本文。PrivacySettings 配下のドリルダウン画面。 */
    data object PrivacyPolicy : RootDestination

    /** 利用規約本文。PrivacySettings 配下のドリルダウン画面。 */
    data object TermsOfService : RootDestination

    /** パスワード変更画面（Figma `799:13327`）。Account タブ配下のサブ画面。 */
    data object PasswordChange : RootDestination
}
