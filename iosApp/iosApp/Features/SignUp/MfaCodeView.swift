import SwiftUI

/// Screen 3 (MFA セットアップ): TOTP 6 桁コード入力。
///
/// hidden TextField + 表示用 6 Box の構成。CTA タップで明示送信のため
/// 6 桁完了 / IME Done での自動 submit はしない（CLAUDE.md UX ルール）。
/// 戻るボタン抑止（ヘッダボタン非表示 + NavigationStack 不使用）。
struct MfaCodeView: View {
    @EnvironmentObject var flow: SignUpFlowState
    @FocusState private var fieldFocused: Bool

    var body: some View {
        ZStack {
            SignUpTokens.background
                .ignoresSafeArea()
            VStack(spacing: 0) {
                SignUpHeader(onBack: nil)
                    .padding(.horizontal, 10)
                Spacer().frame(height: 24)
                VStack(alignment: .center, spacing: 8) {
                    Text("認証コードを入力")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(SignUpTokens.primaryText)
                    Text("認証アプリに表示されている 6 桁のコードを入力してください")
                        .font(.system(size: 13, weight: .regular))
                        .foregroundColor(SignUpTokens.secondaryText)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
                .padding(.horizontal, 24)
                Spacer().frame(height: 28)

                ZStack {
                    totpBoxes
                    TextField("", text: Binding(
                        get: { flow.totpCode },
                        set: { flow.updateTotpCode($0) },
                    ))
                    .keyboardType(.numberPad)
                    .textContentType(.oneTimeCode)
                    .focused($fieldFocused)
                    .foregroundColor(.clear)
                    .accentColor(.clear)
                    .frame(height: 60)
                    .disabled(flow.isSubmitting)
                }
                .padding(.horizontal, 24)
                .contentShape(Rectangle())
                .onTapGesture { fieldFocused = true }

                Spacer()
                if let mfaCodeError = flow.mfaCodeError {
                    Text(mfaCodeError)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(SignUpErrorRed)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                        .padding(.bottom, 8)
                }
                PageIndicator(total: 4, activeIndex: 2)
                    .padding(.bottom, 12)
                PrimaryButton(
                    title: flow.isSubmitting ? "確認中..." : "確認する",
                    enabled: flow.totpCode.count == SignUpFlowState.totpLength && !flow.isSubmitting,
                    action: { flow.submitMfaCode() },
                )
                .padding(.horizontal, 24)
                .padding(.bottom, 16)
            }
        }
        .onAppear { fieldFocused = true }
    }

    private var totpBoxes: some View {
        HStack(spacing: 12) {
            ForEach(0..<SignUpFlowState.totpLength, id: \.self) { index in
                let char = totpChar(at: index)
                let isActive = index == flow.totpCode.count.clamped(to: 0...(SignUpFlowState.totpLength - 1)) && !flow.isSubmitting
                totpBox(char: char, isActive: isActive)
                    .frame(maxWidth: .infinity)
            }
        }
    }

    private func totpChar(at index: Int) -> Character? {
        guard index < flow.totpCode.count else { return nil }
        return flow.totpCode[flow.totpCode.index(flow.totpCode.startIndex, offsetBy: index)]
    }

    private func totpBox(char: Character?, isActive: Bool) -> some View {
        VStack(spacing: 0) {
            ZStack {
                if let char {
                    Text(String(char))
                        .font(.system(size: 28, weight: .bold, design: .monospaced))
                        .foregroundColor(SignUpTokens.primaryText)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            if isActive {
                RoundedRectangle(cornerRadius: 2)
                    .fill(SignUpTokens.primaryText)
                    .frame(width: 20, height: 3)
            } else {
                Circle()
                    .fill(Color(red: 0xB0 / 255, green: 0xB0 / 255, blue: 0xB0 / 255))
                    .frame(width: 4, height: 4)
            }
        }
        .frame(height: 56)
    }
}

private extension Comparable {
    func clamped(to range: ClosedRange<Self>) -> Self {
        return min(max(self, range.lowerBound), range.upperBound)
    }
}

#Preview {
    MfaCodeView()
        .environmentObject(SignUpFlowState())
}
