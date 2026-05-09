package studio.nxtech.fujubank.account

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
        // NOTE: `getMyProfile()` は内部で `runCatchingNetwork` を通り例外を `NetworkResult` に
        //       畳み込むため、ここで `runCatching` を再度被せると `CancellationException` まで
        //       握り潰し構造化並行性を壊す（kotlin.runCatching の既知 footgun）。
        //       sealed の網羅 when で扱い、Failure / NetworkFailure は空のまま据え置く。
        //       ログ収集 SDK 導入は別タスク。
        // NOTE: `init { scope.launch }` でコンストラクタから `this` がワーカースレッドに
        //       見える形になるが、現状参照する `_profile` は val + 初期化済みなので安全。
        //       副作用フィールドを後から追加する場合はここから参照しないこと
        //       （必要なら `start()` 明示パターンに切り替える）。
        scope.launch {
            when (val result = profileRepository.getMyProfile()) {
                is NetworkResult.Success -> _profile.value = result.value.toAccountProfile()
                is NetworkResult.Failure,
                is NetworkResult.NetworkFailure -> Unit
            }
        }
    }

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
     * 次回ログイン時には新しい `RemoteAccountProfileProvider` が Koin から生成され
     * 改めて `getMyProfile()` を叩く設計だが、Koin singleton なので本実装が再利用される
     * ケースでも前ユーザーの displayName / email が残らないよう明示的に空に戻す。
     */
    override fun reset() {
        _profile.value = EMPTY_PROFILE
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
