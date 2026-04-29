import SwiftUI

/// Screen 2: 二段階認証 OTP（Figma `383-14941` / `383-16473`）。
///
/// hidden TextField + 表示用 6 Box の構成にして backspace/paste の挙動を OS 標準に任せる。
struct SignUpOtpView: View {
    @EnvironmentObject var flow: SignUpFlowState
    let onConfirm: () -> Void
    let onBack: () -> Void

    @FocusState private var fieldFocused: Bool

    private var canSubmit: Bool {
        flow.otp.count == SignUpFlowState.otpLength
    }

    var body: some View {
        ZStack {
            SignUpTokens.background
                .ignoresSafeArea()
            VStack(spacing: 0) {
                SignUpHeader(onBack: onBack)
                    .padding(.horizontal, 10)
                Spacer().frame(height: 24)
                VStack(alignment: .leading, spacing: 8) {
                    Text("二段階認証")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(SignUpTokens.primaryText)
                    Text("登録したメールに6桁のコードを送信しました")
                        .font(.system(size: 14, weight: .regular))
                        .foregroundColor(SignUpTokens.subText)
                        .lineSpacing(7)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 24)
                Spacer().frame(height: 32)

                ZStack {
                    otpBoxes
                    // ヒットテストとキーボード入力を集約する hidden TextField。
                    TextField("", text: Binding(
                        get: { flow.otp },
                        set: { flow.updateOtp($0) },
                    ))
                    .keyboardType(.numberPad)
                    .textContentType(.oneTimeCode)
                    .focused($fieldFocused)
                    .foregroundColor(.clear)
                    .accentColor(.clear)
                    .frame(height: 60)
                }
                .padding(.horizontal, 24)
                .contentShape(Rectangle())
                .onTapGesture { fieldFocused = true }

                Spacer()
                PageIndicator(total: 3, activeIndex: 1)
                    .padding(.bottom, 12)
                PrimaryButton(title: "確認する", enabled: canSubmit, action: onConfirm)
                    .padding(.horizontal, 24)
                    .padding(.bottom, 16)
            }
        }
        .onAppear { fieldFocused = true }
    }

    private var otpBoxes: some View {
        HStack(spacing: 0) {
            ForEach(0..<SignUpFlowState.otpLength, id: \.self) { index in
                let char = otpChar(at: index)
                let isCursor = index == flow.otp.count
                otpBox(char: char, isCursor: isCursor)
                    .frame(maxWidth: .infinity)
            }
        }
    }

    private func otpChar(at index: Int) -> Character? {
        guard index < flow.otp.count else { return nil }
        return flow.otp[flow.otp.index(flow.otp.startIndex, offsetBy: index)]
    }

    private func otpBox(char: Character?, isCursor: Bool) -> some View {
        VStack(spacing: 0) {
            ZStack {
                if let char {
                    Text(String(char))
                        .font(.system(size: 36, weight: .bold))
                        .foregroundColor(SignUpTokens.otpUnderlineActive)
                }
            }
            .frame(maxHeight: .infinity)
            Group {
                if char != nil || isCursor {
                    RoundedRectangle(cornerRadius: 23)
                        .fill(SignUpTokens.otpUnderlineActive)
                        .frame(width: 36, height: 6)
                } else {
                    Circle()
                        .fill(SignUpTokens.otpUnderlineIdle)
                        .frame(width: 6, height: 6)
                }
            }
        }
        .frame(width: 52, height: 60)
    }
}

#Preview("empty") {
    SignUpOtpView(onConfirm: {}, onBack: {})
        .environmentObject(SignUpFlowState())
}

#Preview("partial") {
    let state: SignUpFlowState = {
        let s = SignUpFlowState()
        s.otp = "1234"
        return s
    }()
    return SignUpOtpView(onConfirm: {}, onBack: {})
        .environmentObject(state)
}
