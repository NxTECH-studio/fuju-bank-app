# T5-3: iOS エントリで Koin 起動

## 概要

Swift から呼べる `doInitKoin()` を shared module に公開し、`iosApp/iosApp/iOSApp.swift` で起動時に呼ぶ。iOS シミュレータで最低 1 API を疎通確認する。

## 背景・目的

iOS 側でも shared の API 連携層が機能することを保証するスモークテスト。

## 影響範囲

- モジュール: shared / iosApp
- ソースセット: commonMain / iosApp（Swift）
- 破壊的変更: なし
- 追加依存: なし

## 実装ステップ

1. `shared/src/commonMain/.../di/KoinIos.kt`:
   - `fun doInitKoin() { initKoin() }` — Swift から Obj-C 経由で呼べる top-level 関数。
2. `iosApp/iosApp/iOSApp.swift` の `App` struct の `init` で `SharedKt.doInitKoin()` を呼ぶ。
3. Swift 側に一時的なスモークボタン（`Shared` framework の `UserApi` を `get()` で Koin から取り出し、1 件叩いてログに出す）を追加。`// TODO: remove after smoke test`
4. iOS シミュレータで起動してログ確認。

## 検証

- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`
- [ ] Xcode で iOS シミュレータビルド
- [ ] スモークボタン押下 → Xcode コンソールに API レスポンスが出る

## 依存

- T5-1

## 技術的な補足

- Koin を Swift から直接使うのは面倒なので、`KoinIos.kt` に Swift 用ファサード関数（`fun userApi(): UserApi = KoinPlatformTools.defaultContext().get().get()`）を追加すると iOS 側コードが楽になる。
- `iosApp/` 側の Xcode プロジェクトにファイルを追加するだけで済むなら Swift コードの新規ファイルは作らず `iOSApp.swift` と既存の `ContentView.swift` に収める。
