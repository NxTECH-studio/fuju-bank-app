package studio.nxtech.fujubank.splash

/**
 * 起動スプラッシュの最低表示時間（ミリ秒）。
 *
 * `SessionStore.bootstrap()` がこれより早く完了してもロゴを最低この時間は見せる。
 * Android (`MainActivity`) と iOS (`SplashGate`) の両プラットフォームから参照する
 * 単一の真実源として shared/commonMain に集約する。Figma 側のアニメーション尺指定に
 * 合わせて値を更新する場合はここだけを変更すれば両プラットフォームに反映される。
 */
object SplashConfig {
    const val MIN_DURATION_MS: Long = 2000L
}
