import SwiftUI

/// アカウントハブ画面（Figma `697:8394`）の「設定」セクション内 1 行。
///
/// Android `SettingsRow.kt` と 1:1。
/// - 左に項目ラベル（14pt Medium）
/// - 右に chevron-right（14pt、TextTertiary）
/// - 行全体ボタン
struct SettingsRowView: View {
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack {
                Text(label)
                    .font(FujuBankTypography.body)
                    .foregroundStyle(FujuBankPalette.textPrimary)
                Spacer()
                Image("ChevronRight")
                    .renderingMode(.template)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 14, height: 14)
                    .foregroundStyle(FujuBankPalette.textTertiary)
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 16)
            .padding(.vertical, 16)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityHint("\(label)の画面に進みます")
    }
}

/// 複数の [SettingsRowView] を 1 枚の白角丸カードでまとめるためのコンテナ。
/// 行の間に 1pt の hairline divider を入れる。Android `SettingsCard` と同等。
struct SettingsCardView: View {
    struct Row: Identifiable {
        let id = UUID()
        let label: String
        let action: () -> Void
    }

    let rows: [Row]

    var body: some View {
        VStack(spacing: 0) {
            ForEach(Array(rows.enumerated()), id: \.element.id) { index, row in
                SettingsRowView(label: row.label, action: row.action)
                if index != rows.count - 1 {
                    Divider()
                        .frame(height: 1)
                        .overlay(FujuBankPalette.hairline)
                        .padding(.horizontal, 16)
                }
            }
        }
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(FujuBankPalette.surface)
        )
        .shadow(color: FujuBankPalette.shadowTint.opacity(0.08), radius: 4, x: 0, y: 2)
    }
}
