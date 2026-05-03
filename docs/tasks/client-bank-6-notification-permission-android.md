# 銀行アプリクライアント：通知許可（OS 連動）Android 実装

## 概要

client-bank-4 で実装した `NotificationSettingsScreen`（Figma `718:7332`）の上部に「OS の通知許可状態」セクションを追加し、Android 13+ の `POST_NOTIFICATIONS` ランタイム権限ダイアログ起動と Android 設定アプリへの導線を提供する。既存の着金通知 / 転送通知トグル（アプリ内意図値）と OS 側許可状態を分離して扱い、ユーザーが「アプリ内ではオンにしているのに OS 側許可がない」状況を視覚的に把握できるようにする。

## 背景・目的

### 経緯

- client-bank-4 (`client-bank-4-account-settings-android.md`) で `NotificationSettingsScreen` と `NotificationSettingsPreferences` を導入し、着金 / 転送のトグル状態を `multiplatform-settings` に永続化済み
- 一方、`AndroidManifest.xml` には `POST_NOTIFICATIONS` 権限が未宣言。Android 13 (API 33) 以降のランタイム権限ダイアログを起動する手段が無く、アプリ内トグルがオンでも OS 通知許可が無ければ実通知は届かない
- client-bank-4 の「想定される懸念・リスク」で `NotificationSettingsPreferences` のデフォルト値と OS 側許可の不整合が後続タスクで対応する想定として残っていた

### 目的

- `NotificationSettingsScreen` 上部に **OS 通知許可セクション** を追加し、状態を 3 パターンで表示する:
  - **未許可（Android 13+）**: 「許可する」ボタン → ランタイム権限ダイアログを起動
  - **許可済み（全 API）**: 「許可済み」ラベル + 「OS 設定で管理」リンクボタン
  - **Android 12 以下**: ランタイム権限不要のため「OS 設定で管理」リンクのみ表示
- `AndroidManifest.xml` に `POST_NOTIFICATIONS` 権限を宣言する（API 33+ 環境のみ要求される）
- 既存の `NotificationSettingsPreferences`（アプリ内意図値）には触らず、OS 側状態は **OS 由来の都度参照** で扱う（永続化不要）
- iOS は別 API（`UNUserNotificationCenter`）なので本タスクではアウトオブスコープ。後続 iOS タスクは shared には依存せず純 SwiftUI で実装する想定

## スコープ

- **`AndroidManifest.xml` 変更**
  - `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />` を追加
- **`NotificationSettingsScreen` の拡張**
  - ヘッダー直下、既存「着金通知 / 転送通知」カードの **上** に新規カード `NotificationPermissionCard` を追加
  - カード内容は OS 状態に応じて 3 パターン分岐（後述）
- **OS 通知許可状態の取得・要求ヘルパ**（Android 限定）
  - `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/account/notification/NotificationPermissionState.kt`（仮称）
  - `NotificationManagerCompat.from(context).areNotificationsEnabled()` で現在状態を取得
  - `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())` で `POST_NOTIFICATIONS` を要求
  - 「OS 設定で管理」は `Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)`（API 26+ で利用可能、最小 API 24 のため API 25 以下フォールバックは `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` + `Uri.fromParts("package", ...)` ）
  - SDK バージョン分岐: `Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU`（API 33）
- **画面復帰時の状態再取得**
  - 設定アプリから戻った直後に再評価したいので、`Lifecycle.Event.ON_RESUME` フックで `areNotificationsEnabled()` を再評価し、表示を更新する

### アウトオブスコープ

- **iOS 実装**: `UNUserNotificationCenter.requestAuthorization` を使う別実装。client-bank-5 (iOS) マージ後に `client-bank-9-notification-permission-ios`（仮）として別タスク化する
- **`:shared` 拡張**: OS 通知許可は完全にプラットフォーム個別 API なので shared には何も追加しない
- **着金 / 転送トグル仕様の変更**: 既存 `NotificationSettingsPreferences` のキーやデフォルトは変更しない
- **実通知の送出 / FCM 連携**: Push 配信基盤は別タスク
- **「アプリ内オン × OS 未許可」状態のバナー警告 / 自動連動**: 本タスクでは「並列表示」のみ。アプリ内トグルの自動オフや警告バナーは UX 確定後に別タスクで検討
- **Android 12 (API 32) 以下での通知チャンネル毎制御**

## 着手条件

**client-bank-4 (`client-bank-4-account-settings-android.md`) が `main` にマージ済みであること**。

具体的には:

- `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/account/NotificationSettingsScreen.kt` が存在
- `RootScaffold` の `RootDestination.NotificationSettings` 配線が完了している
- `:shared` の `NotificationSettingsPreferences` が Koin に登録済み

## 影響範囲

- モジュール: `:composeApp`（Android のみ）
  - `composeApp/src/androidMain/AndroidManifest.xml`: `POST_NOTIFICATIONS` 権限追加
  - `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/account/NotificationSettingsScreen.kt`: 拡張
  - `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/account/notification/`（新設）: 権限ヘルパ / カード Composable
