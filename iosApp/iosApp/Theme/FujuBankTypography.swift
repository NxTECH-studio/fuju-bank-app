import SwiftUI

/// fuju 銀行アプリの軽量タイポグラフィトークン。Figma `bzm13wVWQmgaFFmlEbJZ3k`
/// (銀行版 6 画面) から抽出。Android `FujuBankTypography.kt` (`FujuBankTextStyles`)
/// と値・意味を完全一致させてある。
///
/// `Font.system(size:weight:)` のみで構成し、`SF Pro` (iOS 既定) にフォールバックする。
/// 日本語混在テキストでは iOS が自動的に `Hiragino Sans` などへフォールバックする。
enum FujuBankTypography {
    /// 画面タイトル（「取引履歴」「取引詳細」等）。Figma `Inter Bold 17` 相当。
    static let headline: Font = .system(size: 17, weight: .bold)

    /// リストカード内のタイトル（取引相手名・項目名）。Figma `Inter SemiBold 14`。
    static let title: Font = .system(size: 14, weight: .semibold)

    /// 本文（メールアドレス・表示名・本文テキスト）。Figma `Inter Medium 14`。
    static let body: Font = .system(size: 14, weight: .medium)

    /// キャプション（タイムスタンプ・サブ説明）。Figma `Inter Regular 12`。
    static let caption: Font = .system(size: 12, weight: .regular)

    /// セクションラベル（「最近の取引履歴」「アカウント情報」等）。Figma `SF Pro Bold 12`。
    static let sectionLabel: Font = .system(size: 12, weight: .bold)

    /// 残高金額の主要数字。Figma `SF Pro Bold 48`。
    static let amount: Font = .system(size: 48, weight: .bold)

    /// 残高金額の単位（「ふじゅ〜」）。Figma `SF Pro Bold 20`。
    static let amountUnit: Font = .system(size: 20, weight: .bold)

    /// 取引行の金額（一覧）。Figma の Credit カード金額に合わせて 15pt Bold。
    static let rowAmount: Font = .system(size: 15, weight: .bold)

    /// 取引詳細の大金額カード内の `+` `-` 記号。Figma で 40pt Bold。
    static let amountSign: Font = .system(size: 40, weight: .bold)

    /// ボトムナビのタブラベル。Figma で 8pt Bold（極小）。
    static let tabLabel: Font = .system(size: 8, weight: .bold)

    /// 「もっとみる」「再試行」等のリンク調ボタン。Figma で 12pt Bold。
    static let linkAction: Font = .system(size: 12, weight: .bold)
}
