import SwiftUI
import Shared

/// アカウントハブ画面 — Figma `697:8394` 準拠（Android `AccountHubScreen` と 1:1）。
///
/// 構成:
/// - プロフィールカード（円形アバター / 表示名 + 編集鉛筆 / ID）
/// - 「アカウント情報」セクション（表示名 / メールアドレス、各行に編集鉛筆）
/// - 「設定」セクション（通知 / プライバシー設定）
///
/// 各行の鉛筆タップで `AccountInfoEditSheetView` を `.sheet(item:)` で開き、対応する
/// フィールド単独で編集する（client-bank-11 / Android client-bank-10 と同等）。
///
/// 子画面遷移は親 `NavigationStack` の `navigationDestination(for:)` に値を渡して行う。
/// `AccountDestination` は `RootTabView` 側で定義されているので、本ビューはタップ時に値を
/// 親に通知するためのコールバック (`onSelectDestination`) を受け取る形にする。
/// （`NavigationLink(value:)` を使うと SettingsRowView の `Button` と二重になるので避ける）
struct AccountHubView: View {
    @StateObject private var viewModel = ObservableAccountHubViewModel()
    let onSelectDestination: (AccountDestination) -> Void
    /// 編集中フィールド。`nil` のときシートは閉じている。
    @State private var editingField: AccountInfoField?
    /// ログアウト確認ダイアログの表示制御（client-bank-16）。
    @State private var showLogoutConfirm: Bool = false

    var body: some View {
        // MVP は受け取り専用のため AuthCore 側に email/displayName 更新 API が揃うまで
        // 編集 UI を無効化する。鉛筆アイコン非表示 + onEdit* no-op ガードで「タップしても
        // 何も起きない」状態にし、AccountInfoEditSheetView 側のコードは復活前提で残す。
        let editingEnabled = viewModel.editingEnabled

        // プロフィール取得失敗・取得前は空文字で来るので、UI 側で「-」プレースホルダに置換する。
        let displayNameOrPlaceholder = viewModel.profile.displayName.isEmpty
            ? Self.profilePlaceholder : viewModel.profile.displayName
        let emailOrPlaceholder = viewModel.profile.email.isEmpty
            ? Self.profilePlaceholder : viewModel.profile.email
        let accountIdOrPlaceholder = viewModel.profile.accountId.isEmpty
            ? Self.profilePlaceholder : viewModel.profile.accountId

        return ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                ProfileCardView(
                    displayName: displayNameOrPlaceholder,
                    accountId: accountIdOrPlaceholder,
                    editable: editingEnabled,
                )

                sectionLabel("アカウント情報")
                AccountInfoSectionView(
                    displayName: displayNameOrPlaceholder,
                    email: emailOrPlaceholder,
                    // editable=false の間は呼ばれないが、将来復活させた際の経路として残す。
                    onEditDisplayName: { if editingEnabled { editingField = .displayName } },
                    onEditEmail: { if editingEnabled { editingField = .email } },
                    editable: editingEnabled,
                )

                sectionLabel("設定")
                SettingsCardView(rows: [
                    .init(label: "通知") { onSelectDestination(.notifications) },
                    .init(label: "プライバシー設定") { onSelectDestination(.privacy) },
                    .init(label: "パスワード変更") { onSelectDestination(.passwordChange) },
                    // client-bank-16: 「設定」末尾にログアウト行を追加。行は通常スタイル
                    // （黒テキスト）で、destructive 表示は confirmationDialog 側に閉じる。
                    .init(label: "ログアウト") {
                        if !viewModel.isLoggingOut { showLogoutConfirm = true }
                    },
                ])
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 16)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(FujuBankPalette.background.ignoresSafeArea())
        .confirmationDialog(
            "ログアウトしますか？",
            isPresented: $showLogoutConfirm,
            titleVisibility: .visible,
        ) {
            // role: .destructive で iOS 標準の赤色になる（自前 color 指定は不要）。
            Button("ログアウト", role: .destructive) {
                viewModel.logout()
            }
            .disabled(viewModel.isLoggingOut)
            Button("キャンセル", role: .cancel) { }
        } message: {
            Text("再度利用するには再ログインが必要になります。")
        }
        .sheet(item: $editingField) { field in
            switch field {
            case .displayName:
                AccountInfoEditSheetView(
                    title: "表示名を編集",
                    label: "表示名",
                    initialValue: viewModel.profile.displayName,
                    keyboardType: .default,
                    contentType: .name,
                    autocapitalization: .sentences,
                    validate: { !$0.isEmpty },
                    onSave: { newValue in
                        viewModel.updateDisplayName(newValue)
                        editingField = nil
                    },
                    onCancel: { editingField = nil },
                )
            case .email:
                AccountInfoEditSheetView(
                    title: "メールアドレスを編集",
                    label: "メールアドレス",
                    initialValue: viewModel.profile.email,
                    keyboardType: .emailAddress,
                    contentType: .emailAddress,
                    autocapitalization: .never,
                    validate: { $0.contains("@") },
                    onSave: { newValue in
                        viewModel.updateEmail(newValue)
                        editingField = nil
                    },
                    onCancel: { editingField = nil },
                )
            }
        }
    }

    /// プロフィール未取得 / 取得失敗時のフィールド表示プレースホルダ（Android `PROFILE_PLACEHOLDER` と対称）。
    private static let profilePlaceholder = "-"

    /// Figma `697:8394` の「アカウント情報」「設定」見出し（12pt Bold）。
    private func sectionLabel(_ text: String) -> some View {
        Text(text)
            .font(FujuBankTypography.sectionLabel)
            .foregroundStyle(FujuBankPalette.textPrimary)
            .padding(.leading, 4)
            .accessibilityAddTraits(.isHeader)
    }
}

/// 「アカウント情報」セクションで編集中のフィールド。`.sheet(item:)` の駆動値を兼ねるため
/// `Identifiable` に準拠する。
enum AccountInfoField: String, Identifiable {
    case displayName
    case email

    var id: String { rawValue }
}

/// アカウントタブ配下の遷移先。`RootTabView` の `NavigationStack` で `navigationDestination`
/// するために値型として定義する。
///
/// `privacyPolicy` / `termsOfService` は `PrivacySettingsView` 配下のドリルダウン先で、
/// shared `PrivacyContent` の本文テキストを `LegalDocumentView` に流して表示する。
/// Android の `RootDestination.PrivacyPolicy` / `TermsOfService` と同等。
enum AccountDestination: Hashable {
    case notifications
    case privacy
    case privacyPolicy
    case termsOfService
    case passwordChange
}
