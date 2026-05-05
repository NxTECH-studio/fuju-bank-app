# client-bank-14 通知許可（OS 連動）Android 実装

> client-bank-6 を発展継承するタスク。client-bank-6 は未実装のまま deprecated 扱いとし、本ドキュメント (14) を最新版とする。

## 概要

`NotificationSettingsScreen`（Figma `718:7332`、client-bank-4 で実装済み）の上部に「OS の通知許可状態」セクションを追加する。Android 13+ の `POST_NOTIFICATIONS` ランタイム権限ダイアログ起動と OS 設定アプリへの導線を提供し、既存の着金 / 転送トグル（アプリ内意図値）と OS 側許可状態を **並列表示** で扱う。

仕様面（OS 許可状態の取り扱い、呼び出しタイミング、フォールバック UX、トグル連動方針、状態取得タイミング、エラーハンドリング）は iOS 版 (`client-bank-15`) と完全に揃え、UI 実装のみ Android 固有（Composable）で行う。

## 背景・目的

### 経緯

- client-bank-4 で `NotificationSettingsScreen` と `NotificationSettingsPreferences` を導入し、着金 / 転送のトグル状態を `multiplatform-settings` に永続化済み。
- 一方、`AndroidManifest.xml` には `POST_NOTIFICATIONS` 権限が未宣言。Android 13 (API 33) 以降のランタイム権限ダイアログを起動する手段が無く、アプリ内トグルがオンでも OS 通知許可が無ければ実通知は届かない。
- client-bank-6 で Android 単独実装として起票していたが、KMP は iOS/Android 両対応必須（ユーザーメモリ参照）かつ「UI 以外の仕様は両プラットフォームで揃える」方針に変更したため、本タスク (14) で iOS 版 (15) と仕様を共通化した形で再起票する。client-bank-6 は未着手のまま deprecated。

### 目的

- アカウント > 通知設定 から OS 通知許可状態を確認・要求できるようにする。
- 「アプリ内トグルはオン × OS 許可なし」という状態をユーザーが視認できるようにする（自動連動はしない）。
- iOS 版 (`client-bank-15`) とユーザー体験を揃える（後述「共通仕様」セクション）。

## スコープ

- **`AndroidManifest.xml` 変更**: `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />` を追加。
- **`NotificationSettingsScreen` 拡張**: ヘッダー直下、既存「着金通知 / 転送通知」カードの **上** に新規カード `NotificationPermissionCard` を追加。
- **OS 通知許可状態の取得・要求ヘルパ**（Android 限定、`composeApp/src/androidMain/.../features/account/notification/` 配下）。
- **画面復帰時の状態再取得**（`Lifecycle.Event.ON_RESUME`）。

### アウトオブスコープ

- iOS 実装（`client-bank-15` で別途）。
- `:shared` 拡張（OS 通知許可は完全にプラットフォーム個別 API なので shared には何も追加しない）。
- 既存 `NotificationSettingsPreferences` のキー / デフォルト変更。
- 実通知の送出 / FCM 連携。
- 「アプリ内オン × OS 未許可」状態の警告バナー / 自動連動（共通仕様で「並列表示・自動連動なし」と確定済み）。

## 着手条件

- client-bank-4 (`client-bank-4-account-settings-android.md`) が `main` にマージ済みで、以下が存在すること:
  - `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/account/NotificationSettingsScreen.kt`
  - `RootScaffold` の `RootDestination.NotificationSettings` 配線
  - `:shared` 側の `NotificationSettingsPreferences` Koin 登録

---

## 共通仕様（Android / iOS 両プラットフォームで同一）

> このセクションは `client-bank-15-push-notification-permission-ios.md` の同名セクションと **完全に同じ内容** を保つ。仕様変更時は両ファイルを同時更新すること。

### A. OS 許可状態の表示モデル

両プラットフォームで以下 4 状態を区別して表示する。OS API の戻り値を以下にマッピングする。

