package studio.nxtech.fujubank.account

import kotlin.concurrent.Volatile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.repository.ProfileRepository
import studio.nxtech.fujubank.domain.model.UserProfile

/**
 * 実 API（AuthCore + bank `/users/me`）からプロフィールを取得する [AccountProfileProvider]。
 *
 * release ビルド (`USE_DUMMY_PROFILE=false`) で Koin に singleton 登録される。
 *
 * ## キャッシュ戦略
 *
 * - 初期値は EMPTY_PROFILE（空文字）。AccountHub 側で「-」プレースホルダ表示になる。
 * - AccountHub 表示時に [ensureLoaded] が呼ばれて 1 度だけ fetch する。成功すれば
 *   `_profile` が更新され、以降のタブ切替などでは API を叩かず StateFlow から即返す。
 * - ログアウト時に `SessionResetCoordinator` が [reset] を呼ぶと `_profile` を空に戻し、
 *   `loaded` フラグも下ろす。次回 AccountHub 表示で別ユーザの値を取りに行く。
 * - **fetch 失敗時はキャッシュを更新しない**（前回値を据え置く）。ただしログアウト直後の
 *   失敗は EMPTY のまま「-」表示になり、前ユーザの値は決して残らない。
 *
 * ## 並行性
 *
 * - 同じ AccountHub からの再表示や、別経路からの ensureLoaded 二重呼び出しに備え
 *   [loadMutex] で fetch 全体を直列化する。
 * - `loaded` の早期 return は mutex 取得前に判定し、定常状態のオーバーヘッドをほぼゼロに保つ。
 */
class RemoteAccountProfileProvider(
    private val profileRepository: ProfileRepository,
) : AccountProfileProvider {

    private val _profile = MutableStateFlow(EMPTY_PROFILE)
    override val profile: StateFlow<AccountProfile> = _profile.asStateFlow()

    private val loadMutex = Mutex()
    @Volatile
    private var loaded: Boolean = false

    /**
     * MVP では実 API 側に更新エンドポイントが無いため、ローカルの [_profile] のみ更新する。
     * AccountHub の編集 UI は同 MVP 期間で無効化されているため、このメソッドが UI から
     * 呼ばれる経路は現在無い。AuthCore に更新 API が来たら本実装を書き換える。
     */
    override fun updateProfile(publicId: String, email: String) {
        _profile.update { it.copy(publicId = publicId, email = email) }
    }

    /**
     * ログアウト時に呼ばれ、in-memory のキャッシュを空に戻し、`loaded` フラグも下ろす。
     *
     * `accountModule` 上 `single<AccountProfileProvider>` で登録されており、本クラスは
     * プロセス内で 1 度だけ生成される。再ログインでも同一インスタンスが再利用されるため、
     * ここで明示的に空 + loaded=false に戻さないと、別ユーザでログイン後の AccountHub に
     * 前ユーザの publicId / email が残る。
     */
    override fun reset() {
        _profile.value = EMPTY_PROFILE
        loaded = false
    }

    /**
     * AccountHub 初回表示時に呼ぶ lazy load。冪等で、既にロード済みなら即 return。
     *
     * 失敗時は `loaded` フラグが立たず、次回呼び出しで再試行できる。
     */
    override suspend fun ensureLoaded() {
        if (loaded) return
        loadMutex.withLock {
            if (loaded) return
            when (val result = profileRepository.getMyProfile()) {
                is NetworkResult.Success -> {
                    _profile.value = result.value.toAccountProfile()
                    loaded = true
                }
                is NetworkResult.Failure,
                is NetworkResult.NetworkFailure -> Unit
                // 失敗時は loaded のまま据え置き。次回 ensureLoaded 呼び出しで再試行。
            }
        }
    }
}

/** 取得失敗・取得前の空状態。AccountHub 側で「-」プレースホルダに置換される。 */
private val EMPTY_PROFILE = AccountProfile(
    publicId = "",
    email = "",
    accountId = "",
)

/**
 * [UserProfile] → [AccountProfile] の変換。
 *
 * AuthCore の `publicId` を `AccountProfile.publicId` にそのままマップする。
 * AuthCore 側で必ず存在する想定だが、未取得・取得失敗時は空文字（UI 側で「-」表示）。
 */
internal fun UserProfile.toAccountProfile(): AccountProfile = AccountProfile(
    publicId = publicId,
    email = email.orEmpty(),
    accountId = bankUserId,
)
