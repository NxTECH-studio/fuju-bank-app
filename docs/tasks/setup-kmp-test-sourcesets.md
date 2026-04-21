# KMP テストソースセット整備と CI への iOS テスト組み込み

## 概要

`shared` モジュールに `androidUnitTest` / `iosTest`（実体は `iosSimulatorArm64Test` / `iosArm64Test` の集約）ソースセットを追加し、各ソースセットに動作確認用のダミーテストを 1 件ずつ配置する。あわせて GitHub Actions CI（`build-ios-framework` ジョブ）に `./gradlew :shared:iosSimulatorArm64Test` を追加し、iOS 側テストも PR で自動実行されるようにする。

## 背景・目的

- 現状 `shared` モジュールのテストは `commonTest` のみ（`SharedCommonTest.kt`, `ApiErrorCodeTest.kt`）。プラットフォーム固有コード（`TokenStorageFactory.android.kt` / `TokenStorageFactory.ios.kt`、`HttpClientFactory.android.kt` / `HttpClientFactory.ios.kt`）に対するテストを書ける土台がない。
- 今後 `actual` 実装（Keychain / EncryptedSharedPreferences 等）のテストを書く際、ソースセットそのものがセットアップされていないと着手時点で設定コストが発生する。
- CI では現在 `./gradlew :shared:allTests` を Ubuntu ジョブで実行しているが、`iosSimulatorArm64Test` は macOS ランナーでしか実行できないため Ubuntu では skip 扱いになっている。iOS 側テストも macOS ジョブで実行する構成にしておきたい。
- 本タスクでは「テスト基盤の整備」のみをスコープとし、実コードに対するテストは別タスクで対応する。

## 影響範囲

- モジュール: `shared`
- ソースセット:
  - 既存: `commonTest`（変更なし）
  - 新規: `androidUnitTest`, `iosTest`（`iosArm64Test` / `iosSimulatorArm64Test` を集約）
- ファイル変更:
  - `shared/build.gradle.kts`（sourceSets にテスト設定を追加）
  - `.github/workflows/ci.yml`（`build-ios-framework` ジョブに iOS テストステップを追加）
  - `shared/src/androidUnitTest/kotlin/.../AndroidSharedTest.kt`（新規、ダミーテスト）
  - `shared/src/iosTest/kotlin/.../IosSharedTest.kt`（新規、ダミーテスト）
- 破壊的変更: なし
- 追加依存: `libs.versions.toml` に以下を追加
  - `kotlin-test-junit`（既に `[libraries]` に定義済み: `org.jetbrains.kotlin:kotlin-test-junit` → 参照するのみ）
  - 追加の新規依存は不要（`kotlin-test` が common / ios の両方で動作、JUnit 連携は `kotlin-test-junit` で完結）
- スコープ外:
  - 既存 `actual` 実装（`TokenStorage` 系 / `HttpClientFactory` 系）に対する実テスト
  - `composeApp` 側のテスト整備
  - Android Instrumented Test（`androidInstrumentedTest`）の整備

## 前提（調査結果）

- Kotlin: `2.3.20`（`gradle/libs.versions.toml`）
- KMP ターゲット: `androidTarget`, `iosArm64`, `iosSimulatorArm64`
- 既存 `commonTest` 依存: `libs.kotlin.test`
- `libs.versions.toml` に `kotlin-testJunit = { module = "org.jetbrains.kotlin:kotlin-test-junit", version.ref = "kotlin" }` は既に定義済み（`shared` からは未参照）
- 既存 CI 構成:
  - `build-and-check`（ubuntu-latest）→ `./gradlew :shared:allTests`
  - `build-ios-framework`（macos-latest）→ `linkDebugFrameworkIosSimulatorArm64` のみ
  - `build-ios-app`（macos-latest）→ xcodebuild

## 実装ステップ

1. **`shared/build.gradle.kts` にテストソースセットを追加**
   - `sourceSets { ... }` ブロック内に以下を追加:
     ```kotlin
     androidUnitTest.dependencies {
         implementation(libs.kotlin.test)
         implementation(libs.kotlin.testJunit)
     }
     iosTest.dependencies {
         implementation(libs.kotlin.test)
     }
     ```
   - `iosTest` は `iosArm64Test` / `iosSimulatorArm64Test` の親として自動生成される集約ソースセット（KMP default hierarchy template を利用）。明示的な階層定義が必要な場合のみ `applyDefaultHierarchyTemplate()` を追加する（現状 Kotlin 2.3.20 + `androidTarget` + `iosArm64()` / `iosSimulatorArm64()` の構成なら default hierarchy が有効なので追加不要。念のためビルド時に警告が出たら対応）。

