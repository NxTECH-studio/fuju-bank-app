import SwiftUI
import Shared

/// ログイン画面 — Figma node 302-2698 準拠。
///
/// - 背景は splash と同じ `#F6F7F9`（Subtract 装飾はオープニング画面以外では出さない方針）。
/// - ヘッダにワードマーク `fuju pay` のみ表示。戻る矢印は視覚対称のため左に配置するが導線無効。
/// - 入力欄は flat な rounded-16 白カード（`TextField` のデフォルト枠を消し、placeholder 色を Figma に揃える）。
/// - ログイン CTA は底部固定（rounded-16, ブランドピンク `#FF1E9E`）。
/// - 「Googleで続ける」「新規登録」リンクは A2f 以降で配線するため本画面ではタップ無効。
struct LoginView: View {
    @StateObject var viewModel: LoginViewModel
    /// 「新規登録」リンクのタップで呼ばれる。サインアップフロー (A2f) のエントリーポイント。
    var onSignupTap: () -> Void = {}
    /// debug ビルド限定の認証スキップ callback。release では呼び出し側が渡さず常に nil。
    /// optional 自体は release にも残るが、参照する CTA 描画コードは `#if DEBUG` で除去される。
    var onDebugSkip: (() -> Void)? = nil

    private var canSubmit: Bool {
        !viewModel.identifier.trimmingCharacters(in: .whitespaces).isEmpty
            && !viewModel.password.isEmpty
            && !viewModel.isSubmitting
    }

    var body: some View {
        ZStack {
            Color("FujuSplashBackground")
                .ignoresSafeArea()

            VStack(spacing: 0) {
                header
                    .padding(.horizontal, 10)
                Spacer()
                loginCard
                    .padding(.horizontal, 24)
                if let message = viewModel.errorMessage {
                    Text(message)
                        .font(.system(size: 14))
                        .foregroundColor(.red)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                        .padding(.top, 8)
                }
                Spacer()
                bottomCta
                    .padding(.horizontal, 24)
                    .padding(.bottom, 16)
                #if DEBUG
                if let onDebugSkip {
                    debugSkipButton(action: onDebugSkip)
                        .padding(.horizontal, 24)
                        .padding(.bottom, 16)
                }
                #endif
            }
        }
    }

    private var header: some View {
        HStack {
            ZStack {
                Image(systemName: "chevron.left")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundColor(Color(red: 0x11 / 255, green: 0x11 / 255, blue: 0x11 / 255))
            }
            .frame(width: 48, height: 48)
            Spacer()
            Image("FujuWordmark")
                .resizable()
                .scaledToFit()
                .frame(height: 28)
            Spacer()
            Color.clear.frame(width: 48, height: 48)
        }
        .padding(.top, 8)
    }

    private var loginCard: some View {
        VStack(spacing: 32) {
            VStack(spacing: 2) {
                Text("ログイン")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(Self.text111)
                Text("メールまたは公開IDを入力")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(Self.subText)
            }

            VStack(spacing: 16) {
                VStack(spacing: 8) {
                    flatField(
                        text: $viewModel.identifier,
                        placeholder: "メールアドレス または ユーザーID",
                        keyboard: .emailAddress,
                        secure: false,
                    )
                    flatField(
                        text: $viewModel.password,
                        placeholder: "パスワード",
                        keyboard: .default,
                        secure: true,
                    )
                }

                dividerWithLabel("または")

                VStack(spacing: 12) {
                    googleButton
                    signupLink
                }
            }
        }
    }

