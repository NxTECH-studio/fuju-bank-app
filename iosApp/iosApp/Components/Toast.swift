import SwiftUI

/// 画面下部に短く出すトースト表示。SwiftUI には標準のトーストが無いため自前実装。
///
/// 利用側はルートビュー（`RootTabView` 等）に `.toast(message: $message)` を付け、
/// メッセージを `.send()` 経由で更新する。
final class ToastCenter: ObservableObject {
    @Published var message: String?

    func send(_ text: String, duration: TimeInterval = 1.8) {
        // 連続呼び出しで前のメッセージが残らないよう一旦 nil に倒してから差し替える。
        message = nil
        DispatchQueue.main.async { [weak self] in
            self?.message = text
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + duration) { [weak self] in
            // 呼び出し時のテキストと現在のテキストが一致するときだけ消す（次のメッセージが来ていたら維持）。
            if self?.message == text {
                self?.message = nil
            }
        }
    }
}

struct ToastOverlay: View {
    let message: String?

    var body: some View {
        VStack {
            Spacer()
            if let message {
                Text(message)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(.white)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 12)
                    .background(Color.black.opacity(0.78))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .padding(.bottom, 96)
                    .transition(.opacity.combined(with: .move(edge: .bottom)))
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .allowsHitTesting(false)
        .animation(.easeInOut(duration: 0.18), value: message)
    }
}