2. **ダミーテストの配置**
   - `shared/src/androidUnitTest/kotlin/studio/nxtech/fujubank/AndroidSharedTest.kt`:
     ```kotlin
     package studio.nxtech.fujubank

     import kotlin.test.Test
     import kotlin.test.assertEquals

     class AndroidSharedTest {
         @Test
         fun example() {
             assertEquals(3, 1 + 2)
         }
     }
     ```
   - `shared/src/iosTest/kotlin/studio/nxtech/fujubank/IosSharedTest.kt`:
     ```kotlin
     package studio.nxtech.fujubank

     import kotlin.test.Test
     import kotlin.test.assertEquals

     class IosSharedTest {
         @Test
         fun example() {
             assertEquals(3, 1 + 2)
         }
     }
     ```
   - 目的はソースセットが正しく認識されてテストが実行されることの確認のみ。実ロジックのテストは別タスク。

3. **`.github/workflows/ci.yml` の `build-ios-framework` ジョブに iOS テストステップを追加**
   - 既存の `Link Debug framework (iosSimulatorArm64)` ステップの後に以下を追加:
     ```yaml
     - name: Run iOS shared tests (iosSimulatorArm64)
       run: ./gradlew :shared:iosSimulatorArm64Test
     ```
   - ジョブ名はそのまま（`Build iOS shared framework`）でも良いが、責務が広がるため `Build & test iOS shared framework` にリネームしても可（任意）。

4. **ローカル検証**
   - `./gradlew :shared:allTests` で commonTest + androidUnitTest + （macOS 環境なら）iosSimulatorArm64Test がすべて実行されることを確認。
   - macOS 以外の環境では `iosSimulatorArm64Test` は skip される（既存挙動のまま）。

## 検証

- [ ] `./gradlew :shared:allTests` が通る（commonTest + androidUnitTest が実行される）
- [ ] macOS 環境で `./gradlew :shared:iosSimulatorArm64Test` が通る
- [ ] CI の `build-and-check` ジョブが既存どおり green
- [ ] CI の `build-ios-framework` ジョブで `Run iOS shared tests` ステップが追加され green
- [ ] 既存 `commonTest`（`SharedCommonTest`, `ApiErrorCodeTest`）の実行が壊れていない

## 技術的な補足

- **KMP default hierarchy**: Kotlin 1.9.20 以降、`androidTarget()` + `iosArm64()` + `iosSimulatorArm64()` を宣言すると `iosMain` / `iosTest` の集約ソースセットが自動生成される。`shared/build.gradle.kts` 側で `iosMain.dependencies { ... }` を参照できている時点でこの機構は有効。
- **`androidUnitTest` vs `androidTest`**: KMP では Android の unit test は `androidUnitTest`（JVM 上実行）、instrumented test は `androidInstrumentedTest`（端末 / エミュレータ実行）。今回は軽量な `androidUnitTest` のみを整備する。
- **JUnit 依存**: `androidUnitTest` では `kotlin-test` に加え `kotlin-test-junit` を入れないと JUnit runner で動かない（AGP の androidUnitTest は JUnit4 ベース）。
- **CI 実行時間**: `iosSimulatorArm64Test` は konan キャッシュが効いていれば数十秒〜数分。既存の `build-ios-framework` ジョブ（timeout 45 分）の余裕内に収まる。
- **Konan キャッシュ**: 既存の `Cache Konan` ステップが `iosSimulatorArm64Test` でも再利用される。キャッシュキーはそのままで OK。

## 後続タスク（別チケット化）

- 既存 `actual` 実装のテスト:
  - Android: `TokenStorageFactory.android.kt`（EncryptedSharedPreferences）の Robolectric テスト、または instrumented test 化
  - iOS: `TokenStorageFactory.ios.kt`（Keychain）の `iosSimulatorArm64Test` 下でのテスト
- `HttpClientFactory` の共通テスト（MockEngine を使ったインターセプタ挙動検証）
- `composeApp` 側のテスト整備（必要になったら）
