import SwiftUI

/// fujupay ホーム画面 (Figma `89:12356`) のカラートークン。
///
/// Material のセマンティクスとぶつかるため、Asset Catalog ではなく構造体で持ち、
/// 画面側から `FujupayPalette.brandPink` のように直接参照する。
enum FujupayPalette {
    static let background = Color(red: 0xF6 / 255, green: 0xF7 / 255, blue: 0xF9 / 255)
    static let surface = Color.white
    static let textPrimary = Color(red: 0x11 / 255, green: 0x11 / 255, blue: 0x11 / 255)
    static let textSecondary = Color(red: 0x4B / 255, green: 0x4C / 255, blue: 0x50 / 255)
    static let textTertiary = Color(red: 0xB0 / 255, green: 0xB0 / 255, blue: 0xB0 / 255)
    static let brandPink = Color(red: 0xFF / 255, green: 0x1E / 255, blue: 0x9E / 255)
    static let actionPurple = Color(red: 0x9E / 255, green: 0x1E / 255, blue: 0xFF / 255)
    static let actionGreen = Color(red: 0x0C / 255, green: 0xD8 / 255, blue: 0x0C / 255)
    static let actionBlue = Color(red: 0x1E / 255, green: 0x83 / 255, blue: 0xFF / 255)
    /// drop-shadow rgba(30, 34, 42, ...) 系のシャドウベース色 (透明度は呼び出し側で指定)。
    static let shadowTint = Color(red: 30 / 255, green: 34 / 255, blue: 42 / 255)
    /// ボトムバー上端の細いボーダー。
    static let bottomBarBorder = Color(red: 0xEF / 255, green: 0xEF / 255, blue: 0xEF / 255)
    /// 取引履歴行の補助テキスト（日時 / 店舗名）。Figma `410:20343` 参照。
    static let transactionMeta = Color(red: 0x70 / 255, green: 0x72 / 255, blue: 0x75 / 255)
    /// 取引履歴行の区切り線。
    static let transactionDivider = Color(red: 0xEF / 255, green: 0xEF / 255, blue: 0xEF / 255)
    /// 取引相手アバターの暫定プレースホルダ（人物用）。
    static let avatarPerson = Color(red: 0xE5 / 255, green: 0xE5 / 255, blue: 0xE5 / 255)
    /// 取引相手アバターの暫定プレースホルダ（アーティファクト用）。
    static let avatarArtifact = Color(red: 0xC9 / 255, green: 0xD8 / 255, blue: 0xE1 / 255)
}
