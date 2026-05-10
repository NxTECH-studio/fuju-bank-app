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
    /// 送金フローで Step 1 → Step 2 を跨いで同一 VM を共有するため、`RootTabView` に
    /// `@StateObject` で保持する。送金完了後は `recreateSendFlowKey` をインクリメントして
    /// 新しい VM に差し替える（state 残留を防ぐため）。
    @StateObject private var sendFlowViewModel = ObservableSendFlowViewModel()
    @State private var sendFlowKey: Int = 0
    /// 送金完了後に Home 側で再 fetch を強制するためのキー。HomeView を `.id(...)` で再生成する。
    @State private var homeRefreshKey: Int = 0

    enum Destination: Equatable {
        case home, account, transactionHistory, transactionDetail, sendRecipient, sendAmount

        /// ホーム家族（Home / 履歴 / 詳細）はホームタブを selected 表示にする。
        var isHomeFamily: Bool {
            switch self {
            case .home, .transactionHistory, .transactionDetail: return true
            case .account, .sendRecipient, .sendAmount: return false
            }
        }

        /// 送金家族（Step 1 / Step 2）。フッターは送金フロー中は非表示なので selected
        /// 判定はタブから入った直後だけ意味を持つが、Equatable 整合のため定義しておく。
        var isSendFamily: Bool {
            switch self {
            case .sendRecipient, .sendAmount: return true
            default: return false
            }
        }
    }

    /// 法的文書 / パスワード変更 / 送金フロー中はボトムナビを隠す。法的文書は本文が長くフッターに
    /// 被って読めなくなるため、パスワード変更はキーボード操作中の入力欄が押し下がらない
    /// よう画面全体を縦に使うため、送金フローは誤タップ防止と画面集中のため
    /// （Android 側 `RootScaffold` で `bottomBarVisible = false` にしているのと同方針）。
    private var isBottomBarHidden: Bool {
        if destination.isSendFamily { return true }
        guard destination == .account else { return false }
        switch accountPath.last {
        case .privacyPolicy, .termsOfService, .passwordChange: return true
        default: return false
        }
    }

    var body: some View {
        // ボトムナビは原則全画面で表示する（Android RootScaffold は send 画面でのみ非表示にしていたが、
        // iOS 銀行版では send 画面が削除されたため常時表示でよい）。例外として法的文書画面のみ非表示。
        GeometryReader { geo in
            // バー全体 84pt のうち端末の bottom safe area inset (= ホームインジケータ高さ)
            // ぶんを差し引いた残りを「可視タブ領域」とみなしてコンテンツの inset を確保する。
            let visibleBarHeight = isBottomBarHidden ? 0 : max(0, 84 - geo.safeAreaInsets.bottom)
            ZStack(alignment: .bottom) {
                FujuBankPalette.background.ignoresSafeArea()

                content
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .safeAreaInset(edge: .bottom, spacing: 0) {
                        Color.clear.frame(height: visibleBarHeight)
                    }

                if !isBottomBarHidden {
                    bottomBar
                }

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
                onSendReceive: { destination = .sendRecipient },
                onShowToast: { message in toast.send(message) },
            )
            // 送金完了後の自動 refresh のため、`homeRefreshKey` の変化で View を作り直す。
            .id(homeRefreshKey)
        case .sendRecipient:
            SendRecipientView(
                viewModel: sendFlowViewModel,
                onBack: { destination = .home },
                onProceedToAmount: { destination = .sendAmount },
            )
            .id(sendFlowKey)
        case .sendAmount:
            SendAmountView(
                viewModel: sendFlowViewModel,
                onBack: { destination = .sendRecipient },
                onComplete: { _, _ in
                    toast.send("送金しました")
                    homeRefreshKey += 1
                    sendFlowKey += 1
                    sendFlowViewModel.resetToRecipient()
                    destination = .home
                },
            )
            .id(sendFlowKey)
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
                        PrivacySettingsView(
                            onSelectDestination: { dest in accountPath.append(dest) },
                        )
                    case .privacyPolicy:
                        LegalDocumentView(
                            title: PrivacyContent.shared.PRIVACY_POLICY_TITLE,
                            bodyText: PrivacyContent.shared.PRIVACY_POLICY_BODY,
                        )
                    case .termsOfService:
                        LegalDocumentView(
                            title: PrivacyContent.shared.TERMS_OF_SERVICE_TITLE,
                            bodyText: PrivacyContent.shared.TERMS_OF_SERVICE_BODY,
                        )
                    case .passwordChange:
                        PasswordChangeView(
                            onSuccess: { toast.send("パスワードを変更しました") },
                        )
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
        // client-bank-22 で 3 タブ化（ホーム / 送金 / アカウント）。
        ZStack(alignment: .top) {
            FujuBankPalette.surface
                .overlay(
                    Rectangle()
                        .frame(height: 1)
                        .foregroundColor(FujuBankPalette.bottomBarBorder),
                    alignment: .top,
                )

            HStack(spacing: 0) {
                tabItem(image: "BankHomeIcon", label: "ホーム", selected: destination.isHomeFamily) {
                    selectedTransaction = nil
                    destination = .home
                }
                .frame(maxWidth: .infinity)

                tabItem(image: "BankSendIcon", label: "送金", selected: destination.isSendFamily) {
                    selectedTransaction = nil
                    destination = .sendRecipient
                }
                .frame(maxWidth: .infinity)

                tabItem(image: "BankAccountIcon", label: "アカウント", selected: destination == .account) {
                    selectedTransaction = nil
                    destination = .account
                }
                .frame(maxWidth: .infinity)
            }
            .padding(.top, 8)
            .padding(.horizontal, 24)
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
