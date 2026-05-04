import SwiftUI
import Shared

/// プライバシー設定画面 — Figma `798:12559` 準拠（Android `PrivacySettingsScreen` と 1:1）。
///
/// - ヘッダー: 戻る `<` (左 48pt) / タイトル「プライバシー設定」(中央 17pt Bold)
/// - セクション 1「トラッキング」: 単一カード「アプリのトラッキングを許可」+ サブテキスト + 右端トグル
/// - セクション 2「法的情報」: 「プライバシーポリシー」「利用規約」の 2 行リスト、
///   タップでアプリ内 `LegalDocumentView` へ遷移（Android と同じ方針。`PrivacyContent` は
///   URL ではなく本文テキストを保持しているため、外部ブラウザは使わない）。
///
/// 親画面遷移は `NavigationStack` に集約するため、本ビューは値を親に通知するためのコールバック
/// (`onSelectDestination`) を受け取る形にする（`AccountHubView` と同じパターン）。
struct PrivacySettingsView: View {
    @StateObject private var viewModel = ObservablePrivacySettingsViewModel()
    @Environment(\.dismiss) private var dismiss
    let onSelectDestination: (AccountDestination) -> Void

    var body: some View {
        VStack(spacing: 0) {
            header
            ScrollView {
                VStack(alignment: .leading, spacing: 8) {
                    sectionHeading("トラッキング")
                    TrackingOptInCard(
                        isOn: Binding(
                            get: { viewModel.analyticsOptInEnabled },
                            set: { viewModel.setAnalyticsOptInEnabled($0) }
                        )
                    )
                    sectionHeading("法的情報")
                    SettingsCardView(rows: [
                        .init(label: PrivacyContent.shared.PRIVACY_POLICY_TITLE) {
                            onSelectDestination(.privacyPolicy)
                        },
                        .init(label: PrivacyContent.shared.TERMS_OF_SERVICE_TITLE) {
                            onSelectDestination(.termsOfService)
                        },
                    ])
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
            .scrollIndicators(.hidden)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(FujuBankPalette.background.ignoresSafeArea())
        .navigationBarHidden(true)
    }

    private var header: some View {
        ZStack {
            Text("プライバシー設定")
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

    /// Figma `798:12559` のセクション見出し（12pt Bold、白カード外に左寄せ、px:8 py:8）。
    private func sectionHeading(_ text: String) -> some View {
        Text(text)
            .font(FujuBankTypography.sectionLabel)
            .foregroundStyle(FujuBankPalette.textSecondary)
            .padding(.horizontal, 8)
            .padding(.vertical, 8)
            .accessibilityAddTraits(.isHeader)
    }
}

/// トラッキング許諾の白角丸カード（タイトル + 説明 + トグル）。
private struct TrackingOptInCard: View {
    @Binding var isOn: Bool

    var body: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                Text("アプリのトラッキングを許可")
                    .font(FujuBankTypography.title)
                    .foregroundStyle(FujuBankPalette.textPrimary)
                Text("利用状況の分析と改善に使用されます")
                    .font(FujuBankTypography.caption)
                    .foregroundStyle(FujuBankPalette.textTertiary)
            }
            Spacer(minLength: 16)
            Toggle("", isOn: $isOn)
                .labelsHidden()
                .tint(FujuBankPalette.brandPink)
                .accessibilityLabel("アプリのトラッキングを許可")
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 16)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(FujuBankPalette.surface)
        )
        .shadow(color: FujuBankPalette.shadowTint.opacity(0.08), radius: 4, x: 0, y: 2)
    }
}
