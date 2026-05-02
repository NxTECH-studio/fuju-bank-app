import SwiftUI
import Shared

/// 送金画面 — A5 MVP。
///
/// - ヘッダー: 戻る `<` / タイトル「送る」（取引履歴と同じレイアウト）
/// - 本文: 受取人 ID / 金額の 2 入力 + 送るボタン
/// - 確認シート（`.alert`）で最終 submit。submit 中はボタン無効化で二重送金を抑止。
/// - 成功 / エラーは Toast で通知（onShowToast 経由で親 RootTabView の ToastCenter に流す）。
///
/// QR 送金 (`qr-payment-foundation-mpm`) は別タスク。
struct SendView: View {
    @StateObject private var viewModel = SendViewModel()
    var onBack: () -> Void = {}
    var onShowToast: (String) -> Void = { _ in }
    var onTransferSucceeded: () -> Void = {}

    var body: some View {
        VStack(spacing: 0) {
            header
            form
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        }
        .background(FujupayPalette.background.ignoresSafeArea())
        // 入力欄外タップでキーボードを閉じる導線。Form ではなく VStack なので自前で対応する。
        .contentShape(Rectangle())
        .onTapGesture { hideKeyboard() }
        // SendOutcome.Success / Failure の通知を View 側で 1 度だけ消費する。
        // iOS 17+ の 2 引数クロージャ形式を使う（IPHONEOS_DEPLOYMENT_TARGET=18.2）。
        .onChange(of: viewModel.successEvent) { _, event in
            guard let event else { return }
            onShowToast("送金しました（残高 \(event.newBalance) fuju）")
            viewModel.consumeSuccess()
            onTransferSucceeded()
        }
        .onChange(of: viewModel.errorMessage) { _, message in
            guard let message else { return }
            onShowToast(message)
            viewModel.consumeError()
        }
        // 確認ダイアログ。`.alert` は phase が .confirming / .submitting のときに出す。
        .alert(
            "送金内容の確認",
            isPresented: Binding(
                get: { viewModel.phase == .confirming || viewModel.phase == .submitting },
                set: { newValue in
                    if !newValue { viewModel.cancelConfirm() }
                },
            ),
            actions: {
                Button(viewModel.phase == .submitting ? "送金中..." : "送金する") {
                    viewModel.submit()
                }
                .disabled(viewModel.phase == .submitting)
                Button("キャンセル", role: .cancel) {
                    viewModel.cancelConfirm()
                }
                .disabled(viewModel.phase == .submitting)
            },
            message: {
                Text(
                    "送り先: \(viewModel.recipientExternalId.trimmingCharacters(in: .whitespaces))\n" +
                    "金額: \(viewModel.parsedAmount ?? 0) fuju",
                )
            },
        )
    }

    private var header: some View {
        // 取引履歴と同じヘッダー構造に揃える（タイトル中央寄せ、左に 48pt 戻るボタン）。
        ZStack {
            Text("送る")
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(FujupayPalette.textPrimary)

            HStack {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(FujupayPalette.textPrimary)
                        .frame(width: 48, height: 48)
                }
                .buttonStyle(.plain)
                Spacer()
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }

    private var form: some View {
        VStack(alignment: .leading, spacing: 16) {
            Spacer().frame(height: 8)

            VStack(alignment: .leading, spacing: 6) {
                Text("送り先（ユーザー ID）")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(FujupayPalette.textSecondary)
                TextField(
                    "usr_xxxx",
                    text: Binding(
                        get: { viewModel.recipientExternalId },
                        set: { viewModel.onRecipientChange($0) },
                    ),
                )
                .textFieldStyle(.plain)
                .autocorrectionDisabled(true)
                .textInputAutocapitalization(.never)
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .background(FujupayPalette.surface)
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(FujupayPalette.bottomBarBorder, lineWidth: 1),
                )
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .disabled(viewModel.phase != .editing)
            }

            VStack(alignment: .leading, spacing: 6) {
                Text("金額（fuju）")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(FujupayPalette.textSecondary)
                HStack(spacing: 8) {
                    TextField(
                        "1000",
                        text: Binding(
                            get: { viewModel.amountFuju },
                            set: { viewModel.onAmountChange($0) },
                        ),
                    )
                    .keyboardType(.numberPad)
                    .textFieldStyle(.plain)
                    .disabled(viewModel.phase != .editing)
                    Text("fuju")
                        .font(.system(size: 14))
                        .foregroundStyle(FujupayPalette.textSecondary)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .background(FujupayPalette.surface)
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(FujupayPalette.bottomBarBorder, lineWidth: 1),
                )
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }

            Spacer().frame(height: 8)

            submitButton
        }
        .padding(.horizontal, 16)
    }

    private var submitButton: some View {
        let submitting = viewModel.phase == .submitting
        let enabled = viewModel.canSubmit && !submitting
        return Button(action: { viewModel.requestConfirm() }) {
            HStack(spacing: 8) {
                if submitting {
                    ProgressView()
                        .progressViewStyle(.circular)
                        .tint(.white)
                        .scaleEffect(0.8)
                }
                Text(submitting ? "送金中..." : "送る")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(.white)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 52)
            .background(enabled ? FujupayPalette.brandPink : FujupayPalette.brandPink.opacity(0.4))
            .clipShape(RoundedRectangle(cornerRadius: 14))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }

    private func hideKeyboard() {
        // SwiftUI 標準ではキーボードを閉じる API が無いため UIKit にフォールバック。
        UIApplication.shared.sendAction(
            #selector(UIResponder.resignFirstResponder),
            to: nil,
            from: nil,
            for: nil,
        )
    }
}
