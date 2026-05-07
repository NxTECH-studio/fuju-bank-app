package studio.nxtech.fujubank.di

import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import org.koin.dsl.module
import studio.nxtech.fujubank.BuildKonfig
import studio.nxtech.fujubank.account.AccountProfileProvider
import studio.nxtech.fujubank.account.DummyAccountProfileProvider
import studio.nxtech.fujubank.account.NotificationSettingsPreferences
import studio.nxtech.fujubank.account.PrivacyPreferences
import studio.nxtech.fujubank.account.RemoteAccountProfileProvider

/**
 * アカウントタブ配下（ハブ画面 / 通知設定画面）が依存する shared コンポーネントを提供する。
 *
 * - [NotificationSettingsPreferences]: `Settings` は signupModule で既に登録済みのものを共有する。
 * - [AccountProfileProvider]:
 *   - debug (`USE_DUMMY_PROFILE=true`): [DummyAccountProfileProvider]（オフライン UI 確認用）
 *   - release (`USE_DUMMY_PROFILE=false`): [RemoteAccountProfileProvider]（実 API 取得）
 *   `RemoteAccountProfileProvider` は生成時に 1 回 fetch する設計のため、
 *   プロセス共有 [CoroutineScope]（[APP_SCOPE_QUALIFIER]、`realtimeModule` で登録）を
 *   注入する。
 */
val accountModule = module {
    // signupModule で `single<Settings> { Settings() }` 済みなので get() で同一インスタンスを取る
    single { NotificationSettingsPreferences(get<Settings>()) }
    single { PrivacyPreferences(get<Settings>()) }
    single<AccountProfileProvider> {
        if (BuildKonfig.USE_DUMMY_PROFILE) {
            DummyAccountProfileProvider()
        } else {
            RemoteAccountProfileProvider(
                profileRepository = get(),
                scope = get<CoroutineScope>(APP_SCOPE_QUALIFIER),
            )
        }
    }
}
