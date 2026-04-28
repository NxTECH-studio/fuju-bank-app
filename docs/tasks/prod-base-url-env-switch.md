# prod-base-url-env-switch: 本番/開発で baseUrl と cableUrl を切り替える

## 概要

現状 `AndroidPlatformModule.kt` / `IosPlatformModule.kt` に書かれている `BANK_API_BASE_URL` と、`FujuBankApp.kt` / iOS 側 Koin ブートストラップに書かれている `CABLE_URL` がローカル疎通用の暫定ハードコード（`http://10.0.2.2:3000` 等）のまま。本番（`https://api.fujupay.app`）にクライアントを向けるため、ビルドバリアント経由で環境差分を注入できるようにする。

## 背景・目的

- 既存コードには `// TODO: remove after smoke test — baseUrl の設定経路は別タスクで設計する。` と明記されている。本タスクがその設計/実装。
- Android: debug / release で切り替えるのが自然（Gradle の `buildConfigField` または [BuildKonfig](https://github.com/yshrsmz/BuildKonfig)）。
- iOS: xcconfig / Info.plist もしくは BuildKonfig（KMP 共通化するなら後者）。
- 実装として最もコスト低く KMP 共通化できるのは **BuildKonfig** プラグインの導入。Gradle 側で Debug/Release を分け、Kotlin 側からは `BuildKonfig.BANK_API_BASE_URL` / `BuildKonfig.CABLE_URL` として参照する。

## 影響範囲

- モジュール: shared, composeApp
- プラットフォーム: Android / iOS 両方
- 破壊的変更: なし（開発時の URL は既存値を維持）
- 追加依存: `com.codingfeline.buildkonfig` (Gradle プラグイン)

## 実装ステップ

1. **BuildKonfig プラグイン導入**
   - `gradle/libs.versions.toml` に `buildKonfig = "0.17.1"`（最新安定版）を追加
   - `[plugins]` に `buildKonfig = { id = "com.codingfeline.buildkonfig", version.ref = "buildKonfig" }`
   - ルート `build.gradle.kts` または `shared/build.gradle.kts` の `plugins {}` に `alias(libs.plugins.buildKonfig)`

2. **shared/build.gradle.kts に buildkonfig ブロックを追加**
   ```kotlin
   buildkonfig {
       packageName = "studio.nxtech.fujubank"
       defaultConfigs {
           buildConfigField(STRING, "BANK_API_BASE_URL", "http://10.0.2.2:3000")
           buildConfigField(STRING, "CABLE_URL", "ws://10.0.2.2:3000/cable")
       }
       defaultConfigs("release") {
           buildConfigField(STRING, "BANK_API_BASE_URL", "https://api.fujupay.app")
           buildConfigField(STRING, "CABLE_URL", "wss://api.fujupay.app/cable")
       }
   }
   ```
   - iOS の baseUrl は `localhost:3000` だが、Android エミュレータ用の `10.0.2.2` と差異がある。defaultConfigs は共通、iOS 固有の上書きが必要なら `targetConfigs("iosArm64") { ... }` などで分離。
   - ただし iOS シミュレータも `10.0.2.2` 以外は `localhost` で疎通するので、**開発時はプラットフォーム別に `defaultConfigs("ios") { ... }` を生やすのが楽**。

3. **AndroidPlatformModule.kt / IosPlatformModule.kt の修正**
   - `private const val BANK_API_BASE_URL = "..."` を削除
   - `baseUrl = BANK_API_BASE_URL` → `baseUrl = BuildKonfig.BANK_API_BASE_URL`
   - TODO コメントを削除

4. **composeApp/src/androidMain/.../FujuBankApp.kt と iOS 側 KoinIos.kt の修正**
   - `CABLE_URL` ハードコードを削除し `BuildKonfig.CABLE_URL` 参照に差し替え
   - `// TODO: remove after smoke test` コメントを削除

5. **検証**
   - Debug ビルドで `BuildKonfig.BANK_API_BASE_URL == "http://10.0.2.2:3000"` になること（ログまたは一時的な `Log.d`）
   - Release ビルドで `BuildKonfig.BANK_API_BASE_URL == "https://api.fujupay.app"` になること
   - iOS 側も同等に切り替わっていること

## 検証チェックリスト

- [ ] `./gradlew :shared:generateBuildKonfig` が成功
- [ ] `./gradlew :composeApp:assembleDebug` 成功、Android エミュレータから `/up` が叩ける
- [ ] `./gradlew :composeApp:assembleRelease` 成功（署名なしでビルド通ればOK）
- [ ] iOS: `./gradlew :shared:assembleXcFramework` 成功
- [ ] Release ビルドを実機/TestFlight に配って `/up` が 200 を返すこと

## 依存

- backend 側で `https://api.fujupay.app` が稼働済み（Step 2 完了後が望ましいが、`/up` は既に 200 を返すので Step 1 単体で検証可能）

## 技術的な補足

- BuildKonfig を避けたい場合の代替案:
  - Android: `android { buildTypes { debug {..} release {..} } buildFeatures { buildConfig = true }` で `buildConfigField` を生やす。iOS 側は `Constants.ios.kt` / `Constants.android.kt` で expect/actual + Xcode Configuration から流し込む。
  - ただし iOS 側の値を Xcode の Build Configuration で管理するのは KMP ビルドフローと噛み合わせにくいので、初手は BuildKonfig 推奨。
- `AUTHCORE_BASE_URL` も将来同じ BuildKonfig で管理するが、本タスクでは触らない（AuthCore の扱い自体が未決 → backend 側の `auth-strategy-decision.md` 参照）。
