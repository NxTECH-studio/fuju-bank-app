import SwiftUI
import UIKit

/// Screen 2 (MFA セットアップ): TOTP QR 表示。
///
/// AuthCore が生成した base64 PNG を `UIImage(data:)` でデコードして表示する。
/// 戻るボタン非表示 + システムバック抑止（NavigationStack を使わない構成）。
struct MfaQrView: View {
    @EnvironmentObject var flow: SignUpFlowState

    var body: some View {
        ZStack {
            SignUpTokens.background
                .ignoresSafeArea()
            VStack(spacing: 0) {
                SignUpHeader(onBack: nil)
                    .padding(.horizontal, 10)
                Spacer()
                VStack(spacing: 20) {
                    Text("二段階認証の設定")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(SignUpTokens.primaryText)
                    Text("Google Authenticator などの認証アプリで\n以下の QR コードをスキャンしてください")
                        .font(.system(size: 14, weight: .regular))
                        .foregroundColor(SignUpTokens.secondaryText)
                        .multilineTextAlignment(.center)
                    qrBox
                    if let secret = flow.mfaSetup?.secret {
                        Text("QR が読めない場合: \(secret)")
                            .font(.system(size: 12, weight: .regular))
                            .foregroundColor(SignUpTokens.secondaryText)
                            .multilineTextAlignment(.center)
                    }
                    Button(action: { flow.startMfaSetup() }) {
                        Text("QR を再生成")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(SignUpTokens.primary)
                            .underline()
                    }
                    .disabled(flow.isSubmitting)
                    if let formError = flow.formError {
                        Text(formError)
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(SignUpErrorRed)
                            .multilineTextAlignment(.center)
                    }
                }
                .padding(.horizontal, 24)
                Spacer()
                PageIndicator(total: 4, activeIndex: 1)
                    .padding(.bottom, 12)
                PrimaryButton(
                    title: "コードを入力する",
                    enabled: flow.mfaSetup != nil && !flow.isSubmitting,
                    action: { flow.goToMfaCodeInput() },
                )
                .padding(.horizontal, 24)
                .padding(.bottom, 16)
            }
        }
    }

    @ViewBuilder
    private var qrBox: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 12)
                .fill(SignUpTokens.card)
                .frame(width: 220, height: 220)
            if let image = qrImage {
                Image(uiImage: image)
                    .resizable()
                    .interpolation(.none)
                    .scaledToFit()
                    .frame(width: 196, height: 196)
            } else {
                Text("QR を準備中...")
                    .font(.system(size: 13, weight: .regular))
                    .foregroundColor(SignUpTokens.secondaryText)
            }
        }
    }

    private var qrImage: UIImage? {
        guard let base64 = flow.mfaSetup?.qrPngBase64,
              let data = Data(base64Encoded: base64) else { return nil }
        return UIImage(data: data)
    }
}

#Preview {
    MfaQrView()
        .environmentObject(SignUpFlowState())
}
