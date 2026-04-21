# T5-2: Android エントリで Koin 起動

## 概要

composeApp の Android エントリ（Application クラス新設 or `MainActivity`）で `initKoin { androidContext(this) }` を呼び、Android シミュレータで最低 1 API を疎通確認する。

## 背景・目的

実機/シミュレータで shared モジュールの API 連携層が機能することを保証する最初のスモークテスト。

## 影響範囲

- モジュール: composeApp
- ソースセット: androidMain
- 破壊的変更: なし
- 追加依存: なし

## 実装ステップ

1. `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/FujuBankApp.kt` を新設:
   - `class FujuBankApp : Application() { override fun onCreate() { super.onCreate(); initKoin { androidContext(this@FujuBankApp) } } }`
2. `composeApp/src/androidMain/AndroidManifest.xml` の `<application android:name=".FujuBankApp">` を追加。
3. 既存 `App.kt` か `MainActivity.kt` に **デバッグ用** の「`UserApi.get()` を 1 回叩いて結果をログに出す」ボタンを一時的に追加。リリース前に削除する想定でコメント `// TODO: remove after smoke test` を付ける。
4. Android シミュレータで起動し、ログに `balance_fuju` が表示されることを確認。

## 検証

- [ ] `./gradlew :composeApp:assembleDebug`
- [ ] Android シミュレータでアプリ起動
- [ ] スモークボタン押下 → ログに API レスポンスが出る

## 依存

- T5-1

## 技術的な補足

- AndroidManifest が無ければ `composeApp/src/androidMain/AndroidManifest.xml` を新設。パッケージ指定は `studio.nxtech.fujubank`。
- `FujuBankApp` にせずに `MainActivity.onCreate` で `initKoin` を呼ぶ簡易版も可（Application を作らない軽量パス）。既存コード優先。
