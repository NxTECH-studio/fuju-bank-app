import Foundation

/// サインアップフロー全体（アカウント作成 → OTP → 成功）で共有する入力状態。
///
/// 戻る/進むで入力値が消えないよう、3 View が同じインスタンスを `@EnvironmentObject` で参照する。
/// API 連携・本番バリデーションは本タスクのスコープ外。
final class SignUpFlowState: ObservableObject {
    @Published var email: String = ""
    @Published var password: String = ""
    @Published var otp: String = ""

    static let otpLength = 6

    func reset() {
        email = ""
        password = ""
        otp = ""
    }

    func updateOtp(_ raw: String) {
        // 数字のみ・最大 otpLength 桁にサニタイズして保持。
        let digits = raw.filter { $0.isNumber }
        otp = String(digits.prefix(Self.otpLength))
    }
}
