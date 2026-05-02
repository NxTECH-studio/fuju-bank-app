import SwiftUI

/// LaunchScreen (Info.plist `UILaunchScreen`) と同じ見た目を SwiftUI で再現するビュー。
///
/// Figma node 504-5945 の構成:
/// - 背景色は Asset Catalog の `FujuSplashBackground` (Info.plist の `UIColorName` と一致、`#F6F7F9`)
/// - 中央付近に銀行建物シルエット装飾 (`FujuSplashDecoration`) を配置
///   (Figma の `calc(50%-7.3px)` に合わせて y を -7pt オフセット)
/// - 銀行建物の柱が並ぶ位置 (top:413, height:51.617) に icon + "fuju 銀行" ロゴ (`FujuLogo`) を重ねる
///   (画面中央から +13pt 下)
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
                .frame(width: 277, height: 273)
                .offset(y: -7)
            Image("FujuLogo")
                .resizable()
                .scaledToFit()
                .frame(width: 196)
                .offset(y: 13)
        }
    }
}

#Preview {
    SplashView()
}
