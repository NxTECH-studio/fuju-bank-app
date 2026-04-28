# prod-smoke-test-against-api-fujupay-app: 本番 API (api.fujupay.app) に対する Release 実機スモークテスト

## 概要

PR #37 (`prod-base-url-env-switch`) で BuildKonfig の Release flavor が `https://api.fujupay.app` / `wss://api.fujupay.app/cable` を向くようになったので、Android Release AAB を実機で、iOS は Xcode から Release ビルドで動かし、本番エンドポイントに正しく到達できることを確認する。

## 背景・目的

PR #37 では Gradle / リンク段階までしか検証できておらず、以下が未確認:

- Release ビルド（Android 実機 / iOS は Xcode 経由）が本番 URL (`api.fujupay.app`) を実際に叩けるか
- TLS / 証明書 / ATS（iOS App Transport Security）周りで弾かれないか
- Android `usesCleartextTraffic` 等のネットワーク設定が Release 構成で正しく `https` のみを許可しているか
- `wss://` の WebSocket（ActionCable）が Release で確立できるか
- `-P buildkonfig.flavor=release` 付け忘れ自動補完が CI / 手元両方で動くか

`-P` 付け忘れによる「本番 URL を向いていない Release ビルドが配られる」事故を防ぐ最後の砦。

## 影響範囲

- モジュール: shared / composeApp / iosApp（ビルド成果物の確認のみ。コード変更は基本なし）
- 破壊的変更: なし
- 追加依存: なし

## 前提

- PR #37 が main にマージされていること
- バックエンド (`api.fujupay.app`) が `/up` を 200 で返す状態であること
- Android 実機 1 台と Xcode（iOS シミュレータ or 開発者証明書で繋いだ実機）が手元にあること

## 実装ステップ

1. **Android Release AAB / APK のビルド**
   - `./gradlew :composeApp:bundleRelease`（`-P` 無し → 自動で release flavor）
   - 生成された AAB / APK を実機にインストール（`bundletool` で APKS 展開 → `adb install-multiple`）
   - **ヘルスチェック**: `adb shell curl -sSI https://api.fujupay.app/up` で 200 が返ることを確認（端末のネットワーク経路 / TLS / DNS が通っていることを先に切り分け）。`curl` が無い端末なら Chrome で開く
   - 起動 → ログイン or 認証フロー → 残高 / 取引履歴取得が成功することを確認
   - `adb logcat | grep -i "fujupay\|ktor\|http"` で `https://api.fujupay.app` への通信が出ていることを確認
2. **iOS の Xcode Release ビルド**
   - Xcode で `iosApp` を開き、Edit Scheme → Run → Build Configuration を `Release` に変更
   - `./gradlew :shared:linkReleaseFrameworkIosArm64`（実機）または `:shared:linkReleaseFrameworkIosSimulatorArm64`（シミュレータ）が Xcode のビルドフェーズから走り、`buildkonfig.flavor=release` 自動補完で本番 URL になることを確認
   - Xcode から Run（シミュレータ or 開発者証明書を入れた実機）で起動
   - **ヘルスチェック**: シミュレータ / 実機の Safari で `https://api.fujupay.app/up` を開いて 200 が返ることを確認（ATS / 証明書チェーンを先に切り分け）
   - 起動 → 認証フロー → 残高 / 取引履歴取得が成功することを確認
   - Xcode Console で `https://api.fujupay.app` への通信ログを確認
   - 確認後は Run の Build Configuration を `Debug` に戻すことを忘れない
3. **WebSocket (ActionCable) の確認**
   - 残高更新 / リアルタイム通知が想定どおり配信されることを確認（`wss://api.fujupay.app/cable`）
   - 切断 → 再接続のリカバリも 1 度試す
4. **Release flavor 自動補完の確認**
   - `./gradlew :composeApp:assembleRelease`（`-P buildkonfig.flavor=release` を**付けない**）でビルドし、生成 APK 内の `BuildKonfig` がリリース URL になっていることを `apkanalyzer dex code` または逆コンパイルで確認
   - `./gradlew :shared:assembleDebug` では `10.0.2.2` のままであることも併せて確認（リグレッションチェック）
5. **結果の記録**
   - 本タスクの「検証」チェック欄を埋め、PR #37 の最後のチェックボックスもクローズ
   - 失敗があれば別チケット（`fix-prod-...`）を切って分離

## 検証

### 自動化済み（2026-04-28 確認）

- [x] `./gradlew :composeApp:bundleRelease`（`-P` 無し）が成功し、生成された `shared/build/buildkonfig/androidMain/.../BuildKonfig.kt` が本番 URL (`https://api.fujupay.app` / `wss://api.fujupay.app/cable`) になっている
- [x] `./gradlew :shared:linkReleaseFrameworkIosArm64` / `:linkReleaseFrameworkIosSimulatorArm64` が成功し、`iosArm64Main` / `iosSimulatorArm64Main` の `BuildKonfig.kt` も本番 URL になっている
- [x] Debug ビルド（`assembleDebug` / `linkDebugFrameworkIosSimulatorArm64`）後の BuildKonfig は Android `http://10.0.2.2:3000` / iOS `http://localhost:3000` に戻り、リグレッションなし

### 手動（要実機 / Xcode）

- [ ] Android Release AAB を実機で起動し、ログイン → 残高取得が成功
- [ ] iOS を Xcode の Release Run（シミュレータ or 実機）で起動し、ログイン → 残高取得が成功
- [ ] 両プラットフォームで `https://api.fujupay.app/up` 相当のヘルスチェック呼び出しが 200 を返す
- [ ] ActionCable (`wss://api.fujupay.app/cable`) で subscribe / イベント受信が成功
- [ ] Release ビルドのログに `10.0.2.2` / `localhost` が一切出ない

## 依存

- PR #37 (`prod-base-url-env-switch`) のマージ

## 技術的な補足・懸念

- iOS の ATS は `https` / `wss` のみなので追加設定不要のはず。`Info.plist` に `NSAllowsArbitraryLoads` が残っていないか念のため確認すること
- Android は `network_security_config.xml` で cleartext を全 domain に対して禁止しているか確認。Debug 用に `10.0.2.2` を許可する設定が Release にも漏れていないこと
- 認証ヘッダ（Bearer）を含む通信が Logging プラグイン経由で出力されていないか確認（PR #37 の "懸念点" に記載のとおり、`enableLogging = true` ハードコードは別タスク扱い。ここでは "出ていること自体" を記録するだけ）
- 本タスクは "本番 URL に到達できること" の確認が目的で、配布チャネル（TestFlight / Play Internal）自体の検証は含めない。TestFlight 配布まで踏み込むなら別タスクで切ること
