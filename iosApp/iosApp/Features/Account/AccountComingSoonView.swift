import SwiftUI

/// アカウントタブ配下の準備中画面。プライバシー設定 / アカウント情報変更の遷移先で使用する。
///
/// Android `AccountComingSoonScreen` と 1:1。`NotificationSettingsView` と同じく chevron-back
/// 付きヘッダーを持ち、本文は中央に「準備中です」を表示。タイトルだけ呼び出し側で差し替えて再利用する。
///
/// `Features/Placeholder/ComingSoonView.swift` は別タブ（取引等）用の汎用版なので、ヘッダー構造の
/// 揃ったアカウント専用版を別途定義する。
struct AccountComingSoonView: View {
    let title: String
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 0) {
            header
            Spacer()
            Text("準備中です")
                .font(.system(size: 16, weight: .medium))
                .foregroundStyle(FujuBankPalette.textSecondary)
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(FujuBankPalette.background.ignoresSafeArea())
        .navigationBarHidden(true)
    }

    private var header: some View {
        ZStack {
            Text(title)
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
}
