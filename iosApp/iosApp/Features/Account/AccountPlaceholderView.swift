import SwiftUI

/// アカウントタブの Coming Soon プレースホルダ。
/// 実装は A3b（Figma `100:19982`）で行う。
struct AccountPlaceholderView: View {
    var body: some View {
        ZStack {
            FujuBankPalette.background.ignoresSafeArea()
            Text("アカウント画面は準備中です")
                .font(.system(size: 16, weight: .medium))
                .foregroundStyle(FujuBankPalette.textSecondary)
        }
    }
}
