package studio.nxtech.fujubank.di

import com.russhwolf.settings.Settings
import org.koin.dsl.module
import studio.nxtech.fujubank.signup.SignupCompletionSignal
import studio.nxtech.fujubank.signup.SignupWelcomePreferences

/**
 * Welcome 画面の表示制御に使う永続フラグ／ワンショットシグナルを提供する。
 *
 * `Settings()` の no-arg コンストラクタは Android では `SharedPreferences`、
 * iOS では `NSUserDefaults` をそれぞれデフォルト名で解決する。
 */
val signupModule = module {
    single<Settings> { Settings() }
    single { SignupWelcomePreferences(get()) }
    single { SignupCompletionSignal() }
}
