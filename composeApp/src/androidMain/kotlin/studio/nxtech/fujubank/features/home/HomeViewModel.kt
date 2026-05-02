package studio.nxtech.fujubank.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import studio.nxtech.fujubank.data.remote.NetworkResult
import studio.nxtech.fujubank.data.repository.ProfileRepository

/**
 * ホーム画面の状態とアクションを束ねる ViewModel。
 *
 * - 起動時に `ProfileRepository.getMyProfile()` を 1 回だけ叩いて `UserProfile` を取得。
 * - 残高は初期マスク。`toggleReveal()` で表示／非表示を切り替える。
 * - `refresh()` で再取得（pull-to-refresh 用）。
 *
 * TODO(A6): realtimeRepository.events を collect して残高ライブ更新する slot
 */
class HomeViewModel(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    // 進行中の fetch Job。新しい load() 開始前にキャンセルし、refresh 連打で
    // 古い結果が後勝ちで state を上書きしないようにする。
    private var loadJob: Job? = null

    init {
        load(initial = true)
    }

    fun toggleReveal() {
        _state.update { current ->
            if (current is HomeUiState.Loaded) current.copy(revealed = !current.revealed) else current
        }
    }

    fun refresh() {
        load(initial = false)
    }

    private fun load(initial: Boolean) {
        loadJob?.cancel()
        if (!initial) {
            _state.update { current ->
                when (current) {
                    is HomeUiState.Loaded -> current.copy(refreshing = true)
                    else -> current
                }
            }
        }
        loadJob = viewModelScope.launch {
            when (val result = profileRepository.getMyProfile()) {
                is NetworkResult.Success -> _state.update { current ->
                    when (current) {
                        is HomeUiState.Loaded -> current.copy(
                            profile = result.value,
                            refreshing = false,
                        )
                        else -> HomeUiState.Loaded(profile = result.value)
                    }
                }
                is NetworkResult.Failure -> _state.update {
                    HomeUiState.Error(message = "プロフィールを取得できませんでした")
                }
                is NetworkResult.NetworkFailure -> _state.update {
                    HomeUiState.Error(message = "通信エラーが発生しました")
                }
            }
        }
    }
}
