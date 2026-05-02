import SwiftUI

/// ホーム / アカウントの 2 タブ + 中央 FAB（マゼンタの「支払い」円形ボタン）を提供する
/// ルートシェル。Figma `89:12356` の `43:258` ボトムナビ準拠。
///
/// `TabView` は中央 FAB をネイティブに表現できないため、自前のバーを `safeAreaInset`
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
        ZStack(alignment: .top) {
            // 84pt のバー / pt-8 / 左右 px-48 / 中央は FAB スペース。
            HStack(spacing: 0) {
                // 左：ホーム（右寄せ、pr-64 で中央 FAB と離す）
                HStack {
                    Spacer()
                    tabItem(image: "TabHomeFilled", label: "ホーム", selected: destination.isHomeFamily) {
                        destination = .home
                    }
                }
                .padding(.trailing, 64)
                .frame(maxWidth: .infinity, alignment: .trailing)

                // 右：アカウント（左寄せ、pl-64）
                HStack {
                    tabItem(image: "TabAccountCircle", label: "アカウント", selected: destination == .account) {
                        destination = .account
                    }
                    Spacer()
                }
                .padding(.leading, 64)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.top, 8)
            .padding(.horizontal, 48)
            .frame(height: 84)
            .frame(maxWidth: .infinity)
            .background(FujupayPalette.surface)
            .overlay(
                Rectangle()
                    .frame(height: 1)
                    .foregroundColor(Color(red: 239/255, green: 239/255, blue: 239/255)),
                alignment: .top
            )

            // 中央 pink 円形 FAB（top -13）。アイコン + 「支払い」ラベルを内蔵。
            Button(action: { toast.send("支払い機能は実装中です") }) {
                VStack(spacing: 2) {
                    Image("FabPayQr")
                        .resizable()
                        .renderingMode(.template)
                        .foregroundColor(.white)
                        .scaledToFit()
                        .frame(width: 32, height: 32)
                    Text("支払い")
                        .font(.system(size: 8, weight: .bold))
                        .foregroundColor(.white)
                }
                .padding(.top, 10)
                .padding(.bottom, 22)
                .padding(.horizontal, 16)
                .frame(width: 64, height: 64)
                .background(FujupayPalette.brandPink)
                .clipShape(RoundedRectangle(cornerRadius: 58))
                .shadow(color: Color(red: 30/255, green: 34/255, blue: 42/255).opacity(0.08), radius: 6, x: 0, y: 4)
            }
            .buttonStyle(.plain)
            .offset(y: -13)
        }
    }

    private func tabItem(
        image: String,
        label: String,
        selected: Bool,
        action: @escaping () -> Void,
    ) -> some View {
        let labelColor = selected ? Color.black : FujupayPalette.textTertiary
        // Figma では Frame 幅 32 に対して「アカウント」テキストが 40 と幅を超えており、
        // 横にはみ出す前提のレイアウト。VStack の幅は固定せず、ラベル幅まで広げて改行を防ぐ。
        return Button(action: action) {
            VStack(spacing: 0) {
                Image(image)
                    .resizable()
                    .renderingMode(.original)
                    .scaledToFit()
                    .frame(width: 32, height: 32)
                Text(label)
                    .font(.system(size: 8, weight: .bold))
                    .foregroundStyle(labelColor)
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
            }
        }
        .buttonStyle(.plain)
    }
}