    private func flatField(
        text: Binding<String>,
        placeholder: String,
        keyboard: UIKeyboardType,
        secure: Bool,
    ) -> some View {
        ZStack(alignment: .leading) {
            if text.wrappedValue.isEmpty {
                Text(placeholder)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(Self.placeholderText)
            }
            Group {
                if secure {
                    SecureField("", text: text)
                } else {
                    TextField("", text: text)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled(true)
                        .keyboardType(keyboard)
                }
            }
            .font(.system(size: 14, weight: .semibold))
            .foregroundColor(Self.text111)
            .disabled(viewModel.isSubmitting)
        }
        .padding(.horizontal, 24)
        .frame(height: 48)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private func dividerWithLabel(_ label: String) -> some View {
        HStack(spacing: 10) {
            line
            Text(label)
                .font(.system(size: 12, weight: .regular))
                .foregroundColor(Self.dividerText)
            line
        }
        .padding(.horizontal, 24)
        .frame(height: 20)
    }

    private var line: some View {
        RoundedRectangle(cornerRadius: 27)
            .fill(Self.dividerLine)
            .frame(height: 2)
            .frame(maxWidth: .infinity)
    }

    private var googleButton: some View {
        // Google OAuth は本タスクのスコープ外。視覚のみ。
        HStack(spacing: 16) {
            Image("GoogleG")
                .resizable()
                .scaledToFit()
                .frame(width: 20, height: 20)
            Text("Googleで続ける")
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(Self.text111)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 48)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .shadow(color: Color.black.opacity(0.02), radius: 3.014, x: 0, y: 3.014)
    }

    private var signupLink: some View {
        // 「新規登録」 のタップでサインアップフロー (A2f) に遷移する。文言・配色は変更しない。
        Button(action: onSignupTap) {
            HStack(spacing: 4) {
                Text("アカウントをお持ちでない方は")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(Self.subText)
                Text("新規登録")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(Self.brandPink)
                    .underline()
            }
        }
        .buttonStyle(.plain)
    }

    private var bottomCta: some View {
        Button(action: { viewModel.submit() }) {
            HStack(spacing: 8) {
                if viewModel.isSubmitting {
                    ProgressView()
                        .progressViewStyle(.circular)
                        .tint(.white)
                }
                Text(viewModel.isSubmitting ? "ログイン中..." : "ログイン")
                    .font(.system(size: 16, weight: .semibold))
            }
            .foregroundColor(canSubmit ? .white : Self.ctaDisabledText)
            .frame(maxWidth: .infinity)
            .frame(height: 48)
            .background(canSubmit ? Self.brandPink : Self.ctaDisabledBg)
            .clipShape(RoundedRectangle(cornerRadius: 16))
        }
        .disabled(!canSubmit)
    }

    #if DEBUG
    /// debug ビルド限定の認証スキップ CTA。本番 UI に紛れた場合に一目で識別できるよう、
    /// グレー枠 + 細字 + 「[DEBUG]」プレフィクスで本番 CTA と差別化する。
    private func debugSkipButton(action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text("[DEBUG] ログインせず進む")
                .font(.system(size: 13, weight: .regular))
                .foregroundColor(Self.subText)
                .frame(maxWidth: .infinity)
                .frame(height: 44)
                .background(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Self.dividerText, lineWidth: 1),
                )
        }
    }
    #endif

    private static let text111 = Color(red: 0x11 / 255, green: 0x11 / 255, blue: 0x11 / 255)
    private static let subText = Color(red: 0x6E / 255, green: 0x6F / 255, blue: 0x72 / 255)
    private static let placeholderText = Color(red: 0xDA / 255, green: 0xDB / 255, blue: 0xDF / 255)
    private static let dividerLine = Color(red: 0xE9 / 255, green: 0xE9 / 255, blue: 0xEC / 255)
    private static let dividerText = Color(red: 0xC5 / 255, green: 0xC5 / 255, blue: 0xCB / 255)
    private static let brandPink = Color(red: 0xFF / 255, green: 0x1E / 255, blue: 0x9E / 255)
    private static let ctaDisabledBg = Color(red: 0xE6 / 255, green: 0xE6 / 255, blue: 0xE6 / 255)
    private static let ctaDisabledText = Color(red: 0xC3 / 255, green: 0xC3 / 255, blue: 0xCA / 255)
}
