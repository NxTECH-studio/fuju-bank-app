import Foundation
import UIKit
import UserNotifications
import os.log

/// OS のプッシュ通知許可状態を表す共通モデル。
///
/// client-bank-14 / client-bank-15 の共通仕様 A に従い、Android / iOS で 4 状態を
/// 区別する。iOS では `UNAuthorizationStatus` を以下にマッピングする:
///
/// - `.authorized` / `.provisional` / `.ephemeral` → `.granted`
/// - `.denied` → `.denied`
/// - `.notDetermined` → `.notDetermined`
/// - その他（将来追加された未知ケース） → `.denied`
///
/// `.systemSettingsOnly` は Android API 32 以下専用ケースだが、共通モデルの
/// 対称性を保つため iOS 側にも残す（iOS では発生しない）。
enum NotificationPermissionState {
    case notDetermined
    case granted
    case denied
    case systemSettingsOnly

    /// マスタートグルの ON/OFF 判定（共通仕様 D）。
    var isGranted: Bool {
        switch self {
        case .granted, .systemSettingsOnly:
            return true
        case .notDetermined, .denied:
            return false
        }
    }
}

private let permissionLog = OSLog(subsystem: "studio.nxtech.fujubank", category: "NotificationPermission")

/// 現在の OS 通知許可状態を取得する。
///
/// `UNUserNotificationCenter.current().notificationSettings()` の `authorizationStatus`
/// を共通モデルに変換する。共通仕様 E に従い、初回表示時 / `.scenePhase` 復帰時
/// から呼び出される想定。
func currentNotificationPermissionState() async -> NotificationPermissionState {
    let settings = await UNUserNotificationCenter.current().notificationSettings()
    return mapAuthorizationStatus(settings.authorizationStatus)
}

/// プッシュ通知許可ダイアログをユーザーに表示し、結果を共通モデルで返す。
///
/// `.notDetermined` のときのみ実際にダイアログが出る。すでに `.denied` の場合は
/// iOS 仕様上ダイアログは出ず即時偽が返るため、UI 側で事前に分岐して
/// `openAppNotificationSettings()` に振る運用にする（共通仕様 C）。
///
/// 例外発生時は `os_log` でローカルログのみ残し（共通仕様 F）、現状の OS 状態を
/// 再取得して返す。
func requestNotificationPermission() async -> NotificationPermissionState {
    do {
        _ = try await UNUserNotificationCenter.current().requestAuthorization(
            options: [.alert, .badge, .sound]
        )
    } catch {
        os_log(
            "Failed to request notification authorization: %{public}@",
            log: permissionLog,
            type: .error,
            String(describing: error)
        )
    }
    return await currentNotificationPermissionState()
}

/// 本アプリの OS 通知設定画面を開く（共通仕様 C）。
///
/// `Denied` 時のフォールバック導線として使うほか、iOS では一度許可した状態を
/// アプリ内から revoke できないため `Granted` 状態のタップ時にも同じ導線へ流す。
func openAppNotificationSettings() {
    guard let url = URL(string: UIApplication.openSettingsURLString) else {
        os_log(
            "openSettingsURLString is invalid",
            log: permissionLog,
            type: .error
        )
        return
    }
    Task { @MainActor in
        guard UIApplication.shared.canOpenURL(url) else {
            os_log(
                "Cannot open settings URL",
                log: permissionLog,
                type: .error
            )
            return
        }
        UIApplication.shared.open(url, options: [:]) { success in
            if !success {
                os_log(
                    "Failed to open app notification settings",
                    log: permissionLog,
                    type: .error
                )
            }
        }
    }
}

private func mapAuthorizationStatus(_ status: UNAuthorizationStatus) -> NotificationPermissionState {
    switch status {
    case .authorized, .provisional, .ephemeral:
        return .granted
    case .denied:
        return .denied
    case .notDetermined:
        return .notDetermined
    @unknown default:
        // 将来 OS 追加された未知の状態は安全側に倒し、`.denied` 扱いで
        // OS 設定アプリ導線に振る。
        return .denied
    }
}
