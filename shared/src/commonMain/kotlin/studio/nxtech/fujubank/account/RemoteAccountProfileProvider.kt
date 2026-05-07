package studio.nxtech.fujubank.account

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.repository.ProfileRepository
import studio.nxtech.fujubank.domain.model.UserProfile

/**
 * 実 API（AuthCore + bank `/users/me`）からプロフィールを取得する [AccountProfileProvider]。
 *
 * release ビルド (`USE_DUMMY_PROFILE=false`) で Koin に登録され、生成時に 1 回だけ
 * [ProfileRepository.getMyProfile] を呼んで `_profile` を初期値（空）から実値に置換する。
 *
 * 設計メモ:
 * - 取得失敗時は空の [AccountProfile] のまま据え置く。例外は投げない（D-1）。
 *   → AccountHub 側で空文字を「-」プレースホルダに置換して表示する。
 * - タブ切り替えごとに refetch しない（D-1）。アプリ再起動 / ログイン直後にのみ最新化。
 *   pull-to-refresh などのリアクティブ購読が要るタイミングで別タスクで拡張する。
 * - [updateProfile] は no-op に近い。AuthCore 側に email/displayName 更新 API が
 *   揃ったら通信を伴う実装に差し替える。MVP の方針上、編集 UI 自体も無効化されている。
 */
class RemoteAccountProfileProvider(
    private val profileRepository: ProfileRepository,
    scope: CoroutineScope,
) : AccountProfileProvider {

    private val _profile = MutableStateFlow(EMPTY_PROFILE)
    override val profile: StateFlow<AccountProfile> = _profile.asStateFlow()

    init {
        scope.launch {
            runCatching { profileRepository.getMyProfile() }
                .onSuccess { result ->
                    if (result is NetworkResult.Success) {
                        _profile.value = result.value.toAccountProfile()
                    }
                    // Failure / NetworkFailure は空のまま据え置き。
                    // ログ収集 SDK 導入は別タスク。
                }
        }
    }

    /**
     * MVP では実 API 側に更新エンドポイントが無いため、ローカルの [_profile] のみ更新する。
     * AccountHub の編集 UI は同 MVP 期間で無効化されているため、このメソッドが UI から
     * 呼ばれる経路は現在無い。AuthCore に更新 API が来たら本実装を書き換える。
     */
    override fun updateProfile(displayName: String, email: String) {
        _profile.value = _profile.value.copy(
            displayName = displayName,
            email = email,
        )
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
