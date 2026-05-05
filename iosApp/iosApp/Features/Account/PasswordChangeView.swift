import SwiftUI
import UIKit

/// パスワード変更画面 — Android `PasswordChangeScreen` と 1:1。
///
/// - ヘッダー: 戻る `<` (左 48pt) / タイトル「パスワード変更」(中央 17pt Bold)
/// - 3 入力欄: 「現在のパスワード」「新しいパスワード」「新しいパスワード（確認）」
///   いずれも `SecureField` でマスク表示
/// - 保存ボタン: 高さ 48 / 16pt 角丸 / `FujuBankPalette.brandPink`、disabled 時は `hairline`
///
/// バックエンド連携は未配線のため、`viewModel.submit()` は疑似遅延ののち成功扱いになる。
/// 成功時 (`isSubmitted == true`) で [onSuccess] を呼び親側で toast 表示し、
/// `dismiss()` で `AccountHubView` まで戻る。
///
/// タブバーは `RootTabView.isBottomBarHidden` で `accountPath.last == .passwordChange`
/// のときに非表示化する（法的文書画面と同方針）。`RootTabView` は自前ボトムバー構造で
/// SwiftUI の `TabView` を使っていないため、`.toolbar(.hidden, for: .tabBar)` は不要。
struct PasswordChangeView: View {
    @StateObject private var viewModel = ObservablePasswordChangeViewModel()
    @Environment(\.dismiss) private var dismiss
    let onSuccess: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            header
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    PasswordSecureField(
                        title: "現在のパスワード",
                        text: $viewModel.current,
                        textContentType: .password,
                        submitLabel: .next,
                    )
                    PasswordSecureField(
                        title: "新しいパスワード",
                        text: $viewModel.newPassword,
                        textContentType: .newPassword,
                        submitLabel: .next,
                    )
                    PasswordSecureField(
                        title: "新しいパスワード（確認）",
                        text: $viewModel.confirm,
                        textContentType: .newPassword,
                        submitLabel: .done,
                        onSubmit: {
                            if viewModel.canSubmit { viewModel.submit() }
                        },
                    )

                    saveButton
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
            }
            .scrollIndicators(.hidden)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(FujuBankPalette.background.ignoresSafeArea())
        .navigationBarHidden(true)
        .onChange(of: viewModel.isSubmitted) { _, newValue in
            // 成功フラグの立ち上がりで toast → ハブへ戻る。
            // ViewModel は `@StateObject` で本ビューに紐づくため、画面破棄で破棄され、
            // Android 側のような consumeSubmitted リセットは不要。
            if newValue {
                onSuccess()
                dismiss()
            }
        }
    }

    private var header: some View {
        ZStack {
            Text("パスワード変更")
                .font(FujuBankTypography.headline)
                .foregroundStyle(FujuBankPalette.textPrimary)

            HStack {
                Button(action: { dismiss() }) {
                    Image("ChevronLeft")
                        .renderingMode(.template)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 24, height: 24)
                        .foregroundStyle(FujuBankPalette.textPrimary)
                        .frame(width: 48, height: 48)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("戻る")
                Spacer()
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 10)
    }

    private var saveButton: some View {
        Button(action: { viewModel.submit() }) {
            Text("保存")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(viewModel.canSubmit ? Color.white : FujuBankPalette.textTertiary)
                .frame(maxWidth: .infinity)
                .frame(height: 48)
                .background(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .fill(viewModel.canSubmit ? FujuBankPalette.brandPink : FujuBankPalette.hairline)
                )
        }
        .buttonStyle(.plain)
        .disabled(!viewModel.canSubmit)
        .accessibilityLabel("保存")
    }
}

/// パスワード入力欄 — Android `PasswordField` と同等のラベル付きアウトライン枠。
///
/// `OutlinedTextField` 相当の見た目を `SecureField` + `RoundedRectangle` ボーダーで再現。
/// フォーカス時はボーダー色を `brandPink` に切り替えてアクティブ表示する。
private struct PasswordSecureField: View {
    let title: String
    @Binding var text: String
    let textContentType: UITextContentType
    let submitLabel: SubmitLabel
    var onSubmit: (() -> Void)? = nil

    @FocusState private var isFocused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(FujuBankTypography.caption)
                .foregroundStyle(isFocused ? FujuBankPalette.brandPink : FujuBankPalette.textSecondary)
            SecureField("", text: $text)
                .textContentType(textContentType)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)
                .submitLabel(submitLabel)
                .focused($isFocused)
                .onSubmit { onSubmit?() }
                .font(.system(size: 14))
                .foregroundStyle(FujuBankPalette.textPrimary)
                .tint(FujuBankPalette.brandPink)
                .padding(.horizontal, 14)
                .frame(height: 48)
                .background(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .stroke(
                            isFocused ? FujuBankPalette.brandPink : FujuBankPalette.hairline,
                            lineWidth: 1,
                        )
                        .background(
                            RoundedRectangle(cornerRadius: 16, style: .continuous)
                                .fill(FujuBankPalette.surface)
                        )
                )
        }
    }
}
