import SwiftUI

/// fuju 銀行アプリのカラートークン。Figma `bzm13wVWQmgaFFmlEbJZ3k` (銀行版 6 画面)
/// から抽出。Android `FujuBankColors.kt` と値を完全一致させてある。
///
/// Material のセマンティクスとぶつかるため、Asset Catalog ではなく構造体で持ち、
/// 画面側から `FujuBankPalette.brandPink` のように直接参照する。
enum FujuBankPalette {
    /// 画面の地の色。Figma 全画面で共通 (`#F6F7F9`)。
    static let background = Color(red: 0xF6 / 255, green: 0xF7 / 255, blue: 0xF9 / 255)

    /// 残高カードや取引履歴カードなど白い面 (`#FFFFFF`)。
    static let surface = Color.white

    /// 主要テキスト (`#111111`)。Figma 銀行版で統一。
    static let textPrimary = Color(red: 0x11 / 255, green: 0x11 / 255, blue: 0x11 / 255)

    /// 補助テキスト（タイムスタンプ・サブ説明）(`#6E6F72`)。
    static let textSecondary = Color(red: 0x6E / 255, green: 0x6F / 255, blue: 0x72 / 255)

    /// より淡い補助テキスト（ID・フォームラベル・無効ナビ）(`#B0B0B0`)。
    static let textTertiary = Color(red: 0xB0 / 255, green: 0xB0 / 255, blue: 0xB0 / 255)

    /// ブランドアクセント。金額表示・通知バッジ・トグル ON (`#FF1E9E`)。
    static let brandPink = Color(red: 0xFF / 255, green: 0x1E / 255, blue: 0x9E / 255)

    /// リンクテキスト（「もっとみる」等）(`#187AEA`)。
    static let linkBlue = Color(red: 0x18 / 255, green: 0x7A / 255, blue: 0xEA / 255)

    /// 通知ベルの未読ドット (`#FF0000`)。
    static let notificationDot = Color(red: 0xFF / 255, green: 0x00 / 255, blue: 0x00 / 255)

    /// ボトムナビ上部の細いボーダー (`#EFEFEF`)。
    static let bottomBarBorder = Color(red: 0xEF / 255, green: 0xEF / 255, blue: 0xEF / 255)

    /// 取引履歴カードの区切り (`#EFEFEF`)。
    static let transactionDivider = Color(red: 0xEF / 255, green: 0xEF / 255, blue: 0xEF / 255)

    /// アバター枠やフォーム内の細い区切り線 (`#E9EBED`)。
    static let hairline = Color(red: 0xE9 / 255, green: 0xEB / 255, blue: 0xED / 255)

    /// 取引相手アバターの暫定プレースホルダ（人物用）(`#E5E5E5`)。
    static let avatarPerson = Color(red: 0xE5 / 255, green: 0xE5 / 255, blue: 0xE5 / 255)

    /// 取引相手アバターの暫定プレースホルダ（アーティファクト用）(`#C9D8E1`)。
    static let avatarArtifact = Color(red: 0xC9 / 255, green: 0xD8 / 255, blue: 0xE1 / 255)

    /// drop-shadow rgba(30, 34, 42, ...) 系のシャドウベース色 (透明度は呼び出し側で指定)。
    /// Figma の銀行版カードに薄く敷くドロップシャドウに使用する。
    static let shadowTint = Color(red: 30 / 255, green: 34 / 255, blue: 42 / 255)
}
