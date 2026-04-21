# T1-2: TokenStorage（expect / actual）

## 概要

アクセストークン / リフレッシュトークン / subject(ULID) をセキュアに保存する `TokenStorage` を expect/actual で提供する。Android は EncryptedSharedPreferences、iOS は Keychain Services を使用。

## 背景・目的

AuthCore から取得した JWT を、プラットフォーム推奨のセキュアストレージに保存する。`HttpClient` の Auth プラグイン（T1-1）から `getAccessToken()` で読めるようにする。

## 影響範囲

- モジュール: shared
- ソースセット: commonMain / androidMain / iosMain
- 破壊的変更: なし
- 追加依存: なし（T0-1 で `androidx.security:security-crypto` 追加済み）

## 実装ステップ

1. `shared/src/commonMain/kotlin/studio/nxtech/fujubank/auth/TokenStorage.kt`:
   - `interface TokenStorage { suspend fun getAccessToken(): String?; suspend fun getRefreshToken(): String?; suspend fun getSubject(): String?; suspend fun save(access: String, refresh: String, subject: String); suspend fun clear() }`
2. `shared/src/commonMain/kotlin/studio/nxtech/fujubank/auth/TokenStorageFactory.kt`:
   - `expect class TokenStorageFactory { fun create(): TokenStorage }`
3. `shared/src/androidMain/.../auth/TokenStorageFactory.android.kt`:
   - `actual class TokenStorageFactory(private val context: Context)`
   - 実装: `EncryptedSharedPreferences.create(context, "fuju_tokens", MasterKey.Builder(context).setKeyScheme(AES256_GCM).build(), AES256_SIV, AES256_GCM)` を利用。suspend 関数は `withContext(Dispatchers.IO)` で包む。
4. `shared/src/iosMain/.../auth/TokenStorageFactory.ios.kt`:
   - `actual class TokenStorageFactory`
   - 実装: `Security` framework の `SecItemAdd` / `SecItemCopyMatching` / `SecItemDelete` を `kSecClassGenericPassword` 用に薄くラップ。`service = "studio.nxtech.fujubank"`, `account = "access"/"refresh"/"subject"`。
5. `auth/` の marker ファイルは削除。

## 検証

- [ ] `./gradlew :shared:build`
- [ ] `./gradlew :shared:allTests`
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`

## 依存

- T0-1, T0-2

## 技術的な補足

- Android の `TokenStorageFactory` は Context を受け取る必要があるため、コンストラクタに `Context` を取る。Koin（T4-1 以降）で `androidContext()` から注入する。
- iOS 側は Keychain 直叩き。Cocoa 依存の import が多いので、`@OptIn(ExperimentalForeignApi::class)` が必要な可能性あり。
- エラー時は例外を投げずに `null` を返す設計（auth プラグインのリトライを単純化するため）。
