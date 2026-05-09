import Foundation
import Shared

/// アカウントハブ画面の ViewModel。
///
/// `:shared` 側 `AccountProfileProvider` の `StateFlow<AccountProfile>` を Swift 側から
/// 観測できるよう公開された `AccountIosKt.observeAccountProfile`（`observeDepositEnabled` /
/// `observeTransferEnabled` と同一パターン）を購読し、`@Published` に転写する。
///
/// 編集操作は [updateDisplayName] / [updateEmail] で Provider に書き戻す。Provider 側が
/// in-memory state を持つため、保存時に Provider が更新されると `observeAccountProfile`
/// 経由で UI に即時反映される（Android `AccountHubViewModel` と同等）。
@MainActor
final class ObservableAccountHubViewModel: ObservableObject {
    @Published private(set) var profile: AccountProfile

    /// ログアウト処理中フラグ（client-bank-16）。true の間は確認ダイアログの「ログアウト」
    /// ボタンを `disabled(...)` にしてリスト行タップでもダイアログを開かないようにする。
    /// Android `AccountHubViewModel.isLoggingOut` と対称。
    @Published private(set) var isLoggingOut: Bool = false

    /// MVP は受け取り専用で AuthCore 側に email/displayName 更新 API が揃っていないため、
    /// 編集 UI は一旦無効化する。Android `AccountHubScreen` の `editingEnabled = false` と
    /// 対称。鉛筆アイコン非表示 + sheet 起動経路の no-op ガードに用いる。
    /// `AccountInfoEditSheetView` 自体は将来の復活前提でコードを残す。
    let editingEnabled: Bool = false

    private let provider: AccountProfileProvider
    private var profileToken: FlowToken?

    init() {
        let p = KoinIosKt.accountProfileProvider()
        self.provider = p
        // Kotlin generic な StateFlow.value は Swift 側で `Any` として露出されるため、
        // 型保証された初期値取得用ブリッジ `currentAccountProfile` を経由して force cast を避ける。
        self.profile = AccountIosKt.currentAccountProfile(provider: p)

        profileToken = AccountIosKt.observeAccountProfile(provider: p) { [weak self] next in
            // observeFlow は Dispatchers.Main で collect されるが、UI 反映は @MainActor で
            // 確実にメインアクタへホップさせる（既存 ObservableNotificationSettingsViewModel と同パターン）。
            Task { @MainActor in
                self?.profile = next
            }
        }
    }

    deinit {
        profileToken?.close()
    }

    /// 表示名のみ更新。メールアドレスは現在値を維持する。
    func updateDisplayName(_ displayName: String) {
        provider.updateProfile(displayName: displayName, email: profile.email)
    }

    /// メールアドレスのみ更新。表示名は現在値を維持する。
    func updateEmail(_ email: String) {
        provider.updateProfile(displayName: profile.displayName, email: email)
    }

    /// ログアウト処理（client-bank-16）。
    ///
    /// `AuthFlowIosKt.logoutAndClear` がサーバ呼び出しの結果に関わらず最後に
    /// `SessionStore.clear()` を呼んで `Unauthenticated` に倒すので、UI 側は完了を待って
    /// `isLoggingOut` を戻すだけで良い（Login 画面への遷移は `AppRoot` 側のセッション分岐に任せる）。
    /// 二度押し防止に `isLoggingOut` でガードする（Android `AccountHubViewModel.logout` と対称）。
    func logout() {
        guard !isLoggingOut else { return }
        isLoggingOut = true
        AuthFlowIosKt.logoutAndClear(
            authRepository: KoinIosKt.authRepository(),
            sessionStore: KoinIosKt.sessionStore()
        ) { [weak self] in
            // logoutAndClear のコールバックは shared 側 SessionStore.scope (Dispatchers.Main)
            // で発火するが、@MainActor の整合をはっきりさせるため Task でホップする
            // （他 Observable VM と同パターン）。
            Task { @MainActor in
                self?.isLoggingOut = false
            }
        }
    }
}
