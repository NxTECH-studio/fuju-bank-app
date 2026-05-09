import SwiftUI
import Shared

/// MFA 検証 → 検証成功後オンボーディング 3 画面（成功 / ようこそ / fujupay ロゴ）の SwiftUI ルート。
///
/// - `.input`: OTP 6 スロット入力 + ピンク CTA（Figma 01 / 02）。
/// - `.onboarding(.success)`: 「認証が成功しました」+ ページインジケータ + 「次へ」（Figma 03）。
/// - `.onboarding(.welcome)`: 「ようこそ」中央のみ。1.8 秒後自動進行（Figma 04）。
/// - `.onboarding(.brand)`: fujupay ロゴ中央。1.5 秒後 `setAuthenticated` を呼ぶ（Figma 05）。
struct MfaVerifyView: View {
    @StateObject var viewModel: MfaVerifyViewModel

    var body: some View {
        ZStack {
            Color("FujuSplashBackground")
                .ignoresSafeArea()

            Group {
                switch viewModel.phase {
                case .input:
                    MfaVerifyInputView(viewModel: viewModel)
                case .onboarding(.success):
                    MfaSuccessView(onNext: viewModel.advanceOnboarding)
                case .onboarding(.welcome):
                    MfaWelcomeView(onAdvance: viewModel.advanceOnboarding)
                case .onboarding(.brand):
                    MfaBrandView(onAdvance: viewModel.advanceOnboarding)
                }
            }
            .transition(.opacity)
        }
        .animation(.easeInOut(duration: 0.25), value: viewModel.phase)
    }
}

// MARK: - Input phase

private struct MfaVerifyInputView: View {
    @ObservedObject var viewModel: MfaVerifyViewModel
    @FocusState private var isCodeFocused: Bool

    private var canSubmit: Bool {
        viewModel.code.count == MfaVerifyViewModel.codeLength && !viewModel.isSubmitting
    }

    var body: some View {
        VStack(spacing: 0) {
            AuthHeader(showBack: true, onBack: { viewModel.cancel() })
                .padding(.horizontal, 10)
            Spacer().frame(height: 24)
            TitleAndSubtitle(
                title: "二段階認証",
                // Figma の「登録したメールに 6 桁のコードを送信しました」は誤情報のため
                // TOTP 用の説明文に差し替える。MFA factor は TOTP のまま。
                subtitle: "認証アプリの 6 桁コードを入力してください",
            )
            Spacer()
            otpSlotRow
                .padding(.horizontal, 24)
            if let message = viewModel.errorMessage {
                Text(message)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(MfaPalette.error)
                    .multilineTextAlignment(.center)
                    .padding(.top, 12)
                    .padding(.horizontal, 24)
            }
            Spacer()
            PrimaryCta(
                label: viewModel.isSubmitting ? "確認中..." : "確認する",
                isLoading: viewModel.isSubmitting,
                isEnabled: canSubmit,
                action: { viewModel.submit() },
            )
            .padding(.horizontal, 24)
            .padding(.bottom, 16)
        }
        .onAppear {
            // 初期表示で自動的にキーボード表示。
            isCodeFocused = true
        }
    }

    private var otpSlotRow: some View {
        // 単一の隠し TextField に全文字を集約し、視覚は 6 個の OtpSlotCell で表現する OTP の定番パターン。
        ZStack {
            HStack(spacing: 12) {
                ForEach(0 ..< MfaVerifyViewModel.codeLength, id: \.self) { i in
                    OtpSlotCell(
                        digit: digit(at: i),
                        isActive: i == activeIndex && !viewModel.isSubmitting,
                    )
                }
            }
            // 透明な実体 TextField を全面に重ねて入力を吸収する。numberPad 用に keyboardType を設定。
            TextField("", text: $viewModel.code)
                .keyboardType(.numberPad)
                .textContentType(.oneTimeCode)
                .focused($isCodeFocused)
                .disabled(viewModel.isSubmitting)
                .foregroundColor(.clear)
                .accentColor(.clear)
                .tint(.clear)
                .opacity(0.001)
        }
        .contentShape(Rectangle())
        .onTapGesture { isCodeFocused = true }
    }

    private func digit(at index: Int) -> String {
        guard index < viewModel.code.count else { return "" }
        let i = viewModel.code.index(viewModel.code.startIndex, offsetBy: index)
        return String(viewModel.code[i])
    }

    private var activeIndex: Int {
        min(viewModel.code.count, MfaVerifyViewModel.codeLength - 1)
    }
}

private struct OtpSlotCell: View {
    let digit: String
    let isActive: Bool

    var body: some View {
        VStack(spacing: 0) {
            ZStack {
                Text(digit)
                    .font(.system(size: 28, weight: .bold, design: .monospaced))
                    .foregroundColor(MfaPalette.text111)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 36)
            Spacer().frame(height: 8)
            if isActive {
                // 現在入力位置: 太い黒の下線。
                RoundedRectangle(cornerRadius: 2)
                    .fill(MfaPalette.text111)
                    .frame(width: 20, height: 3)
            } else {
                // 未入力 / 入力済みスロット: 細い灰色のドット。
                Circle()
                    .fill(MfaPalette.dot)
                    .frame(width: 4, height: 4)
            }
        }
        .frame(height: 56)
    }
}

