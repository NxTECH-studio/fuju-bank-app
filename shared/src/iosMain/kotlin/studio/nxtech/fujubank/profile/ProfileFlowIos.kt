package studio.nxtech.fujubank.profile

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.repository.ProfileRepository
import studio.nxtech.fujubank.domain.model.UserProfile

/**
 * Swift から呼び出すホームプロフィール取得の結果型。
 *
 * - [Loaded]: 取得成功。
 * - [Failure]: API エラー（Bearer 401 など）。`message` は表示用日本語。
 * - [NetworkFailure]: 通信失敗。
 */
sealed class ProfileLoadOutcome {
    data class Loaded(val profile: UserProfile) : ProfileLoadOutcome()
    data class Failure(val message: String) : ProfileLoadOutcome()
    data class NetworkFailure(val message: String) : ProfileLoadOutcome()
}

/**
 * Swift 側 HomeViewModel から `getMyProfile { outcome in ... }` 形で呼ぶためのファサード。
 *
 * suspend 関数を直接 Swift に晒すと結果コールバックが複雑になるため、
 * AuthFlowIos と同じパターンで `onResult` クロージャに包む。
 *
 * 内部スコープは独立して持ち、Repository のキャンセルを Swift から
 * 直接トリガーしない（Home はマウント中ずっと残るので問題ない）。
 */
private val profileScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

fun fetchMyProfile(
    profileRepository: ProfileRepository,
    onResult: (ProfileLoadOutcome) -> Unit,
) {
    profileScope.launch {
        val outcome = when (val result = profileRepository.getMyProfile()) {
            is NetworkResult.Success -> ProfileLoadOutcome.Loaded(result.value)
            is NetworkResult.Failure -> ProfileLoadOutcome.Failure(
                message = "プロフィールを取得できませんでした",
            )
            is NetworkResult.NetworkFailure -> ProfileLoadOutcome.NetworkFailure(
                message = "通信エラーが発生しました",
            )
        }
        onResult(outcome)
    }
}
