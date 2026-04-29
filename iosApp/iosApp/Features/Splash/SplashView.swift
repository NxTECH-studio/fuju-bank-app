import SwiftUI

/// LaunchScreen (Info.plist `UILaunchScreen`) と同じ見た目を SwiftUI で再現するビュー。
///
/// Figma node 175-2457 の構成:
/// - 背景色は Asset Catalog の `FujuSplashBackground` (Info.plist の `UIColorName` と一致、`#F6F7F9`)
/// - 中央付近に Subtract 装飾 (`FujuSplashDecoration`) を配置 (Figma の `calc(50%+6.97px)` に
///   合わせて y を +7pt オフセット)
/// - 中央に icon + "fuju pay" wordmark の合成ロゴ (`FujuLogo`)
///
/// Info.plist `UILaunchScreen` は `UIImageName` 1 枚のみを中央表示する制約があるため、
/// 装飾を含む完全な見た目はこの SwiftUI ビューでのみ再現される (LaunchScreen 自体は
/// 背景色 + ロゴ 1 枚に簡略化されている)。
struct SplashView: View {
    var body: some View {
        ZStack {
            Color("FujuSplashBackground")
                .ignoresSafeArea()
            Image("FujuSplashDecoration")
                .resizable()
                .scaledToFit()
                .frame(width: 252, height: 352)
                .offset(y: 7)
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
