package studio.nxtech.fujubank.navigation

/**
 * RootScaffold が扱うトップレベルのナビゲーション宛先。
 *
 * - [Home] / [Account] はボトムタブで切替える 2 つのタブ画面。
 * - [TransactionHistory] / [Send] はホームの 4 アクションから push される画面（A4 / A5）。
 *
 * MVP では Navigation Compose を導入せず、`var current by remember`
 * の形で簡易にスタックを表現する。A4 / A5 の本実装時に Navigation Compose に
 * 切り替える想定。
 */
sealed interface RootDestination {
    data object Home : RootDestination
    data object Account : RootDestination
    data object TransactionHistory : RootDestination
    data object Send : RootDestination
}
