package studio.nxtech.fujubank.auth

import io.ktor.client.plugins.cookies.CookiesStorage

/**
 * AuthCore の refresh_token cookie をアプリ再起動後も保持するための
 * 永続化された [CookiesStorage] を生成する factory。
 *
 * - Android: EncryptedSharedPreferences ベース。コンストラクタで `Context` を要求。
 * - iOS: Keychain ベース。コンストラクタは引数なし。
 *
 * iOS で `URLSession` の cookie storage を使うと OS グローバルな cookie jar と
 * 混ざってしまうため、Ktor 独自の [CookiesStorage] 実装で隔離する。
 *
 * `TokenStorageFactory` と並ぶ platform 抽象化。Koin の platformModule で
 * single 登録してから shared 側で `createHttpClient` に渡す。
 */
expect class PersistentCookiesStorageFactory {
    fun create(): CookiesStorage
}
