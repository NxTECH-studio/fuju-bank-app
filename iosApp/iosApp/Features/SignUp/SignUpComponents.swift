import SwiftUI

/// サインアップ 3 画面で共通利用するデザイントークンと UI 部品。
///
/// LoginView との重複は将来の整理対象。本タスクでは features/SignUp に閉じる方針で寄せる。
enum SignUpTokens {
    static let background = Color(red: 0xF6 / 255, green: 0xF7 / 255, blue: 0xF9 / 255)
    static let card = Color.white
    static let primary = Color(red: 0xFF / 255, green: 0x1E / 255, blue: 0x9E / 255)
    static let primaryText = Color(red: 0x11 / 255, green: 0x11 / 255, blue: 0x11 / 255)
    static let secondaryText = Color(red: 0x6E / 255, green: 0x6F / 255, blue: 0x72 / 255)
    static let placeholder = Color(red: 0xDA / 255, green: 0xDB / 255, blue: 0xDF / 255)
    static let subText = Color(red: 0x64 / 255, green: 0x74 / 255, blue: 0x8B / 255)
    static let linkBlue = Color(red: 0x00 / 255, green: 0x6C / 255, blue: 0xD7 / 255)
    static let dividerLine = Color(red: 0xE9 / 255, green: 0xE9 / 255, blue: 0xEC / 255)
    static let dividerLabel = Color(red: 0xC5 / 255, green: 0xC5 / 255, blue: 0xCB / 255)
    static let indicatorActive = Color(red: 0x11 / 255, green: 0x11 / 255, blue: 0x11 / 255)
    static let indicatorInactive = Color(red: 0xE1 / 255, green: 0xE2 / 255, blue: 0xE4 / 255)
    static let otpUnderlineActive = Color(red: 0x33 / 255, green: 0x34 / 255, blue: 0x36 / 255)
    static let otpUnderlineIdle = Color(red: 0xE8 / 255, green: 0xE9 / 255, blue: 0xED / 255)
    static let ctaDisabledBg = Color(red: 0xE6 / 255, green: 0xE6 / 255, blue: 0xE6 / 255)
    static let ctaDisabledText = Color(red: 0xC3 / 255, green: 0xC3 / 255, blue: 0xCA / 255)
}

struct SignUpHeader: View {
    let onBack: (() -> Void)?

    var body: some View {
        HStack {
            ZStack {
                if let onBack {
                    Button(action: onBack) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundColor(SignUpTokens.primaryText)
                    }
                }
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
}

struct BankTextField: View {
    @Binding var text: String
    let placeholder: String
    var keyboard: UIKeyboardType = .default
    var secure: Bool = false

    var body: some View {
        ZStack(alignment: .leading) {
            if text.isEmpty {
                Text(placeholder)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(SignUpTokens.placeholder)
            }
            Group {
                if secure {
                    SecureField("", text: $text)
                } else {
                    TextField("", text: $text)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled(true)
                        .keyboardType(keyboard)
                }
            }
            .font(.system(size: 14, weight: .semibold))
            .foregroundColor(SignUpTokens.primaryText)
        }
        .padding(.horizontal, 24)
        .frame(height: 48)
        .background(SignUpTokens.card)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

struct PrimaryButton: View {
    let title: String
    let enabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(enabled ? .white : SignUpTokens.ctaDisabledText)
                .frame(maxWidth: .infinity)
                .frame(height: 48)
                .background(enabled ? SignUpTokens.primary : SignUpTokens.ctaDisabledBg)
                .clipShape(RoundedRectangle(cornerRadius: 16))
        }
        .disabled(!enabled)
    }
}

struct DividerWithLabel: View {
    let label: String

    var body: some View {
        HStack(spacing: 10) {
            line
            Text(label)
                .font(.system(size: 12, weight: .regular))
                .foregroundColor(SignUpTokens.dividerLabel)
            line
        }
        .padding(.horizontal, 24)
        .frame(height: 20)
    }

    private var line: some View {
        RoundedRectangle(cornerRadius: 27)
            .fill(SignUpTokens.dividerLine)
            .frame(height: 2)
            .frame(maxWidth: .infinity)
    }
}

struct GoogleSignInButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 16) {
                Image("GoogleG")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 20, height: 20)
                Text("Googleで続ける")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(SignUpTokens.primaryText)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 48)
            .background(SignUpTokens.card)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .shadow(color: Color.black.opacity(0.02), radius: 3.014, x: 0, y: 3.014)
        }
        .buttonStyle(.plain)
    }
}

/// Figma 準拠のページインジケータ。アクティブは 35x6 の角丸ピル、非アクティブは 6x6 の円。
struct PageIndicator: View {
    let total: Int
    let activeIndex: Int

    var body: some View {
        HStack(spacing: 6) {
            ForEach(0..<total, id: \.self) { index in
                if index == activeIndex {
                    RoundedRectangle(cornerRadius: 20)
                        .fill(SignUpTokens.indicatorActive)
                        .frame(width: 35, height: 6)
                } else {
                    Circle()
                        .fill(SignUpTokens.indicatorInactive)
                        .frame(width: 6, height: 6)
                }
            }
        }
    }
}

struct LoginRedirectLink: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                Text("アカウントをお持ちの方は")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(SignUpTokens.secondaryText)
                Text("ログイン")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(SignUpTokens.primary)
                    .underline()
            }
        }
        .buttonStyle(.plain)
    }
}

struct LegalAgreementText: View {
    // 利用規約 / プライバシーポリシーの遷移先実装は本タスクのスコープ外。視覚のみで配線しない。
    var body: some View {
        (
            Text("登録することで、")
                .foregroundColor(SignUpTokens.secondaryText)
            + Text("利用規約")
                .foregroundColor(SignUpTokens.linkBlue)
                .underline()
            + Text(" と ")
                .foregroundColor(SignUpTokens.secondaryText)
            + Text("プライバシーポリシー")
                .foregroundColor(SignUpTokens.linkBlue)
                .underline()
            + Text(" に同意します")
                .foregroundColor(SignUpTokens.secondaryText)
        )
        .font(.system(size: 12, weight: .medium))
        .multilineTextAlignment(.center)
    }
}
