# T0-1: KMP ネットワーク基盤の依存追加

## 概要

Ktor / kotlinx.serialization / kotlinx.coroutines / kotlinx.datetime / Koin / androidx.security-crypto を Version Catalog に追加し、`shared/build.gradle.kts` の `commonMain` / `androidMain` / `iosMain` に配線する。

## 背景・目的

後続の全 API 連携タスク（T1 以降）が前提とする依存をまとめて入れる。以降の PR では `libs.versions.toml` と `shared/build.gradle.kts` を触らないためのワンショット。

## 影響範囲

- モジュール: shared
- ソースセット: commonMain / androidMain / iosMain（エンジン登録）
- 破壊的変更: なし
- 追加依存: 本タスク本文参照

## 追加依存

`gradle/libs.versions.toml`:

### [versions]

- `ktor = "3.0.x"`（Kotlin 2.3.20 互換の最新安定版を確認して固定）
- `kotlinxSerialization = "1.7.x"`
- `kotlinxCoroutines = "1.9.x"`
- `kotlinxDatetime = "0.6.x"`
- `koin = "4.0.x"`
- `androidxSecurityCrypto = "1.1.0-alpha06"`

### [libraries]

- `ktor-client-core`, `ktor-client-content-negotiation`, `ktor-client-logging`, `ktor-client-auth`, `ktor-client-websockets`
- `ktor-serialization-kotlinx-json`
- `ktor-client-okhttp`（android）
- `ktor-client-darwin`（ios）
- `kotlinx-serialization-json`
- `kotlinx-coroutines-core`
- `kotlinx-datetime`
- `koin-core`, `koin-android`
- `androidx-security-crypto`

### [plugins]

- `kotlinSerialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }`

## 実装ステップ

1. `gradle/libs.versions.toml` の `[versions]` / `[libraries]` / `[plugins]` に上記を追記。
2. `shared/build.gradle.kts` の `plugins { ... }` に `alias(libs.plugins.kotlinSerialization)` を追加。
3. `sourceSets.commonMain.dependencies` に Ktor core/content-negotiation/logging/auth/websockets、ktor-serialization-kotlinx-json、kotlinx-serialization-json、kotlinx-coroutines-core、kotlinx-datetime、koin-core を追加。
4. `sourceSets.androidMain.dependencies` に ktor-client-okhttp、koin-android、androidx-security-crypto を追加。
5. `sourceSets.iosMain.dependencies` に ktor-client-darwin を追加。

## 検証

- [ ] `./gradlew :shared:build`
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`

## 依存

なし（全タスクの起点）

## 技術的な補足

- Ktor 3.x 系は Kotlin 2.0+ 対応。バージョン確認は [ktor リリース](https://github.com/ktorio/ktor/releases)で行う。
- `kotlinx.serialization` プラグインは Kotlin バージョンと同期する必要あり（`version.ref = "kotlin"`）。
- iOS の Darwin engine は `linkDebugFrameworkIosSimulatorArm64` 時にリンクされる。
