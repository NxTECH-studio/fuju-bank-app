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
}
