import SwiftUI

/// Screen 3: 認証成功（Figma `383-16105`）。完了 CTA で Welcome（=ログイン）に戻る（モック挙動）。
struct SignUpSuccessView: View {
    let onFinish: () -> Void

    var body: some View {
        ZStack {
            SignUpTokens.background
                .ignoresSafeArea()
            VStack(spacing: 0) {
                // 戻るは無効。視覚対称のためロゴのみ中央に配置。
                SignUpHeader(onBack: nil)
                    .padding(.horizontal, 10)
                Spacer()
                Text("認証が\n成功しました")
                    .font(.system(size: 40, weight: .bold))
                    .foregroundColor(SignUpTokens.primaryText)
                    .multilineTextAlignment(.center)
                    .lineSpacing(8)
                    .frame(maxWidth: .infinity)
                    .padding(.horizontal, 24)
                Spacer()
                PageIndicator(total: 3, activeIndex: 2)
                    .padding(.bottom, 12)
                PrimaryButton(title: "次へ", enabled: true, action: onFinish)
                    .padding(.horizontal, 24)
                    .padding(.bottom, 16)
            }
        }
    }
}

#Preview {
    SignUpSuccessView(onFinish: {})
}
