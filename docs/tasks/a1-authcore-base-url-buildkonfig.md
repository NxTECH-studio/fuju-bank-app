# A1: AUTHCORE_BASE_URL を BuildKonfig 化

## メタ情報

- **Phase**: 0
- **並行起動**: ✅ 単独着手可能（backend 待ち不要）
- **依存**: なし
- **同期点**: backend B4 で `https://authcore.fujupay.app` 確定後、release 値を更新する追従 PR を 1 本出す

## 概要

`shared/.../data/remote/NetworkConstants.kt:4` に `https://authcore.fuju-bank.local` がハードコード。`prod-base-url-env-switch.md` で意図的にスキップされたが、A2a (shared 層改修) と A2b (ログイン UI) が実 URL に向ける前にここを BuildKonfig 化する。

## 背景・目的

- BuildKonfig は既に `BANK_API_BASE_URL` / `CABLE_URL` で導入済み (`shared/build.gradle.kts`)。同じパターンで `AUTHCORE_BASE_URL` を追加するだけ。
- release 値は backend B4 確定まで暫定 (`https://authcore.fujupay.app`) で OK。

## 影響範囲

- ファイル:
  - `shared/build.gradle.kts`（buildkonfig ブロック）
  - `shared/src/commonMain/.../data/remote/NetworkConstants.kt`（削除）
  - `shared/src/commonMain/.../di/AuthModule.kt`（参照差し替え）
  - `shared/src/commonMain/.../di/BuildConfigFacade.kt`（`defaultAuthCoreBaseUrl()` 追加）
  - `shared/src/commonTest/.../di/SharedModuleTest.kt`（必要なら修正）

## 実装ステップ

1. **`shared/build.gradle.kts` の buildkonfig ブロックに追加**:
   ```kotlin
   defaultConfigs {
     buildConfigField(STRING, "BANK_API_BASE_URL", "http://10.0.2.2:3000")
     buildConfigField(STRING, "CABLE_URL", "ws://10.0.2.2:3000/cable")
     buildConfigField(STRING, "AUTHCORE_BASE_URL", "http://10.0.2.2:8080")  // 追加
   }
   defaultConfigs("release") {
     buildConfigField(STRING, "BANK_API_BASE_URL", "https://api.fujupay.app")
     buildConfigField(STRING, "CABLE_URL", "wss://api.fujupay.app/cable")
     buildConfigField(STRING, "AUTHCORE_BASE_URL", "https://authcore.fujupay.app")  // 追加（暫定）
   }
   targetConfigs {
     create("iosArm64") {
       buildConfigField(STRING, "BANK_API_BASE_URL", "http://localhost:3000")
       buildConfigField(STRING, "CABLE_URL", "ws://localhost:3000/cable")
       buildConfigField(STRING, "AUTHCORE_BASE_URL", "http://localhost:8080")  // 追加
     }
     // iosSimulatorArm64 も同様
   }
   targetConfigs("release") {
     // 同様に release 値を追加
   }
   ```

2. **`BuildConfigFacade.kt` に追加**:
   ```kotlin
   fun defaultAuthCoreBaseUrl(): String = BuildKonfig.AUTHCORE_BASE_URL
   ```

3. **`NetworkConstants.kt` を削除** （他に定数が残るならファイルは残してこのフィールドのみ削除）。

4. **`AuthModule.kt` の参照差し替え**:
   ```kotlin
   single { AuthApi(get(), defaultAuthCoreBaseUrl()) }
   ```

5. **動作確認**:
   - `./gradlew :shared:generateBuildKonfig`
   - 生成された `BuildKonfig.kt` で AUTHCORE_BASE_URL が期待値か目視

## 検証チェックリスト

- [ ] `./gradlew :shared:generateBuildKonfig` 成功
- [ ] `./gradlew :shared:assembleDebug` 成功（Android）
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` 成功
- [ ] `./gradlew :composeApp:assembleRelease -Pbuildkonfig.flavor=release` 成功
- [ ] Debug ビルドで `defaultAuthCoreBaseUrl() == "http://10.0.2.2:8080"` (Android) / `"http://localhost:8080"` (iOS sim)
- [ ] Release ビルドで `defaultAuthCoreBaseUrl() == "https://authcore.fujupay.app"`

## 後続タスク

backend B4 で確定 URL が決まったら release 値を更新する 1 行 PR を出す。
