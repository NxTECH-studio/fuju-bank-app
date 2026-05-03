import Foundation
import Shared

/// 通知設定画面の ViewModel。
///
/// shared 側 `NotificationSettingsPreferences` の `StateFlow<Boolean>` 2 つを Swift 側から
/// 観測できるよう、`AccountIos.kt` で公開された `observeDepositEnabled` / `observeTransferEnabled`
/// （`SessionStoreIos.observeBootstrapped` と同一パターン）を購読し、`@Published` に転写する。
///
/// SwiftUI の `Toggle` は `Binding<Bool>` を要求するため、画面側では
/// `Binding(get: { vm.depositEnabled }, set: { vm.setDepositEnabled($0) })` の形で
/// 書き戻し API を経由する。`@Published` の直接バインドは shared への永続化を伴わないので避ける。
@MainActor
final class ObservableNotificationSettingsViewModel: ObservableObject {
    @Published private(set) var depositEnabled: Bool
    @Published private(set) var transferEnabled: Bool

    private let preferences: NotificationSettingsPreferences
    private var depositToken: FlowToken?
    private var transferToken: FlowToken?

    init() {
        let prefs = KoinIosKt.notificationSettingsPreferences()
        self.preferences = prefs
        // observeXxxEnabled は subscribe 直後に現在値を 1 回 emit するので、初期値は仮で
        // false を入れておき、購読開始直後のコールバックで正しい値に上書きされる。
        self.depositEnabled = false
        self.transferEnabled = false

        depositToken = AccountIosKt.observeDepositEnabled(preferences: prefs) { [weak self] value in
            // Kotlin の Boolean は Swift から KotlinBoolean として渡ってくる。
            let on = value.boolValue
            Task { @MainActor in
                self?.depositEnabled = on
            }
        }
        transferToken = AccountIosKt.observeTransferEnabled(preferences: prefs) { [weak self] value in
            let on = value.boolValue
            Task { @MainActor in
                self?.transferEnabled = on
            }
        }
    }

    deinit {
        depositToken?.close()
        transferToken?.close()
    }

    func setDepositEnabled(_ value: Bool) {
        preferences.setDepositEnabled(value: value)
    }

    func setTransferEnabled(_ value: Bool) {
        preferences.setTransferEnabled(value: value)
    }
}
