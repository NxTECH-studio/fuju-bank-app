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
    data object Send : RootDestination

    /** 通知設定（Figma `718:7332`）。Account タブ配下のサブ画面。 */
    data object NotificationSettings : RootDestination

    /** プライバシー設定（準備中画面）。Account タブ配下のサブ画面。 */
    data object PrivacySettings : RootDestination

    /** アカウント情報変更（準備中画面）。Account タブ配下のサブ画面。 */
    data object AccountEdit : RootDestination
}
