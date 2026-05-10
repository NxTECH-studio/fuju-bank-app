package studio.nxtech.fujubank.account

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [AccountProfile] の取得口。Figma `697:8394` の文字列をダミーとして返す。
 *
 * 抽象化の意図:
 * - UI 側からは [profile] の `StateFlow` を購読し、[updateProfile] で書き戻す
 * - 本タスクではダミー実装（[DummyAccountProfileProvider]）を Koin に登録
 * - 実 API 接続時には別実装（例: Repository を注入する `RemoteAccountProfileProvider`）
 *   を作って Koin の登録を差し替えるだけで UI 側は変えない
 */
interface AccountProfileProvider {
    val profile: StateFlow<AccountProfile>

    fun updateProfile(displayName: String, email: String)

    /**
     * ログアウト（`SessionState.Authenticated → Unauthenticated` 遷移）時に
     * [studio.nxtech.fujubank.session.SessionResetCoordinator] から呼び出され、
     * プロセス内に保持しているプロフィールキャッシュを破棄する。
     *
     * 実装は「次に [profile] を購読した側が前ユーザーの値を見ない」状態にすることが契約。
     * 詳細は実装ごと（dummy は初期ダミー値、remote は空プロフィール）に異なる。
     */
    fun reset()

    /**
     * `Authenticated` 遷移時に [studio.nxtech.fujubank.session.SessionResetCoordinator] から
     * 呼ばれ、プロフィール（AuthCore + bank）を再取得して in-memory キャッシュを更新する。
     *
     * 取得失敗時はキャッシュを変更しない（前回値を維持）。サインアップ完了直後・ログイン直後・
     * アプリ再起動時の bootstrap 復元など、Authenticated に遷移するすべての経路で 1 回だけ
     * 呼ばれる。AccountHub 画面入場のたびに refetch せずに済むよう、結果は [profile] の
     * StateFlow に保持されたまま使い回される（キャッシュ）。
     *
     * suspend にしているのは、Coordinator 側で逐次直列に呼べるようにするため。
     */
    suspend fun refresh()
}

/**
 * Figma `697:8394` 由来の固定文字列を初期値として保持する in-memory 実装。
 * 本タスクの間はこれを Koin に登録する（[studio.nxtech.fujubank.di.accountModule]）。
 *
 * [updateProfile] で更新された値はプロセス内に保持され、UI からの編集を即時反映する。
 * プロセス終了時に揮発する（実 API 連携時には Provider 側で永続化する）。
 */
class DummyAccountProfileProvider : AccountProfileProvider {
    private val _profile = MutableStateFlow(INITIAL_PROFILE)
    override val profile: StateFlow<AccountProfile> = _profile.asStateFlow()

    override fun updateProfile(displayName: String, email: String) {
        _profile.value = _profile.value.copy(
            displayName = displayName,
            email = email,
        )
    }

    override fun reset() {
        _profile.value = INITIAL_PROFILE
    }

    /** ダミー実装は実 API を叩かないため refresh は no-op。 */
    override suspend fun refresh() {
        // no-op
    }

    private companion object {
        // Figma `697:8394` 上の表記をそのまま保持。実 API 確定時に削除する。
        val INITIAL_PROFILE = AccountProfile(
            displayName = "山田 花子",
            email = "hanako@example.com",
            accountId = "1293031294904",
        )
    }
}
