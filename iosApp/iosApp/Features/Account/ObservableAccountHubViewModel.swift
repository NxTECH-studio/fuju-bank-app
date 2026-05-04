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

    private let provider: AccountProfileProvider
    private var profileToken: FlowToken?

    init() {
        let p = KoinIosKt.accountProfileProvider()
        self.provider = p
        // Kotlin の generic StateFlow.value は Swift 側で `Any` として露出されるため
        // AccountProfile に明示キャストする（共有モジュールの型保証で必ず成功する）。
        self.profile = p.profile.value as! AccountProfile

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
