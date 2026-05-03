import Foundation
import Shared

/// アカウントハブ画面の ViewModel。
///
/// `AccountProfileProvider.current()` を 1 度呼び、結果を `@Published` に保持するだけのシンプルな
/// 構造（Android `AccountHubViewModel` と同等）。本タスクではダミー固定の Provider を使うため
/// プロセス内で値が変化することはない。実 API 連携時には Provider 実装側を差し替える。
@MainActor
final class ObservableAccountHubViewModel: ObservableObject {
    @Published private(set) var profile: AccountProfile

    init() {
        self.profile = KoinIosKt.accountProfileProvider().current()
    }
}
