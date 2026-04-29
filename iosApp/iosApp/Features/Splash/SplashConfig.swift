import Foundation

/// 起動スプラッシュの最低表示時間。
///
/// `SessionStore.bootstrap()` がこれより早く完了してもロゴを最低この時間は見せる。
/// Android 側 `SplashConfig.MIN_DURATION_MS` (ミリ秒) と値を揃えること。
enum SplashConfig {
    static let minDuration: TimeInterval = 2.0
}
