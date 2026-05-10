package studio.nxtech.fujubank.account

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.repository.ProfileRepository
import studio.nxtech.fujubank.domain.model.UserProfile

/**
 * 実 API（AuthCore + bank `/users/me`）からプロフィールを取得する [AccountProfileProvider]。
 *
 * release ビルド (`USE_DUMMY_PROFILE=false`) で Koin に登録され、`SessionResetCoordinator`
 * が `* → Authenticated` 遷移を観測したタイミングで [refresh] を呼ぶ。これにより:
 *
 * - 起動時: bootstrap で SessionStore が Authenticated に遷移 → refresh
 * - ログイン: provisionAndAuthenticate → setAuthenticated → refresh
 * - サインアップ: confirmRecoveryCodes → setAuthenticated → refresh
 * - ログアウト: setAuthenticated→Unauthenticated 遷移で [reset] により空キャッシュに戻る
 *
 * 設計メモ:
 * - 取得失敗時は **前回値を据え置く**（NetworkFailure / Failure ともに `_profile` を変更しない）。
 *   AccountHub 側で空文字を「-」プレースホルダに置換して表示する。
 * - 通常の AccountHub 画面入場では refresh しない（StateFlow が直前のキャッシュを返すだけ）。
 *   pull-to-refresh 等が要るタイミングで別タスクで拡張する。
 * - [updateProfile] は no-op に近い。AuthCore に email/displayName 更新 API が揃ったら
 *   通信を伴う実装に差し替える（MVP の方針上、編集 UI 自体も無効化されている）。
 */
class RemoteAccountProfileProvider(
    private val profileRepository: ProfileRepository,
) : AccountProfileProvider {

    private val _profile = MutableStateFlow(EMPTY_PROFILE)
    override val profile: StateFlow<AccountProfile> = _profile.asStateFlow()

    /**
     * MVP では実 API 側に更新エンドポイントが無いため、ローカルの [_profile] のみ更新する。
     * AccountHub の編集 UI は同 MVP 期間で無効化されているため、このメソッドが UI から
     * 呼ばれる経路は現在無い。AuthCore に更新 API が来たら本実装を書き換える。
     */
    override fun updateProfile(displayName: String, email: String) {
        _profile.update { it.copy(displayName = displayName, email = email) }
    }

    /**
     * ログアウト時に呼ばれ、in-memory のプロフィールキャッシュを空に戻す。
     *
     * `accountModule` 上 `single<AccountProfileProvider>` で登録されており、本クラスは
     * プロセス内で 1 度だけ生成される。再ログインでも同一インスタンスが再利用されるため、
     * ここで明示的に空に戻さないと前ユーザーの displayName / email が残る。
     */
    override fun reset() {
        _profile.value = EMPTY_PROFILE
    }

    /**
     * AuthCore + bank プロフィールを取得し直して [_profile] を更新する。
     *
     * 失敗時は `_profile` を変更しない（前回成功した値があれば維持される）。
     * `runCatchingNetwork` 経由なので [kotlinx.coroutines.CancellationException] は
     * Repository 内部で適切に再 throw される。
     */
    override suspend fun refresh() {
        when (val result = profileRepository.getMyProfile()) {
            is NetworkResult.Success -> _profile.value = result.value.toAccountProfile()
            is NetworkResult.Failure,
            is NetworkResult.NetworkFailure -> Unit
        }
    }
}

/** 取得失敗・取得前の空状態。AccountHub 側で「-」プレースホルダに置換される。 */
private val EMPTY_PROFILE = AccountProfile(
    displayName = "",
    email = "",
    accountId = "",
)

/**
 * [UserProfile] → [AccountProfile] の変換。
 *
 * `displayName` のフォールバック順:
 *   1. bank `/users/me` の `name`（将来サーバ追加予定）
 *   2. AuthCore の `email` の `@` 前
 *   3. AuthCore の `publicId`
 *   4. 空文字（UI 側で「-」表示）
 */
internal fun UserProfile.toAccountProfile(): AccountProfile = AccountProfile(
    displayName = name?.takeIf { it.isNotBlank() }
        ?: email?.substringBefore('@')?.takeIf { it.isNotBlank() }
        ?: publicId.takeIf { it.isNotBlank() }
        ?: "",
    email = email.orEmpty(),
    accountId = bankUserId,
)
