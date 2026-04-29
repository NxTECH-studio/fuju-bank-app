import SwiftUI

/// LaunchScreen (Info.plist `UILaunchScreen`) と同じ見た目を SwiftUI で再現するビュー。
///
/// - 背景色は Asset Catalog の `FujuSplashBackground` (Info.plist の `UIColorName` と一致)。
/// - 中央に `FujuLogo` (icon + "fuju pay" wordmark の合成、Figma node 175-2457) を表示。
///
/// 切り替わり時のチラつきを防ぐため、Info.plist UILaunchScreen と
/// 背景色・ロゴ位置を揃えること。
struct SplashView: View {
    var body: some View {
        ZStack {
            Color("FujuSplashBackground")
                .ignoresSafeArea()
            Image("FujuLogo")
                .resizable()
                .scaledToFit()
                .frame(width: 195)
        }
    }
}

#Preview {
    SplashView()
}