| 共通状態名             | 意味                                                        | Android (`POST_NOTIFICATIONS` / `NotificationManagerCompat`)                                                                                       | iOS (`UNUserNotificationCenter`)                                  |
| ---------------------- | ----------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------- |
| `NotDetermined`        | 未確定。アプリからダイアログを出せる                        | API 33+ かつ `ContextCompat.checkSelfPermission == PERMISSION_DENIED` かつ `shouldShowRequestPermissionRationale == false` かつ未要求             | `UNAuthorizationStatus.notDetermined`                             |
| `Granted`              | 許可済み                                                    | `NotificationManagerCompat.from(ctx).areNotificationsEnabled() == true`                                                                            | `.authorized` / `.provisional` / `.ephemeral`                     |
| `Denied`               | 拒否されている。アプリからダイアログ再表示は **不可**        | API 33+ で `shouldShowRequestPermissionRationale == true`、または「今後表示しない」状態。API 32 以下でユーザーが OS 設定で通知をオフにしている状態 | `.denied`                                                         |
| `SystemSettingsOnly`   | OS の制約上ランタイム要求の概念がなく、設定アプリでのみ変更 | API 32 以下で `areNotificationsEnabled() == true` の状態（ランタイム要求は不要、OS 設定のみ）                                                       | （iOS では基本未使用。将来の拡張のためモデルに含める）             |

### B. 許可ダイアログの呼び出しタイミング

- **アプリ起動時にプロアクティブには呼ばない**（オンボーディングや起動直後の自動要求は行わない）。
- ユーザーが「アカウント > 通知設定」を開き、OS 許可セクションの **「許可する」ボタンをタップしたとき** にのみ要求する。
- ダイアログを実際に表示できるのは `NotDetermined` のときのみ。`Denied` の場合はボタン押下で OS 設定アプリ導線に切り替える。

### C. 許可拒否時のフォールバック UX

- `Denied` 状態では「許可する」ボタンを **「OS 設定で開く」ボタンに置き換える**。
- ボタンをタップすると、本アプリの OS 通知設定画面を直接起動する:
  - Android: `Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)` + `EXTRA_APP_PACKAGE`。失敗時は `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` フォールバック。
  - iOS: `UIApplication.shared.open(URL(string: UIApplication.openSettingsURLString)!)`。
- 設定アプリから戻ってきたタイミングで状態を再取得し、UI を即座に更新する（仕様 E 参照）。

### D. アプリ内トグルとの連動方針

- アプリ内トグル（着金通知 / 転送通知 = `NotificationSettingsPreferences`）と OS 許可状態は **並列表示**。
- **自動連動なし**: アプリ内トグルをオンにしても OS 許可は要求しないし、OS で拒否されてもアプリ内トグルを自動でオフにしない。
- ユーザーが「アプリ内ではオン × OS 未許可」を視認できるよう、両者を独立したカードとして並べる。
- 将来的に警告バナーや自動連動を追加する余地は残すが、本タスクのスコープ外。

### E. 許可状態の取得タイミング

- 通知設定画面の **初回表示時**: その場で OS API を叩いて取得。
- **バックグラウンド復帰時 / 設定アプリから戻ってきたとき**: 同じく再取得して UI を更新する。
  - Android: `Lifecycle.Event.ON_RESUME` を Composable から観測。
  - iOS: SwiftUI `.task` + `@Environment(\.scenePhase)` の `.active` 復帰を観測。
- ダイアログを閉じた直後のコールバックでも再取得する（即時反映のため）。
- バックグラウンドで定期ポーリングはしない。

### F. エラーハンドリング・ログ出力

- OS API 呼び出し（権限要求 / 設定アプリ起動）が例外を投げた場合は、UI には共通の控えめなトースト相当の表現で「設定アプリを開けませんでした」を表示。再試行ボタンは設けない（ユーザーは OS の通知センター等から手動到達できる）。
- 例外スタックは `Log.w` / `os_log` 相当でローカルログに残す。リモートクラッシュ送信は本タスクではしない（送信基盤未整備）。
- ダイアログ呼び出し中に二重タップが起きてもクラッシュしないよう、ボタンは要求中は `disabled` にする。
- ログタグは Android 側 `NotificationPermission`、iOS 側 `NotificationPermission` で揃える（grep しやすさのため）。

