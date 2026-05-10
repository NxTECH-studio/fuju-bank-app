import SwiftUI

/// Screen 1: アカウント作成（client-bank-21 で旧 SignUpCreateView を置換）。
///
/// email + password + public_id を入力し、CTA タップで AuthCore `/v1/auth/register` を叩く。
/// 成功時は SignUpFlowState が裏で login → provisionMe → mfa/register まで進めて MfaQr 画面に遷移する。
struct SignUpAccountView: View {
    @EnvironmentObject var flow: SignUpFlowState
    let onBack: () -> Void
    let onLoginRedirect: () -> Void

    private var canSubmit: Bool {
        !flow.email.isEmpty && !flow.password.isEmpty &&
            !flow.publicId.isEmpty && flow.publicIdError == nil && !flow.isSubmitting
    }

    var body: some View {
        ZStack {
            SignUpTokens.background
                .ignoresSafeArea()
            VStack(spacing: 0) {
                SignUpHeader(onBack: onBack)
                    .padding(.horizontal, 10)
                Spacer()
                VStack(spacing: 20) {
                    VStack(spacing: 2) {
                        Text("アカウントの作成")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(SignUpTokens.primaryText)
                        Text("メール・パスワード・ユーザー ID を入力")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(SignUpTokens.secondaryText)
                    }
                    fieldWithError(error: flow.emailError) {
                        BankTextField(
                            text: Binding(
                                get: { flow.email },
                                set: { flow.updateEmail($0) },
                            ),
                            placeholder: "メールアドレス",
                            keyboard: .emailAddress,
                            secure: false,
                        )
                        .disabled(flow.isSubmitting)
                    }
                    BankTextField(
                        text: Binding(
                            get: { flow.password },
                            set: { flow.updatePassword($0) },
                        ),
                        placeholder: "パスワード（8 文字以上）",
                        keyboard: .default,
                        secure: true,
                    )
                    .disabled(flow.isSubmitting)
                    fieldWithError(error: flow.publicIdError, helper: "半角英数字 4〜16 文字") {
                        BankTextField(
                            text: Binding(
                                get: { flow.publicId },
                                set: { flow.updatePublicId($0) },
                            ),
                            placeholder: "ユーザー ID",
                            keyboard: .asciiCapable,
                            secure: false,
                        )
                        .disabled(flow.isSubmitting)
                    }
                    LoginRedirectLink(action: onLoginRedirect)
                    LegalAgreementText()
                    if let formError = flow.formError {
                        Text(formError)
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(SignUpErrorRed)
                    }
                }
                .padding(.horizontal, 24)
                Spacer()
                PageIndicator(total: 4, activeIndex: 0)
                    .padding(.bottom, 12)
                PrimaryButton(
                    title: flow.isSubmitting ? "送信中..." : "次へ",
                    enabled: canSubmit,
                    action: { flow.submitAccount() },
                )
                .padding(.horizontal, 24)
                .padding(.bottom, 16)
            }
        }
    }

    @ViewBuilder
    private func fieldWithError<Content: View>(
        error: String?,
        helper: String? = nil,
        @ViewBuilder content: () -> Content,
    ) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            content()
            if let error {
                Text(error)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(SignUpErrorRed)
            } else if let helper {
                Text(helper)
                    .font(.system(size: 12, weight: .regular))
                    .foregroundColor(SignUpTokens.secondaryText)
            }
        }
    }
}

let SignUpErrorRed = Color(red: 0xD3 / 255, green: 0x2F / 255, blue: 0x2F / 255)

#Preview {
    SignUpAccountView(
        onBack: {},
        onLoginRedirect: {},
    )
    .environmentObject(SignUpFlowState())
}
