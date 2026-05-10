import SwiftUI
import UIKit

/// Screen 4 (MFA セットアップ): リカバリコード表示。
///
/// - 8〜10 個の recovery codes を等幅フォントで表示。
/// - 「すべてコピー」でクリップボードに改行区切りでコピー。
/// - 「保存しました」チェックを ON にしないと CTA enable しない。
/// - 戻るボタン抑止（NavigationStack 不使用）。AuthCore は再表示不可。
struct RecoveryCodesView: View {
    @EnvironmentObject var flow: SignUpFlowState
    let onComplete: () -> Void

    var body: some View {
        ZStack {
            SignUpTokens.background
                .ignoresSafeArea()
            VStack(spacing: 0) {
                SignUpHeader(onBack: nil)
                    .padding(.horizontal, 10)
                VStack(alignment: .leading, spacing: 12) {
                    Text("リカバリコードを保存")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(SignUpTokens.primaryText)
                    Text("認証アプリを失った場合のバックアップとして使用します。\n以下のコードは **この画面でしか表示されません**。安全な場所に必ず保存してください。")
                        .font(.system(size: 13, weight: .regular))
                        .foregroundColor(SignUpTokens.secondaryText)
                        .lineSpacing(4)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 24)
                .padding(.vertical, 12)

                ScrollView {
                    VStack(alignment: .leading, spacing: 8) {
                        let codes = flow.mfaSetup?.recoveryCodes ?? []
                        if codes.isEmpty {
                            Text("リカバリコードを準備中...")
                                .font(.system(size: 13))
                                .foregroundColor(SignUpTokens.secondaryText)
                        } else {
                            ForEach(codes, id: \.self) { code in
                                Text(code)
                                    .font(.system(size: 16, weight: .medium, design: .monospaced))
                                    .foregroundColor(SignUpTokens.primaryText)
                            }
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(16)
                }
                .frame(maxHeight: 280)
                .background(SignUpTokens.card)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .padding(.horizontal, 24)

                Spacer().frame(height: 12)

                Button(action: copyAll) {
                    Text("すべてコピー")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(SignUpTokens.primary)
                        .frame(maxWidth: .infinity)
                        .frame(height: 40)
                        .background(SignUpTokens.card)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
                .disabled((flow.mfaSetup?.recoveryCodes.isEmpty ?? true))
                .padding(.horizontal, 24)

                Spacer().frame(height: 12)

                HStack(spacing: 4) {
                    Image(systemName: flow.recoverySaved ? "checkmark.square.fill" : "square")
                        .font(.system(size: 22, weight: .regular))
                        .foregroundColor(flow.recoverySaved ? SignUpTokens.primary : SignUpTokens.secondaryText)
                    Text("リカバリコードを安全な場所に保存しました")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(SignUpTokens.primaryText)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 24)
                .contentShape(Rectangle())
                .onTapGesture { flow.recoverySaved.toggle() }

                Spacer()
                PageIndicator(total: 4, activeIndex: 3)
                    .padding(.bottom, 12)
                PrimaryButton(
                    title: "完了",
                    enabled: flow.recoverySaved && flow.pendingUserId != nil,
                    action: {
                        // confirmRecoveryCodes は SessionStore.setAuthenticated まで実行する。
                        // SwiftUI 側はその後で signupRoute をリセットする責務だけ持つ。
                        if flow.confirmRecoveryCodes() {
                            onComplete()
                        }
                    },
                )
                .padding(.horizontal, 24)
                .padding(.bottom, 16)
            }
        }
    }

    private func copyAll() {
        let codes = flow.mfaSetup?.recoveryCodes ?? []
        guard !codes.isEmpty else { return }
        UIPasteboard.general.string = codes.joined(separator: "\n")
    }
}

#Preview {
    RecoveryCodesView(onComplete: {})
        .environmentObject(SignUpFlowState())
}