- 破壊的変更:
  - `NotificationSettingsScreen` のシグネチャは現状維持（`viewModel` / `onBack` / `onNotificationClick`）。新規セクションは内部で `LocalContext` から取得するため呼び出し側は変更不要
  - `AndroidManifest.xml` への権限追加は新規宣言のため後方互換
- 追加依存:
  - なし（`androidx.core:core-ktx` 経由で `NotificationManagerCompat` は既に利用可能）
  - `androidx.activity.compose.rememberLauncherForActivityResult` も `androidx-activity-compose` 既存利用

## 技術アプローチ

### Android Manifest 変更

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

`<application>` の前に追加。`maxSdkVersion` は付けない（API 33+ で必要、それ以下では OS が無視する）。

### 権限状態の取得

```kotlin
// NotificationPermissionState.kt（概念例）
internal sealed interface NotificationPermissionState {
    data object Granted : NotificationPermissionState
    data object DeniedRequestable : NotificationPermissionState   // API 33+ かつ未許可
    data object DeniedSystemOnly : NotificationPermissionState     // API 32 以下、または既に「今後表示しない」状態
}

@Composable
internal fun rememberNotificationPermissionState(): NotificationPermissionState { ... }
```

`Composable` 内で `LocalContext` から `NotificationManagerCompat.from(context).areNotificationsEnabled()` を呼ぶ。`LocalLifecycleOwner` の `Lifecycle` を `LaunchedEffect` で観測し、`ON_RESUME` で再評価する（設定アプリから戻った瞬間に反映）。

API 33+ かつ未許可の場合のみ `DeniedRequestable`、それ以外の未許可ケースは `DeniedSystemOnly`（OS 設定アプリ誘導のみ）として扱う。`shouldShowRequestPermissionRationale` は永続拒否判定に使うが、本タスクでは UX を簡略化し「`DeniedRequestable` が出たら必ずダイアログ起動を試みる」だけで十分（OS が結果的に表示しなかった場合は `Granted` にならず再描画後も `DeniedRequestable` のままになるので、ユーザーは何度かタップ → 反応が無ければ「OS 設定で管理」を別途試せる構造）。

### 権限要求

```kotlin
val launcher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission(),
) { granted ->
    // granted を受けて再評価。NotificationPermissionState は ON_RESUME 経由でも更新されるので
    // ここでは追加処理を最小化する。
}

if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
}
```

### OS 設定アプリへの導線

```kotlin
val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
}
context.startActivity(intent)
```

`ACTION_APP_NOTIFICATION_SETTINGS` は API 26+ で利用可能、最小 API は 24 なので API 24/25 用のフォールバックを 1 段だけ用意する:

```kotlin
val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
    data = Uri.fromParts("package", context.packageName, null)
}
```

### UI 構成

`NotificationPermissionCard` は client-bank-4 の `NotificationCard` と同じスタイル（白背景 / `RoundedCornerShape(20dp)` / `shadow(4dp)`）で実装し、`NotificationSettingsScreen` の `Column` 内で既存カードの **前** に挿入する:

```kotlin
Column(...) {
    NotificationPermissionCard(state = permissionState, onRequest = { launcher.launch(...) }, onOpenSettings = { ... })
    NotificationCard(depositEnabled = ..., transferEnabled = ..., ...)
}
```

カード内のレイアウトは `ToggleRow` と同じ `Row(SpaceBetween)` で、左側にタイトル「OS 通知許可」+ サブテキスト（状態説明）、右側に状態に応じたボタン:

| state                | 右側ボタン                            |
| -------------------- | ------------------------------------- |
| `Granted`            | テキスト「許可済み」+ 「設定」リンク  |
| `DeniedRequestable`  | 塗りつぶしボタン「許可する」           |
| `DeniedSystemOnly`   | アウトラインボタン「OS 設定で管理」    |

ボタンの色 / フォントは `FujuBankColors.BrandPink` を使い、既存のトグル群と視覚的に揃える。

## 実装手順

1. **Manifest 権限追加**
   1. `composeApp/src/androidMain/AndroidManifest.xml` に `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />` を追加
   2. `./gradlew :composeApp:assembleDebug` が通ることを確認
2. **権限ヘルパ実装**
   1. `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/account/notification/NotificationPermissionState.kt` を新規作成
   2. `NotificationPermissionState`（sealed interface）と `rememberNotificationPermissionState()` Composable を実装
   3. `Lifecycle.Event.ON_RESUME` 観測で状態を再評価する処理を含める
3. **`NotificationPermissionCard` 実装**
   1. 同ディレクトリに `NotificationPermissionCard.kt` を新規作成
   2. 3 パターン分岐の UI を実装、Preview で 3 状態を並べて確認
