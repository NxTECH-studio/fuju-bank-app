package studio.nxtech.fujubank.account

/**
 * [AccountProfile] の取得口。Figma `697:8394` の文字列をダミーとして返す。
 *
 * 抽象化の意図:
 * - UI 側からは `AccountProfileProvider.current()` だけを呼ぶ
 * - 本タスクではダミー実装（[DummyAccountProfileProvider]）を Koin に登録
 * - 実 API 接続時には別実装（例: Repository を注入する `RemoteAccountProfileProvider`）
 *   を作って Koin の登録を差し替えるだけで UI 側は変えない
 */
interface AccountProfileProvider {
    fun current(): AccountProfile
}

/**
 * Figma `697:8394` 由来の固定文字列を返すダミー実装。
 * 本タスクの間はこれを Koin に登録する（[studio.nxtech.fujubank.di.accountModule]）。
 */
class DummyAccountProfileProvider : AccountProfileProvider {
    override fun current(): AccountProfile = DUMMY

    private companion object {
        // Figma `697:8394` 上の表記をそのまま保持。実 API 確定時に削除する。
        val DUMMY = AccountProfile(
            displayName = "山田 花子",
            email = "hanako@example.com",
            accountId = "1293031294904",
        )
    }
}