---

## 影響範囲（Android）

- モジュール: `:composeApp`（Android のみ）
  - 変更: `composeApp/src/androidMain/AndroidManifest.xml` — `POST_NOTIFICATIONS` 追加
  - 変更: `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/account/NotificationSettingsScreen.kt` — 上部にカード追加
  - 新規: `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/account/notification/NotificationPermissionState.kt`
  - 新規: `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/account/notification/NotificationPermissionCard.kt`
- 破壊的変更: なし（`NotificationSettingsScreen` のシグネチャ維持、`AndroidManifest.xml` への権限追加は新規宣言）。
- 追加依存: なし。`androidx.core:core-ktx` 経由で `NotificationManagerCompat` が利用可能。`androidx.activity.compose.rememberLauncherForActivityResult` も既存利用。

## 関連ファイル

参考実装:
- `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/account/NotificationSettingsScreen.kt` — 拡張対象、白角丸カード（`RoundedCornerShape(20.dp)` + `shadow(4.dp)`）の参考
- `composeApp/src/androidMain/AndroidManifest.xml` — 権限追加対象
- `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/account/NotificationSettingsViewModel.kt` — 既存 ViewModel（変更しない）

## 実装ステップ

1. **Manifest 権限追加**
   - `composeApp/src/androidMain/AndroidManifest.xml` の `<application>` 上に `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />` を追加。
   - `./gradlew :composeApp:assembleDebug` が通ることを確認。

2. **`NotificationPermissionState.kt` 新規作成**
   - 共通仕様 A の状態（`NotDetermined` / `Granted` / `Denied` / `SystemSettingsOnly`）を sealed interface で表現。
   - `@Composable fun rememberNotificationPermissionState(): State<NotificationPermissionState>` を実装。
     - `LocalContext` から `NotificationManagerCompat.from(ctx).areNotificationsEnabled()` を呼ぶ。
     - API 33+ では `ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)` と `shouldShowRequestPermissionRationale` を組み合わせて `NotDetermined` / `Denied` を判別。
     - API 32 以下で許可済みなら `SystemSettingsOnly`、未許可（OS 設定でオフ）なら `Denied`。
   - `LocalLifecycleOwner` の `Lifecycle` を `LaunchedEffect` で観測し、`Event.ON_RESUME` で再評価（共通仕様 E）。

3. **権限要求ヘルパ実装**
   - `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())` で `POST_NOTIFICATIONS` を要求。コールバックで再評価をトリガ。
   - 例外時は `Log.w("NotificationPermission", e)` でログ出力（共通仕様 F）。

4. **`NotificationPermissionCard.kt` 新規作成**
   - 既存 `NotificationCard` と同じスタイル（白背景 / `RoundedCornerShape(20.dp)` / `shadow(4.dp, clip=false)`）。
   - 内部レイアウトは `Row(SpaceBetween)`、左にタイトル「OS 通知許可」+ サブ説明、右に状態別ボタン:
     - `NotDetermined`: 塗りつぶしボタン「許可する」（`FujuBankColors.BrandPink`）→ `launcher.launch(POST_NOTIFICATIONS)`
     - `Granted`: テキスト「許可済み」+ アウトライン「OS 設定で開く」リンク
     - `Denied`: アウトラインボタン「OS 設定で開く」（共通仕様 C）
     - `SystemSettingsOnly`: アウトラインボタン「OS 設定で開く」
   - 「OS 設定で開く」コールバックは `Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)` を起動し、`ActivityNotFoundException` 時は `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` にフォールバック。
   - 要求中は対象ボタンを `enabled = false`（共通仕様 F）。
   - Preview で 4 状態を並べる。

5. **`NotificationSettingsScreen` 拡張**
   - `Column` 内、既存 `NotificationCard` の **前** に `NotificationPermissionCard` を挿入。
   - `rememberNotificationPermissionState()` の値とランチャを渡す。
   - 既存トグル仕様には触らない。

6. **動作確認** — 「動作確認手順」セクション参照。