4. **`NotificationSettingsScreen` 拡張**
   1. `Column(...)` 内、既存 `NotificationCard` の前に `NotificationPermissionCard` を挿入
   2. `rememberNotificationPermissionState()` の値と `rememberLauncherForActivityResult` のラッパを生成し、コールバックを接続
   3. 「OS 設定で管理」コールバックは `LocalContext` から `Intent` を組んで `startActivity`
5. **動作確認（実機 / エミュレータ）**
   1. **API 33+ 端末・初回起動**: アプリ初回 → 通知設定画面遷移 → 「許可する」タップ → ダイアログ表示 → 許可 → カード表示が「許可済み」に切り替わる
   2. **API 33+ 端末・OS 設定で許可取り消し**: OS 設定で通知をオフ → アプリに戻ると `ON_RESUME` で再評価され「OS 設定で管理」表示に切り替わる
   3. **API 32 以下端末**: 「OS 設定で管理」のみ表示、タップで OS 通知設定が開く
   4. **既存トグル**: 着金 / 転送トグルが従来通り永続化される（リグレッションなし）
6. **PR 作成**: `feature/client-bank-6-notification-permission-android` → `main`

## 完了条件

- [ ] `./gradlew build` が通る
- [ ] `./gradlew :composeApp:assembleDebug` が通る
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が通る（iOS リンクへのリグレッションなしを担保）
- [ ] `AndroidManifest.xml` に `POST_NOTIFICATIONS` が宣言されている
- [ ] API 33+ 端末で「許可する」タップ → ランタイム権限ダイアログが表示される
- [ ] API 33+ 端末で許可済み状態のカードに「許可済み」ラベルと「設定」リンクが表示される
- [ ] API 32 以下端末で「OS 設定で管理」リンクのみ表示される
- [ ] 設定アプリから許可状態を変更してアプリに戻ると、カード表示が自動更新される
- [ ] 既存の着金通知 / 転送通知トグルがリグレッションなく動作し、再起動後も値が保持される

## 想定される懸念・リスク

- **「永続拒否」判定の取り扱い**: API 33+ では 1 度拒否されると `requestPermission` を呼んでもダイアログが表示されず即時拒否される（`shouldShowRequestPermissionRationale` で識別可能）。本タスクでは UX 簡略化のため「ダイアログが出なかった場合のフォールバック」は明示せず、ユーザーは別の「OS 設定で管理」ボタンに誘導される設計にしておく（`DeniedRequestable` でも `DeniedSystemOnly` でも常に設定アプリ導線を併設する案も検討対象）
- **`ON_RESUME` 再評価のオーバーヘッド**: 通知設定画面に滞在中はアプリが ON_RESUME を頻繁に受けないので問題なし。タブ切替で別画面に行って戻る場合も再評価 1 回で済むため許容
- **API 33+ 端末でのテストデバイス確保**: エミュレータで Android 13+ イメージを用意して検証する。API 32 以下のテストはエミュレータで API 30 等を別途用意
- **iOS への伝播**: `NotificationSettingsPreferences` には触らないため、iOS（client-bank-5）のリグレッションは発生しない見込み。ただし PR ベースで `:shared` 関連変更が無いことを差分で再確認する
- **後続 iOS タスクへの影響**: iOS は `UNUserNotificationCenter` を使う完全別実装。本タスクで Android 側に追加した shared API は無いため、後続 iOS タスクで参照する shared API は **なし**（iOS は SwiftUI 内で完結）
- **`POST_NOTIFICATIONS` を Manifest に追加するとリリース時のストア表記に「通知」権限が出る**: ユーザー向け表記が変わる点を留意。とはいえ将来的に通知配信は必須機能なので前倒しで宣言しておく方が望ましい

## 参考リンク

- 前提タスク 4 (Android アカウント設定): [`client-bank-4-account-settings-android.md`](./client-bank-4-account-settings-android.md)
- 前提タスク 5 (iOS アカウント設定): [`client-bank-5-account-settings-ios.md`](./client-bank-5-account-settings-ios.md)
- Android Developers: [Notification runtime permission](https://developer.android.com/develop/ui/views/notifications/notification-permission)
- Android Developers: [`NotificationManagerCompat.areNotificationsEnabled()`](https://developer.android.com/reference/androidx/core/app/NotificationManagerCompat#areNotificationsEnabled())
- 既存対象ファイル: `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/account/NotificationSettingsScreen.kt`
- 既存対象ファイル: `composeApp/src/androidMain/AndroidManifest.xml`

---

## Notion タスク登録用サマリ

- **タイトル**: 銀行アプリクライアント：通知許可（OS 連動）Android 実装
- **プレフィックス**: client-bank-6
- **ブランチ命名**: `feature/client-bank-6-notification-permission-android`
- **メモ欄に貼る計画書パス**: `docs/tasks/client-bank-6-notification-permission-android.md`
- **依存タスク**: client-bank-4 (Android アカウント設定) 完了
- **後続タスク**: iOS 版通知許可（client-bank-5 iOS 完了後に別タスク化）
- **PR 構成**: 1 本（Android のみ。`:shared` 変更なし）
