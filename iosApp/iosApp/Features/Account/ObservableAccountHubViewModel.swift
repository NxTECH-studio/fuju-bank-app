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
}
