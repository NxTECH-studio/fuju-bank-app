import SwiftUI
import Shared

/// アカウントハブ画面 — Figma `697:8394` 準拠（Android `AccountHubScreen` と 1:1）。
///
/// 構成:
/// - プロフィールカード（円形アバター / 表示名 + 編集鉛筆 / ID）
/// - 「アカウント情報」セクション（表示名 / メールアドレス）
/// - 「設定」セクション（通知 / プライバシー設定 / アカウント情報）
///
/// 子画面遷移は親 `NavigationStack` の `navigationDestination(for:)` に値を渡して行う。
/// `AccountDestination` は `RootTabView` 側で定義されているので、本ビューはタップ時に値を
/// 親に通知するためのコールバック (`onSelectDestination`) を受け取る形にする。
/// （`NavigationLink(value:)` を使うと SettingsRowView の `Button` と二重になるので避ける）
struct AccountHubView: View {
    @StateObject private var viewModel = ObservableAccountHubViewModel()
    let onSelectDestination: (AccountDestination) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                ProfileCardView(
                    displayName: viewModel.profile.displayName,
                    accountId: viewModel.profile.accountId,
                )

                sectionLabel("アカウント情報")
                AccountInfoSectionView(
                    displayName: viewModel.profile.displayName,
                    email: viewModel.profile.email,
                )

                sectionLabel("設定")
                SettingsCardView(rows: [
                    .init(label: "通知") { onSelectDestination(.notifications) },
                    .init(label: "プライバシー設定") { onSelectDestination(.privacy) },
                    .init(label: "アカウント情報") { onSelectDestination(.accountEdit) },
                ])
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 16)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(FujuBankPalette.background.ignoresSafeArea())
    }

    /// Figma `697:8394` の「アカウント情報」「設定」見出し（12pt Bold）。
    private func sectionLabel(_ text: String) -> some View {
        Text(text)
            .font(FujuBankTypography.sectionLabel)
            .foregroundStyle(FujuBankPalette.textPrimary)
            .padding(.leading, 4)
            .accessibilityAddTraits(.isHeader)
    }
}

/// アカウントタブ配下の遷移先。`RootTabView` の `NavigationStack` で `navigationDestination`
/// するために値型として定義する。
enum AccountDestination: Hashable {
    case notifications
    case privacy
    case accountEdit
}
