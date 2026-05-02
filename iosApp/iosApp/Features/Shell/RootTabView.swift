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
        // フッター（ボトムナビ + 中央 FAB）はメインタブ（ホーム / アカウント）でのみ表示する。
        // 取引履歴 / 送る・もらう のサブ画面ではコンテンツを画面下端まで使えるよう非表示にする。
        let showBottomBar = destination == .home || destination == .account
        // GeometryReader で端末の bottom safe area inset (= ホームインジケータ高さ) を
        // 動的に取得し、コンテンツの bottom inset を「バー全体 84pt − インジケータ高さ」
        // に合わせる。iPhone 系 (34pt) では 50pt、iPad 系 (0pt) では 84pt が確保され、
        // どちらの端末でもバーの可視部分にコンテンツが潜り込まない。
        GeometryReader { geo in
            let visibleBarHeight = max(0, 84 - geo.safeAreaInsets.bottom)
            ZStack(alignment: .bottom) {
                FujupayPalette.background.ignoresSafeArea()

                content
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .safeAreaInset(edge: .bottom, spacing: 0) {
                        // バー非表示時はコンテンツ側の bottom inset も 0 にして画面下端まで使う。
                        Color.clear.frame(height: showBottomBar ? visibleBarHeight : 0)
                    }

                if showBottomBar {
                    bottomBar
                }

                ToastOverlay(message: toast.message)
            }
            // ZStack 自体の bottom edge を画面下端に揃える。これがないと alignment .bottom
            // が safe-area-bottom で止まり、バーが画面最下端まで届かない。
            .ignoresSafeArea(edges: .bottom)
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
            TransactionListView(
                onBack: { destination = .home },
                onNotificationTap: { toast.send("通知機能は実装中です") },
            )
        case .send:
            SendView(
                onBack: { destination = .home },
                onShowToast: { message in toast.send(message) },
                // 送金成功で Home に戻す。HomeView は @StateObject の再生成で
                // onAppear → load が走り直し、最新残高が反映される（A6 で realtime に移行予定）。
                onTransferSucceeded: { destination = .home },
            )
        }
    }

    private var bottomBar: some View {
        // バー全体: 84pt (上 50pt 可視タブ領域 + 下 34pt ホームインジケータ領域) で
        // 端末の最下端まで白を貼る。`.ignoresSafeArea(.bottom)` で safe area の
        // bottom inset を無効化して画面ボトム edge までレイアウトを伸ばす。
        // タブ・FAB・ボーダーは上 50pt に固定して home indicator と被らない。
        ZStack(alignment: .top) {
            // 白い bg + 上端 1pt ボーダー
            FujupayPalette.surface
                .overlay(
                    Rectangle()
                        .frame(height: 1)
                        .foregroundColor(FujupayPalette.bottomBarBorder),
                    alignment: .top,
                )

            // タブ群（バー上部 50pt 内に配置）
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
            .frame(height: 50, alignment: .top)
            .frame(maxWidth: .infinity)

            // 中央 pink 円形 FAB（バー上端から -13pt にせり出す）。64pt 円の中に
            // 28pt アイコン + 9pt ラベルを縦並びで中央寄せ。
            Button(action: { toast.send("支払い機能は実装中です") }) {
                VStack(spacing: 1) {
                    Image("FabPayQr")
                        .resizable()
                        .renderingMode(.template)
                        .foregroundColor(.white)
                        .scaledToFit()
                        .frame(width: 28, height: 28)
                    Text("支払い")
                        .font(.system(size: 9, weight: .bold))
                        .foregroundColor(.white)
                }
                .frame(width: 64, height: 64)
                .background(FujupayPalette.brandPink)
                .clipShape(Circle())
                .shadow(color: FujupayPalette.shadowTint.opacity(0.18), radius: 6, x: 0, y: 4)
            }
            .buttonStyle(.plain)
            // タブは上 50pt にあるので、その上端から -13pt にせり出す位置を計算。
            // ZStack(alignment:.top) の中で alignment .top → -13 で OK。
            .offset(y: -13)
        }
        .frame(height: 84)
        .frame(maxWidth: .infinity)
        // ※ 親 ZStack に既に `.ignoresSafeArea(edges: .bottom)` を適用しているため、
        //    ここでは不要 (重複適用するとレイアウト警告の原因)。
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