7. **PR 作成**
   - ブランチ: `feature/client-bank-14-push-notification-permission-android`
   - 対象: `main`

## 完了条件

- [ ] `./gradlew build` が通る
- [ ] `./gradlew :composeApp:assembleDebug` が通る
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が通る（iOS 影響なし担保）
- [ ] `AndroidManifest.xml` に `POST_NOTIFICATIONS` が宣言されている
- [ ] API 33+ 端末で `NotDetermined` 時に「許可する」→ ランタイム権限ダイアログが表示される
- [ ] 許可後カードが `Granted` 表示に切り替わる
- [ ] 拒否後カードが `Denied` 表示に切り替わり「OS 設定で開く」が表示される
- [ ] API 32 以下端末で `SystemSettingsOnly` / `Denied` のいずれかが正しく表示される
- [ ] OS 設定で許可状態を変更してアプリ復帰すると `ON_RESUME` 経由で UI が更新される
- [ ] 既存の着金 / 転送トグルがリグレッションなく動作し、再起動後も値が保持される
- [ ] 共通仕様 A〜F が iOS 版 (`client-bank-15`) と齟齬なく実装されている

## 動作確認手順

1. **API 33+ エミュレータ・初回起動**: アプリ初回 → アカウント → 通知設定 → 「許可する」タップ → ダイアログ表示 → 許可 → カード `Granted` に切替。
2. **API 33+ エミュレータ・拒否経路**: もう一度初回 → 「許可する」→ 拒否 → カードが `Denied` に切替 → 「OS 設定で開く」タップ → OS 設定が開くこと → 戻ってくると状態が再取得されること。
3. **API 32 以下エミュレータ**: 「OS 設定で開く」のみ表示、タップで OS 通知設定が開く。
4. **既存トグル**: 着金 / 転送トグルが従来通り永続化される（リグレッションなし）。
5. **アプリ内オン × OS 未許可の並列表示**: 着金トグルをオン、OS 許可を `Denied` にして、両方が独立して表示されることを目視確認。

## 想定される懸念・リスク

- **「永続拒否」（API 33+ で 1 度拒否後）**: `requestPermission` を呼んでもダイアログが出ない。共通仕様 C 通り `Denied` 状態で「OS 設定で開く」に置き換えるため UX 的には自然。
- **`POST_NOTIFICATIONS` 宣言によるストア表記変更**: ユーザー向け権限表記に「通知」が出る。将来 Push 配信は必須機能なので前倒し宣言は妥当。
- **iOS との仕様乖離リスク**: 共通仕様セクションが両ファイルでドリフトしないよう、本タスクと client-bank-15 のレビューを **必ず同時に** する。

## 関連リンク

- 元タスク（deprecated）: [`client-bank-6-notification-permission-android.md`](./client-bank-6-notification-permission-android.md)
- ペアタスク (iOS): [`client-bank-15-push-notification-permission-ios.md`](./client-bank-15-push-notification-permission-ios.md)
- 前提タスク (Android アカウント設定): [`client-bank-4-account-settings-android.md`](./client-bank-4-account-settings-android.md)
- 前提タスク (iOS アカウント設定): [`client-bank-5-account-settings-ios.md`](./client-bank-5-account-settings-ios.md)
- Android Developers: [Notification runtime permission](https://developer.android.com/develop/ui/views/notifications/notification-permission)
- Android Developers: [`NotificationManagerCompat.areNotificationsEnabled()`](https://developer.android.com/reference/androidx/core/app/NotificationManagerCompat#areNotificationsEnabled())

---

## Notion タスク登録用サマリ

- **タイトル**: 銀行アプリクライアント：通知許可（OS 連動）Android 実装（client-bank-6 を継承）
- **プレフィックス**: client-bank-14
- **ブランチ命名**: `feature/client-bank-14-push-notification-permission-android`
- **計画書パス**: `docs/tasks/client-bank-14-push-notification-permission-android.md`
- **依存タスク**: client-bank-4 完了済み
- **ペアタスク**: client-bank-15（iOS）
- **PR 構成**: 1 本（Android のみ。`:shared` 変更なし）
