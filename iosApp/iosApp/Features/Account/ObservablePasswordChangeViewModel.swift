import Foundation

/// パスワード変更画面の ViewModel — Android `PasswordChangeViewModel` と 1:1。
///
/// バックエンド API は未提供のため、[submit] は疑似遅延ののち成功扱いにする。
/// 実 API が提供された後は `submit` 内のロジックを `commonMain` の UseCase 呼び出しに
/// 差し替える想定（Android 側と同じ方針）。
///
/// 入力値はセキュリティ観点で `@SceneStorage` 等の永続化を避け、
/// `@Published` 上のメモリ内のみで保持する。プロセス再生成時には消える方針。
@MainActor
final class ObservablePasswordChangeViewModel: ObservableObject {
    @Published var current: String = ""
    @Published var newPassword: String = ""
    @Published var confirm: String = ""
    @Published private(set) var isSubmitting: Bool = false
    @Published private(set) var isSubmitted: Bool = false

    /// 保存ボタンを押せる条件（Android `PasswordChangeUiState.canSubmit` と一致）:
    /// - 3 欄すべて非空
    /// - 新パスワードが現在のパスワードと異なる
    /// - 新パスワードと確認用が一致
    /// - 送信中でない
    var canSubmit: Bool {
        !isSubmitting &&
            !current.isEmpty &&
            !newPassword.isEmpty &&
            !confirm.isEmpty &&
            newPassword != current &&
            newPassword == confirm
    }

    func submit() {
        guard canSubmit else { return }
        isSubmitting = true
        Task { @MainActor in
            // バックエンド連携の疑似遅延。実 API 接続時はここを差し替える。
            try? await Task.sleep(nanoseconds: 800_000_000)
            isSubmitting = false
            isSubmitted = true
        }
    }
}
