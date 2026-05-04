import Foundation
import Shared

/// プライバシー設定画面の ViewModel。
///
/// shared 側 `PrivacyPreferences.analyticsOptInEnabled` の `StateFlow<Boolean>` を Swift 側から
/// 観測できるよう、`AccountIos.kt` で公開された `observeAnalyticsOptInEnabled`
/// （`observeDepositEnabled` と同一パターン）を購読し、`@Published` に転写する。
///
/// SwiftUI の `Toggle` は `Binding<Bool>` を要求するため、画面側では
/// `Binding(get: { vm.analyticsOptInEnabled }, set: { vm.setAnalyticsOptInEnabled($0) })` の形で
/// 書き戻し API を経由する。`@Published` の直接バインドは shared への永続化を伴わないので避ける
/// （`ObservableNotificationSettingsViewModel` と同じ方針）。
@MainActor
final class ObservablePrivacySettingsViewModel: ObservableObject {
    @Published private(set) var analyticsOptInEnabled: Bool

    private let preferences: PrivacyPreferences
    private var optInToken: FlowToken?

    init() {
        let prefs = KoinIosKt.privacyPreferences()
        self.preferences = prefs
        // observeAnalyticsOptInEnabled は subscribe 直後に現在値を 1 回 emit するので、初期値は
        // 仮で false を入れておき、購読開始直後のコールバックで正しい値に上書きされる。
        self.analyticsOptInEnabled = false

        optInToken = AccountIosKt.observeAnalyticsOptInEnabled(preferences: prefs) { [weak self] value in
            // Kotlin の Boolean は Swift から KotlinBoolean として渡ってくる。
            let on = value.boolValue
            Task { @MainActor in
                self?.analyticsOptInEnabled = on
            }
        }
    }

    deinit {
        optInToken?.close()
    }

    func setAnalyticsOptInEnabled(_ value: Bool) {
        preferences.setAnalyticsOptInEnabled(value: value)
    }
}