// MARK: - Success phase

private struct MfaSuccessView: View {
    let onNext: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            AuthHeader(showBack: false, onBack: nil)
                .padding(.horizontal, 10)
            Spacer()
            Text("認証が\n成功しました")
                .multilineTextAlignment(.center)
                .font(.system(size: 32, weight: .bold))
                .foregroundColor(MfaPalette.text111)
                .lineSpacing(8)
            Spacer()
            MfaPageIndicator(activeIndex: 1, total: 3)
                .padding(.bottom, 8)
            PrimaryCta(
                label: "次へ",
                isLoading: false,
                isEnabled: true,
                action: onNext,
            )
            .padding(.horizontal, 24)
            .padding(.bottom, 16)
        }
    }
}

private struct MfaPageIndicator: View {
    let activeIndex: Int
    let total: Int

    var body: some View {
        HStack(spacing: 8) {
            ForEach(0 ..< total, id: \.self) { i in
                let isActive = i == activeIndex
                if isActive {
                    RoundedRectangle(cornerRadius: 2)
                        .fill(MfaPalette.text111)
                        .frame(width: 18, height: 4)
                } else {
                    Circle()
                        .fill(MfaPalette.dot)
                        .frame(width: 4, height: 4)
                }
            }
        }
    }
}

// MARK: - Welcome phase

private struct MfaWelcomeView: View {
    let onAdvance: () -> Void

    var body: some View {
        ZStack {
            Text("ようこそ")
                .font(.system(size: 32, weight: .bold))
                .foregroundColor(MfaPalette.text111)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .task {
            // 自動進行: 約 1.8 秒間「ようこそ」を表示してから次の Brand 画面へ。
            try? await Task.sleep(nanoseconds: 1_800_000_000)
            onAdvance()
        }
    }
}

// MARK: - Brand phase

private struct MfaBrandView: View {
    let onAdvance: () -> Void

    var body: some View {
        ZStack {
            // fuju 銀行 / fujupay 兼用のブランドロゴ。Figma 05 は fujupay のロゴだが、
            // 既存スプラッシュ／ログイン画面と同一の `FujuLogo` を使い回し、ロゴ系統の
            // 一貫性を担保する。fujupay 専用ロゴへの差し替えは未決事項として残す。
            Image("FujuLogo")
                .resizable()
                .scaledToFit()
                .frame(width: 196)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .task {
            // 自動進行: 約 1.5 秒間ロゴを表示してから setAuthenticated → ホームへ。
            try? await Task.sleep(nanoseconds: 1_500_000_000)
            onAdvance()
        }
    }
}

// MARK: - Shared building blocks

private struct AuthHeader: View {
    let showBack: Bool
    let onBack: (() -> Void)?

    var body: some View {
        HStack {
            ZStack {
                if showBack {
                    Button(action: { onBack?() }) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundColor(MfaPalette.text111)
                    }
                    .buttonStyle(.plain)
                }
            }
            .frame(width: 48, height: 48)
            Spacer()
            Image("LogoFujupay")
                .resizable()
                .scaledToFit()
                .frame(height: 24)
            Spacer()
            Color.clear.frame(width: 48, height: 48)
        }
        .padding(.top, 8)
    }
}

private struct TitleAndSubtitle: View {
    let title: String
    let subtitle: String

    var body: some View {
        VStack(spacing: 2) {
            Text(title)
                .font(.system(size: 17, weight: .bold))
                .foregroundColor(MfaPalette.text111)
            Text(subtitle)
                .font(.system(size: 12, weight: .regular))
                .foregroundColor(MfaPalette.subText)
        }
    }
}

private struct PrimaryCta: View {
    let label: String
    let isLoading: Bool
    let isEnabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if isLoading {
                    ProgressView()
                        .progressViewStyle(.circular)
                        .tint(.white)
                }
                Text(label)
                    .font(.system(size: 16, weight: .semibold))
            }
            .foregroundColor(isEnabled ? .white : MfaPalette.ctaDisabledText)
            .frame(maxWidth: .infinity)
            .frame(height: 48)
            .background(isEnabled ? MfaPalette.brandPink : MfaPalette.ctaDisabledBg)
            .clipShape(RoundedRectangle(cornerRadius: 16))
        }
        .disabled(!isEnabled)
    }
}

// MARK: - Palette

private enum MfaPalette {
    static let text111 = Color(red: 0x11 / 255, green: 0x11 / 255, blue: 0x11 / 255)
    static let subText = Color(red: 0x6E / 255, green: 0x6F / 255, blue: 0x72 / 255)
    static let dot = Color(red: 0xB0 / 255, green: 0xB0 / 255, blue: 0xB0 / 255)
    static let brandPink = Color(red: 0xFF / 255, green: 0x1E / 255, blue: 0x9E / 255)
    static let ctaDisabledBg = Color(red: 0xE6 / 255, green: 0xE6 / 255, blue: 0xE6 / 255)
    static let ctaDisabledText = Color(red: 0xC3 / 255, green: 0xC3 / 255, blue: 0xCA / 255)
    static let error = Color(red: 0xD3 / 255, green: 0x2F / 255, blue: 0x2F / 255)
}
