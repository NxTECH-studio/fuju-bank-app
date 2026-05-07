# Account タブ release クラッシュ修正

## 概要

release flavor (`USE_DUMMY_PROFILE=false`) で `AccountModule` が `error("RemoteAccountProfileProvider is not implemented yet")` を投げて Android/iOS 両方が起動直後にクラッシュする問題を、`RemoteAccountProfileProvider` を実装することで解消する。

## 背景・目的

- 直接原因: `shared/src/commonMain/kotlin/studio/nxtech/fujubank/di/AccountModule.kt:29` の `error("RemoteAccountProfileProvider is not implemented yet")` が release flavor で発火し、Koin 解決のタイミングで例外が投げられて落ちる（Android logcat で確認済み）。
- 影響: release ビルドで AccountHub タブを開けない（実質、本番ビルド全機能が不安定）。debug ビルドは `DummyAccountProfileProvider` が返るので問題なし。
- ゴール: `RemoteAccountProfileProvider` を実装し、release ビルドで AccountHub タブを開いてもクラッシュしない状態にする。Crashlytics 等のクラッシュ収集 SDK 導入は別タスク。
- MVP の方針（受け取り専用 / 編集機能は後回し）に合わせ、AccountHub の編集 UI は今回無効化する。

## 影響範囲

- モジュール: `shared` および `composeApp`
- ソースセット:
  - `shared/src/commonMain/...`（新規 `RemoteAccountProfileProvider` 実装、DTO 微修正、Koin 設定差し替え）
  - `composeApp/src/commonMain/...`（AccountHub 表示・編集 UI の調整）
- 破壊的変更: なし（`AccountProfileProvider` インターフェースは変更しない / 変更しても ABI 互換）
- 追加依存: なし（`libs.versions.toml` への追記は不要）
- iOS 追加実装: なし。`KoinIos.kt` / `AccountIos.kt` は既存抽象に依存しているため、Koin 差し替えだけで iOS 側にも同じ Remote 実装が解決される

## 実装ステップ

1. `shared/.../data/remote/dto/UserDto.kt` の `UserResponse` に `name: String? = null` を nullable 追加（B-3）。bank サーバ側に `name` フィールドが入る前提のフォワード互換。
2. `shared/.../account/RemoteAccountProfileProvider.kt` を新規作成。
   - コンストラクタで `ProfileRepository`（または同等のユーザー取得 Repository）と `CoroutineScope` を受け取る。
   - 生成時に 1 回だけプロフィール取得を行うパターン:
     ```kotlin
     init {
         scope.launch {
             runCatching { profileRepository.getMyProfile() }
                 .onSuccess { _profile.value = it.toAccountProfile() }
         }
     }
     ```
   - 失敗時は空の `AccountProfile()`（フィールド全て空文字）のままにする。例外は投げない（D-1）。
3. `UserProfile` → `AccountProfile` のマッピング関数を実装。
   - `displayName` ← `bank.name` ?: `authCore.email?.substringBefore("@")` ?: `authCore.publicId` ?: `""`（B-3 + B-1）
   - `email` ← `authCore.email` ?: `""`
   - `accountId` ← `bank.id?.toString()` ?: `""`
4. `shared/.../di/AccountModule.kt:29` の `error(...)` を `RemoteAccountProfileProvider(get(), get())` に置換。
   - Koin で `CoroutineScope` がまだ提供されていなければ、既存の同等 Scope（例: `SessionStore` 周りで使っているスコープ）を流用するか、`single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }` を Koin に提供する。
5. `composeApp/.../features/account/AccountHubScreen` 側で **編集 UI を無効化（D-c）**。
   - 編集トリガー（鉛筆アイコン / 行タップ等）の onClick を no-op、または該当アイコンを非表示にする。
   - `AccountInfoEditSheet` への遷移経路を切る。シート/画面のコード自体は将来の復活を見越して残す。
6. `composeApp/.../features/account/AccountHubScreen` の表示で **空文字フィールドを「-」プレースホルダに置き換え**。
   - `displayName` / `email` / `accountId` のどれかが空のとき `"-"` を表示。
7. `DummyAccountProfileProvider` は debug ビルドで動作し続けるので変更不要。

## 検証

- [ ] `./gradlew :shared:allTests` が通る
- [ ] `./gradlew :composeApp:assembleDebug` が通る（debug = Dummy で従来通り動作）
- [ ] `./gradlew :composeApp:assembleRelease` が通る
- [ ] Android リリースビルドを実機 or エミュレータで起動し、ログイン → アカウントタブ表示でクラッシュしないことを logcat で確認
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が通る
- [ ] iOS シミュレータで iosApp を起動し、ログイン → アカウントタブ表示でクラッシュしないことを Xcode console で確認
- [ ] AccountHub の編集 UI が無効化されている（タップしても何も起きない / アイコンが出ない）
- [ ] プロフィール取得成功時に `displayName` / `email` / `accountId` が表示される
- [ ] プロフィール取得失敗時に「-」プレースホルダが表示される

## 技術的な補足

- `BuildKonfig.USE_DUMMY_PROFILE` の運用: debug=true / release=false の現状維持（フラグごと削除はしない）。debug でのオフライン開発体験を維持するため。
- 編集機能（`AccountInfoEditSheet` 自体のコード）は将来 AuthCore に email/displayName 更新 API が実装されたとき復活させる前提で **コードは残す**（試行錯誤の履歴を残す方針）。
- 関連の今後タスク:
  - bank サーバ側に `/users/me` レスポンスへの `name` フィールド追加
  - AuthCore に email・displayName 更新 API 追加
  - これらが入ったら本タスクの実装ステップ 1（`UserResponse.name`）と 5（編集 UI 無効化）を見直す。
- iOS 側は `AccountProfileProvider` 抽象に依存しているだけなので追加実装なし。Koin 経由で同じ Remote 実装が解決される。
- `RemoteAccountProfileProvider` は生成時 1 回取得（D-1）。タブ切り替えごとに refetch しない。アプリ再起動 or ログイン直後にだけ最新化される運用。将来的に pull-to-refresh / リアクティブ購読を入れる場合は別タスクで拡張する。
