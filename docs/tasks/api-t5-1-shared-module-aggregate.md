# T5-1: SharedModule aggregate + initKoin

## 概要

Phase 4 で追加した Koin module 群を `di/SharedModule.kt` に aggregate し、`initKoin()` 関数を提供する。

## 背景・目的

プラットフォーム側（Android / iOS）から単一のエントリ関数で Koin を起動できるようにする。各 module を個別に import する責務を shared 側に閉じ込める。

## 影響範囲

- モジュール: shared
- ソースセット: commonMain
- 破壊的変更: なし
- 追加依存: なし

## 実装ステップ

1. `shared/src/commonMain/.../di/SharedModule.kt`:
   - `val sharedModules: List<Module> = listOf(authModule, userModule, ledgerModule, realtimeModule, artifactModule)`
   - `fun initKoin(appDeclaration: KoinAppDeclaration = {}): KoinApplication = startKoin { appDeclaration(); modules(sharedModules) }`
2. `di/` の marker（T0-2 で作成したもの）を削除。
3. commonTest で全 module の `verify()` を実行する簡易テストを追加（`checkModules` API を使用）。

## 検証

- [ ] `./gradlew :shared:allTests`
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`

## 依存

- T4-1, T4-2, T4-3, T4-4, T4-5

## 技術的な補足

- Koin の `checkModules` は commonTest から実行できる（koin-test 依存が必要な場合は T0-1 にフィードバックして追加）。
- `appDeclaration` に Android 側が `androidContext(...)` を渡せるようにしておくことで、T5-2 / T5-3 が小さく済む。
