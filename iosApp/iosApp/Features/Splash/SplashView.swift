import SwiftUI

/// LaunchScreen (Info.plist `UILaunchScreen`) と同じ見た目を SwiftUI で再現するビュー。
///
/// - 背景色は Asset Catalog の `FujuSplashBackground` (Info.plist の `UIColorName` と一致)。
/// - 中央に `FujuLogo` を表示。Figma node 383-18577 の実アセット差し替え待ち。
///
/// 切り替わり時のチラつきを防ぐため、storyboard 相当 (UILaunchScreen) と
/// 背景色・ロゴ位置を揃えること。
struct SplashView: View {
    var body: some View {
        ZStack {
            Color("FujuSplashBackground")
                .ignoresSafeArea()
            Image("FujuLogo")
                .resizable()
                .scaledToFit()
                .frame(width: 120, height: 120)
        }
    }
}

#Preview {
    SplashView()
}
