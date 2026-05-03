import SwiftUI
import Shared

/// ホーム / アカウントの 2 タブを提供するルートシェル — Figma `709:8658` / `697:7601` /
/// `702:6440` の bottomBar 準拠。
///
/// 銀行版では旧 fujupay の中央ピンク FAB（支払い）が撤去され、2 タブが均等配置になっている
/// （Android `RootScaffold.kt` と同一）。SwiftUI の `TabView` を使わずに自前バーを
/// `safeAreaInset` で底面に貼って描画する形は引き続き採用する（Figma 上の余白・1pt ボーダー・
/// アイコンサイズ・ラベルサイズを正確に出すため）。
///
/// 画面遷移:
/// - ホーム → 取引履歴: HomeView の「もっとみる」タップ
/// - 取引履歴 → 取引詳細: 行タップ
/// - 取引詳細 / 取引履歴での戻る: ボトムナビは表示したままで前画面へ復帰
struct RootTabView: View {
    @StateObject private var toast = ToastCenter()
    @State private var destination: Destination = .home
    /// 取引詳細に遷移する際の対象。プロセス中は `@State` で保持し、別取引タップ時に上書きする。
    @State private var selectedTransaction: Shared.Transaction?
    /// アカウントタブ配下の `NavigationStack` のパス。タブを切り替えても保持し、戻ってきたとき
    /// に元の階層を復元する（Android の手動スタックと挙動を揃える狙い）。
    @State private var accountPath: [AccountDestination] = []

    enum Destination: Equatable {
        case home, account, transactionHistory, transactionDetail

        /// ホーム家族（Home / 履歴 / 詳細）はホームタブを selected 表示にする。
        var isHomeFamily: Bool {
            switch self {
            case .home, .transactionHistory, .transactionDetail: return true
            case .account: return false
            }
        }
    }

    var body: some View {
        // ボトムナビは全画面で表示する（Android RootScaffold は send 画面でのみ非表示にしていたが、
        // iOS 銀行版では send 画面が削除されたため常時表示でよい）。
        GeometryReader { geo in
            // バー全体 84pt のうち端末の bottom safe area inset (= ホームインジケータ高さ)
            // ぶんを差し引いた残りを「可視タブ領域」とみなしてコンテンツの inset を確保する。
            let visibleBarHeight = max(0, 84 - geo.safeAreaInsets.bottom)
            ZStack(alignment: .bottom) {
                FujuBankPalette.background.ignoresSafeArea()

                content
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .safeAreaInset(edge: .bottom, spacing: 0) {
                        Color.clear.frame(height: visibleBarHeight)
                    }

                bottomBar

                ToastOverlay(message: toast.message)
            }
            .ignoresSafeArea(edges: .bottom)
        }
    }

    @ViewBuilder
    private var content: some View {
        switch destination {
        case .home:
            HomeView(
                onTransactionHistory: { destination = .transactionHistory },
                onSendReceive: { /* 銀行版では未配線。旧シグネチャ互換 */ },
                onShowToast: { message in toast.send(message) },
            )
        case .account:
            // アカウントタブは NavigationStack をルートにし、ハブ → 子画面（通知設定 / 準備中）の
            // 遷移を `navigationDestination(for:)` に集約する。`accountPath` を `RootTabView` 側で
            // 保持することでタブ切り替え時も階層がリセットされない。
            NavigationStack(path: $accountPath) {
                AccountHubView(
                    onSelectDestination: { dest in accountPath.append(dest) },
                )
                .navigationDestination(for: AccountDestination.self) { dest in
                    switch dest {
                    case .notifications:
                        NotificationSettingsView(
                            onNotificationTap: { toast.send("通知機能は実装中です") },
                        )
                    case .privacy:
                        AccountComingSoonView(title: "プライバシー設定")
                    case .accountEdit:
                        AccountComingSoonView(title: "アカウント情報変更")
                    }
                }
            }
        case .transactionHistory:
            TransactionListView(
                onBack: { destination = .home },
                onNotificationTap: { toast.send("通知機能は実装中です") },
                onTransactionTap: { transaction in
                    selectedTransaction = transaction
                    destination = .transactionDetail
                },
            )
        case .transactionDetail:
            // 何らかの理由で対象 Transaction が失われた場合（プロセス再生成等）は履歴へ戻す。
            if let transaction = selectedTransaction {
                TransactionDetailView(
                    transaction: transaction,
                    onBack: { destination = .transactionHistory },
                    onNotificationTap: { toast.send("通知機能は実装中です") },
                )
                // 同 ViewModel が別取引タップで使い回されないよう、id で差し替えを強制する
                .id(transaction.id)
            } else {
                Color.clear.onAppear { destination = .transactionHistory }
            }
        }
    }

    private var bottomBar: some View {
        // バー全体 84pt: 上 50pt 可視タブ領域 + 下 34pt ホームインジケータ領域。
        // Figma `709:8658` の `pt-8 px-48`、上端 1pt ボーダー、白背景に揃える。
        // 2 タブが均等 weight=1 で並び、それぞれ内側 64pt の余白で中央へ寄せる
        // （Android `RootScaffold.kt` の BottomNav と同一構造）。
        ZStack(alignment: .top) {
            FujuBankPalette.surface
                .overlay(
                    Rectangle()
                        .frame(height: 1)
                        .foregroundColor(FujuBankPalette.bottomBarBorder),
                    alignment: .top,
                )

            HStack(spacing: 0) {
                HStack {
                    Spacer()
                    tabItem(image: "BankHomeIcon", label: "ホーム", selected: destination.isHomeFamily) {
                        selectedTransaction = nil
                        destination = .home
                    }
                }
                .padding(.trailing, 64)
                .frame(maxWidth: .infinity, alignment: .trailing)

                HStack {
                    tabItem(image: "BankAccountIcon", label: "アカウント", selected: destination == .account) {
                        selectedTransaction = nil
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
        }
        .frame(height: 84)
        .frame(maxWidth: .infinity)
    }

    private func tabItem(
        image: String,
        label: String,
        selected: Bool,
        action: @escaping () -> Void,
    ) -> some View {
        let tabColor = selected ? Color.black : FujuBankPalette.textTertiary
        // Figma では Frame 幅 32 に対して「アカウント」テキストが幅を超えており、横にはみ出す
        // 前提のレイアウト。VStack の幅は固定せず、ラベル幅まで広げて改行を防ぐ。
        return Button(action: action) {
            VStack(spacing: 0) {
                Image(image)
                    .renderingMode(.template)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 32, height: 32)
                    .foregroundStyle(tabColor)
                Text(label)
                    .font(FujuBankTypography.tabLabel)
                    .foregroundStyle(tabColor)
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
            }
        }
        .buttonStyle(.plain)
    }
}
