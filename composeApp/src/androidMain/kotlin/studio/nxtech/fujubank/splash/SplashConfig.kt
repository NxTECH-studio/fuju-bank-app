package studio.nxtech.fujubank.splash

/**
 * 起動スプラッシュの最低表示時間。
 *
 * `SessionStore.bootstrap()` がこれより早く完了してもロゴを最低この時間は見せる。
 * iOS 側の `SplashConfig.minDuration` (秒) と値を揃えること。
 *
 * Figma でアニメーション尺が指定された場合は両プラットフォームで同時に更新する。
 */
object SplashConfig {
    const val MIN_DURATION_MS: Long = 2000L
}
