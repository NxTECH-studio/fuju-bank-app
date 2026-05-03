package studio.nxtech.fujubank.di

import com.russhwolf.settings.Settings
import org.koin.dsl.module
import studio.nxtech.fujubank.BuildKonfig
import studio.nxtech.fujubank.account.AccountProfileProvider
import studio.nxtech.fujubank.account.DummyAccountProfileProvider
import studio.nxtech.fujubank.account.NotificationSettingsPreferences

/**
 * アカウントタブ配下（ハブ画面 / 通知設定画面）が依存する shared コンポーネントを提供する。
 *
 * - [NotificationSettingsPreferences]: `Settings` は signupModule で既に登録済みのものを共有する。
 * - [AccountProfileProvider]: 現状は [DummyAccountProfileProvider] を登録。
 *   `BuildKonfig.USE_DUMMY_PROFILE` が将来 false になった場合は Remote 実装へ差し替える。
 */
val accountModule = module {
    // signupModule で `single<Settings> { Settings() }` 済みなので get() で同一インスタンスを取る
    single { NotificationSettingsPreferences(get<Settings>()) }
    single<AccountProfileProvider> {
        // 現状はダミー固定。実 API 連携時に Remote 実装を追加して USE_DUMMY_PROFILE=false の
        // 分岐で返す想定。release ビルド (USE_DUMMY_PROFILE=false) で Dummy が黙って混入しない
        // よう、未実装時は明示的に失敗させる。
        if (BuildKonfig.USE_DUMMY_PROFILE) {
            DummyAccountProfileProvider()
        } else {
            error("RemoteAccountProfileProvider is not implemented yet")
        }
    }
}
