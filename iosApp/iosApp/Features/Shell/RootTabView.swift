import SwiftUI

/// ホーム / アカウントの 2 タブ + 中央 FAB（マゼンタの「支払い」ボタン）を提供する
/// ルートシェル。
///
/// `TabView` は中央 FAB をネイティブに表現できないため、自前のタブバーを `safeAreaInset`
/// で底面に貼り、`ZStack` で中央 FAB をオーバーレイする。
struct RootTabView: View {
    @StateObject private var toast = ToastCenter()
    @State private var destination: Destination = .home

    enum Destination: Equatable {
        case home, account, transactionHistory, send

        var isHomeFamily: Bool {
            switch self {
            case .home, .transactionHistory, .send: return true
            case .account: return false
            }
        }
    }

    var body: some View {
        ZStack {
            FujupayPalette.background.ignoresSafeArea()
            content
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .safeAreaInset(edge: .bottom, spacing: 0) {
                    bottomBar
                }
            ToastOverlay(message: toast.message)
        }
    }

    @ViewBuilder
    private var content: some View {
        switch destination {
        case .home:
            HomeView(
                onTransactionHistory: { destination = .transactionHistory },
                onSendReceive: { destination = .send },
                onShowToast: { message in toast.send(message) },
            )
        case .account:
            AccountPlaceholderView()
        case .transactionHistory:
            ComingSoonView(title: "取引履歴", onBack: { destination = .home })
        case .send:
            ComingSoonView(title: "送る・もらう", onBack: { destination = .home })
        }
    }

    private var bottomBar: some View {
        ZStack {
            HStack {
                tabItem(symbol: "house.fill", label: "ホーム", selected: destination.isHomeFamily) {
                    destination = .home
                }
                Spacer()
                Color.clear.frame(width: 72, height: 1) // 中央 FAB 分のスペース
                Spacer()
                tabItem(symbol: "person.circle", label: "アカウント", selected: destination == .account) {
                    destination = .account
                }
            }
            .padding(.horizontal, 24)
            .frame(height: 64)
            .frame(maxWidth: .infinity)
            .background(FujupayPalette.surface)

            Button(action: { toast.send("支払い機能は実装中です") }) {
                ZStack {
                    Circle()
                        .fill(FujupayPalette.brandPink)
                        .frame(width: 56, height: 56)
                        .shadow(color: Color.black.opacity(0.18), radius: 6, x: 0, y: 3)
                    Image(systemName: "qrcode")
                        .font(.system(size: 22, weight: .semibold))
                        .foregroundColor(.white)
                }
            }
            .buttonStyle(.plain)
            .offset(y: -13)
        }
    }

    private func tabItem(
        symbol: String,
        label: String,
        selected: Bool,
        action: @escaping () -> Void,
    ) -> some View {
        let tint = selected ? FujupayPalette.brandPink : FujupayPalette.textTertiary
        return Button(action: action) {
            VStack(spacing: 2) {
                Image(systemName: symbol)
                    .font(.system(size: 22, weight: .regular))
                    .foregroundStyle(tint)
                Text(label)
                    .font(.system(size: 10, weight: .medium))
                    .foregroundStyle(tint)
            }
        }
        .buttonStyle(.plain)
    }
}
