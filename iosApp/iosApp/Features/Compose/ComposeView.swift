import SwiftUI
import UIKit
import ComposeApp

/// Kotlin Multiplatform の `MainViewController()` (Compose Multiplatform UI) を
/// SwiftUI ツリーに埋め込むためのラッパー。
///
/// `ComposeApp` framework は `composeApp/src/iosMain/kotlin/.../MainViewController.kt`
/// で公開している `MainViewController(): UIViewController` を提供する。
/// SwiftUI 側からは `UIViewControllerRepresentable` 経由で UIKit ブリッジに乗せる。
///
/// Task 1 時点では `MainViewController()` は暫定 "Hello iOS" Composable を返す。
/// Task 2 で `App()` 本体の commonMain 化が進むと、この同じラッパー経由で
/// 本体 UI が描画されるようになる。
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        return MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // 現状 Compose 側に渡す state はない。Task 2 以降で SessionState 等を
        // 渡す必要が出たら、その時点で引数化する。
    }
}
