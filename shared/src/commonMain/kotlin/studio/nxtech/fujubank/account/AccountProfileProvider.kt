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
     * remote 実装ではキャッシュを EMPTY に戻し、`ensureLoaded()` の済みフラグも下ろす
     * （次回 AccountHub 表示で新ユーザの値を再 fetch する）。
     */
    fun reset()

    /**
     * AccountHub 画面の初回表示時に呼ぶ「lazy load」エンドポイント。
     *
     * 振る舞い:
     * - **冪等**: 既に成功裏にロード済みなら即 return（API は叩かない）。
     * - **失敗時は再試行可**: fetch が失敗した場合は loaded フラグが立たず、
     *   次回呼び出しで再 fetch を試みる。
     * - **reset() で無効化**: ログアウト時に [reset] が loaded フラグも下ろすため、
     *   次回 ensureLoaded で別ユーザのデータを必ず取り直す。
     *
     * 同じプロセス内で複数の AccountHub 表示パスから同時に呼ばれても二重 fetch しないよう
     * 内部 mutex で直列化することが実装契約。
     */
    suspend fun ensureLoaded()
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

    /** ダミー実装は API を叩かないため ensureLoaded は no-op（常に initial が入っている）。 */
    override suspend fun ensureLoaded() {
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
