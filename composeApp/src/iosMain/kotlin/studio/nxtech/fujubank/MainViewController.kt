package studio.nxtech.fujubank

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * iOS 側 (`iosApp/iosApp/iOSApp.swift` または SwiftUI ラッパー) から呼び出される
 * Compose Multiplatform のエントリポイント。
 *
 * Task 1 では「iOS で Compose UI が起動するところまで」を確立するための最小実装として、
 * [PlaceholderApp] を表示する。Task 2 以降で `App()` 本体の commonMain 化が完了したら、
 * 本関数の中身を `App()` 呼び出しに差し替える。
 *
 * Koin の初期化は `iosApp/iOSApp.swift` の `KoinIosKt.doInitKoin()` で行われる前提のため、
 * この関数では一切 Koin を触らない（重複初期化防止）。
 */
@Suppress("FunctionName")
fun MainViewController(): UIViewController = ComposeUIViewController {
    PlaceholderApp()
}

/**
 * Compose 経路が iOS 側で正しく描画されることを確認するための暫定 Composable。
 * Task 2 で削除予定。
 */
@Composable
private fun PlaceholderApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("Hello iOS from Compose Multiplatform")
            }
        }
    }
}
