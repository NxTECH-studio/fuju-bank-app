import Foundation
import SwiftUI

/// Screen 1: アカウント作成（Figma `383-12951` / `296-2092`）。
///
/// email + password を入力して OTP 画面へ進む。Google 認証はモックで onTap がログ出力のみ。
struct SignUpCreateView: View {
    @EnvironmentObject var flow: SignUpFlowState
    let onNext: () -> Void
    let onBack: () -> Void
    let onLoginRedirect: () -> Void

    private var canSubmit: Bool {
        let trimmed = flow.email.trimmingCharacters(in: .whitespaces)
        return !trimmed.isEmpty && trimmed.contains("@") && !flow.password.isEmpty
    }

    var body: some View {
        ZStack {
            SignUpTokens.background
                .ignoresSafeArea()
            VStack(spacing: 0) {
                SignUpHeader(onBack: onBack)
                    .padding(.horizontal, 10)
                Spacer()
                VStack(spacing: 24) {
                    VStack(spacing: 2) {
                        Text("アカウントの作成")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(SignUpTokens.primaryText)
                        Text("メールを入力")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(SignUpTokens.secondaryText)
                    }
                    VStack(spacing: 8) {
                        BankTextField(
                            text: $flow.email,
                            placeholder: "メールアドレス または ユーザーID",
                            keyboard: .emailAddress,
                            secure: false,
                        )
                        BankTextField(
                            text: $flow.password,
                            placeholder: "パスワード",
                            keyboard: .default,
                            secure: true,
                        )
                    }
                    DividerWithLabel(label: "または")
                    VStack(spacing: 12) {
                        GoogleSignInButton(action: {
                            // OAuth 実装は本タスクのスコープ外。タップを記録するのみ。
                            print("[SignUpCreateView] Google sign-in tapped (mock)")
                        })
                        LoginRedirectLink(action: onLoginRedirect)
                    }
                    LegalAgreementText()
                }
                .padding(.horizontal, 24)
                Spacer()
                PageIndicator(total: 4, activeIndex: 0)
                    .padding(.bottom, 12)
                PrimaryButton(title: "次へ", enabled: canSubmit, action: onNext)
                    .padding(.horizontal, 24)
                    .padding(.bottom, 16)
            }
        }
    }
}

#Preview {
    SignUpCreateView(
        onNext: {},
        onBack: {},
        onLoginRedirect: {},
    )
    .environmentObject(SignUpFlowState())
}
