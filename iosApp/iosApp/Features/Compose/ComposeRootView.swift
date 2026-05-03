import SwiftUI

/// Compose 経路の動作確認用ルート View。
///
/// Task 1 のスコープでは「iOS 上で Compose Multiplatform UI が起動する経路を確立する」
/// ことが目的のため、`SplashGate` -> `AppRoot` の既存 SwiftUI 経路は壊さずに残し、
/// この View はビルドフラグまたは環境変数で切替えて表示する。Task 2 完了後にデフォルト
/// 切替する想定。
///
/// 切替方法（Task 1 時点）:
/// - 環境変数 `FUJUBANK_USE_COMPOSE=1` を Xcode Scheme の "Run > Arguments >
///   Environment Variables" に追加すると、`iOSApp.swift` がこの View を表示する。
/// - 設定なし、または `0` の場合は従来通り `SplashGate` を表示する。
struct ComposeRootView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}
