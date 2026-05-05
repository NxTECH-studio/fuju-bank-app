# client-bank-15 通知許可（OS 連動）iOS 実装

> client-bank-14 (Android) のペアタスク。UI は SwiftUI で実装するが、UI 以外の仕様は共通仕様セクションに従って Android と完全に揃える。

## 概要

`NotificationSettingsView`（Figma `718:7332`、client-bank-5 で実装済み）の上部に「OS の通知許可状態」セクションを追加する。`UNUserNotificationCenter` で許可状態を取得・要求し、拒否時は `UIApplication.openSettingsURLString` で OS 設定アプリへ誘導する。既存の着金 / 転送トグル（`NotificationSettingsPreferences`）と OS 側許可状態を **並列表示** で扱う。

実装方針はユーザー指示により **`:shared` を介さず SwiftUI View 内で完結**（Android の Composable 完結方式と対称）。`@State` + `.task` + `@Environment(\.scenePhase)` で `UNUserNotificationCenter` を直接呼ぶ。

## 背景・目的

- KMP は iOS/Android 両対応必須（ユーザーメモリ参照）。Android 側 (`client-bank-14`) で プッシュ通知許可セクションを追加するため、iOS でも同等の体験を提供する。
- iOS は `UNUserNotificationCenter` API で完全別実装になるため、`:shared` 拡張はせず SwiftUI 内で閉じる。Android 側も `:shared` には何も追加しない方針なので対称性が取れる。
- 「UI 以外の仕様は揃える」というユーザー指示を満たすため、状態モデル / 呼び出しタイミング / フォールバック / 連動方針 / 取得タイミング / エラーハンドリングを共通仕様として定義し、両ファイルに同じ内容を載せる。

## スコープ

- **`NotificationSettingsView` 拡張**: ヘッダー直下、既存 `NotificationCard` の **上** に新規 View `NotificationPermissionCard`（仮称）を追加。
- **プッシュ通知許可状態の取得・要求ヘルパ**（iOS 限定、SwiftUI View 内で完結）。
- **シーン復帰時の状態再取得**（`@Environment(\.scenePhase)` の `.active` 復帰）。
- **`Info.plist`**: `UNUserNotificationCenter.requestAuthorization` は Info.plist の追加文言を必要としないため変更なし（`NSUserNotificationsUsageDescription` のような専用キーは存在しない）。

### アウトオブスコープ

- Android 実装（`client-bank-14` で別途）。
- `:shared` 拡張。
- 既存 `NotificationSettingsPreferences` のキー / デフォルト変更。
- Push 通知トークン登録 / APNs 連携 / FCM 連携。
- 「アプリ内オン × OS 未許可」状態の警告バナー / 自動連動。

## 着手条件

- client-bank-5 (`client-bank-5-account-settings-ios.md`) が `main` にマージ済みで、以下が存在すること:
  - `iosApp/iosApp/Features/Account/NotificationSettingsView.swift`
  - `iosApp/iosApp/Features/Account/ObservableNotificationSettingsViewModel.swift`
  - 通知設定画面への `NavigationStack` 配線

---

## 共通仕様（Android / iOS 両プラットフォームで同一）

> このセクションは `client-bank-14-push-notification-permission-android.md` の同名セクションと **完全に同じ内容** を保つ。仕様変更時は両ファイルを同時更新すること。

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

### D. アプリ内トグルとの連動方針（マスター/サブ階層）

- 「プッシュ通知」を **マスター（OS 許可状態の表示 + アクションボタン）**、着金通知 / 転送通知を **サブトグル** として階層的に表現する。
- マスター部はステータステキスト + 単一のアクションボタンで表現する（**Toggle / Switch は使わない**）。状態に応じてボタンを切り替える:
  - `NotDetermined` → ラベル「許可する」、タップで OS 権限ダイアログを表示
  - `Denied` / `Granted` / `SystemSettingsOnly` → ラベル「OS 設定を開く」、タップで OS 設定アプリ起動
  - 理由: iOS は一度ダイアログ結果が確定すると再表示不可、`Granted` の revoke もアプリから不可。Android も `Granted` の revoke は OS 設定経由のみ。Toggle メタファだと「ON をタップしても OFF にならず設定へ飛ぶ」という挙動と語彙の不整合が出るため、明示的にボタン導線とする。
- **マスター OFF 状態**（`NotDetermined` / `Denied`）ではサブトグルを `enabled = false` / `.disabled(true)` にして操作を受け付けない。サブトグルの永続値そのものは変更しない。
- **マスター ON 状態**（`Granted` / `SystemSettingsOnly`）ではサブトグル（着金 / 転送）を個別に ON/OFF できる。
- **サブトグルの自動上書きはしない**: マスター OFF → ON 遷移時もサブトグルの永続値はユーザーが過去に保存した値を尊重する（旧仕様の「両方 ON 自動上書き」は撤廃）。初回起動時のサブトグル既定値は `NotificationSettingsPreferences` 側のデフォルト（着金 / 転送ともに true）で担保される。
- iOS / Android で同じ階層関係・ステータス + ボタンベースの UI に揃える。

### E. 許可状態の取得タイミング

- 通知設定画面の **初回表示時**: その場で OS API を叩いて取得。
- **バックグラウンド復帰時 / 設定アプリから戻ってきたとき**: 同じく再取得して UI を更新する。
  - Android: `Lifecycle.Event.ON_RESUME` を Composable から観測。
  - iOS: SwiftUI `.task` + `@Environment(\.scenePhase)` の `.active` 復帰を観測。
- ダイアログを閉じた直後のコールバックでも再取得する（即時反映のため）。
- バックグラウンドで定期ポーリングはしない。

### F. エラーハンドリング・ログ出力

- OS API 呼び出し（権限要求 / 設定アプリ起動）が例外を投げた場合は、UI フィードバックは出さずローカルログのみに記録する（MVP として割り切る）。`ACTION_APP_NOTIFICATION_SETTINGS` 失敗時は `ACTION_APPLICATION_DETAILS_SETTINGS` フォールバックで救済し、両方失敗した場合のみ握り潰す。ユーザーは OS の通知センター等から手動到達できるため再試行 UI は設けない。将来 Toast/Snackbar フィードバックを追加する場合は別タスクで対応する。
- 例外スタックは `Log.w` / `os_log` 相当でローカルログに残す。リモートクラッシュ送信は本タスクではしない（送信基盤未整備）。
- ダイアログ呼び出し中に二重タップが起きてもクラッシュしないよう、ボタンは要求中は `disabled` にする。
- ログタグは Android 側 `NotificationPermission`、iOS 側 `NotificationPermission` で揃える（grep しやすさのため）。

---

## 影響範囲（iOS）

- モジュール: `iosApp` のみ（`:shared` には触れない）
  - 変更: `iosApp/iosApp/Features/Account/NotificationSettingsView.swift` — 上部にカード追加
  - 新規: `iosApp/iosApp/Features/Account/Components/NotificationPermissionCard.swift`（仮）
  - 新規: `iosApp/iosApp/Features/Account/NotificationPermissionState.swift`（仮、enum + ヘルパ関数）
  - 変更: `iosApp/iosApp.xcodeproj/project.pbxproj` — 新規 Swift ファイルをプロジェクトに追加
- 破壊的変更: なし。
- 追加依存: なし。`UserNotifications` / `UIKit` は標準フレームワーク。

## 関連ファイル

参考実装:
- `iosApp/iosApp/Features/Account/NotificationSettingsView.swift` — 拡張対象、白角丸カード（`RoundedRectangle(cornerRadius: 20, style: .continuous)` + shadow）の参考
- `iosApp/iosApp/Features/Account/ObservableNotificationSettingsViewModel.swift` — 既存 VM（変更しない）
- `iosApp/iosApp/Features/Account/PrivacySettingsView.swift` — `@Environment(\.scenePhase)` 利用例があれば参考
- 直近コミット `cd17bfc` `21611b3`（client-bank-12/13 パスワード変更画面）— 画面追加の pbxproj 反映パターン

## 実装ステップ

1. **`NotificationPermissionState.swift` 新規作成**
   - 共通仕様 A の状態を `enum NotificationPermissionState { case notDetermined, granted, denied, systemSettingsOnly }` で表現。
   - `func currentNotificationPermissionState() async -> NotificationPermissionState` を実装:
     - `await UNUserNotificationCenter.current().notificationSettings()` の `authorizationStatus` を共通状態にマッピング。
     - `.authorized` / `.provisional` / `.ephemeral` → `.granted`、`.denied` → `.denied`、`.notDetermined` → `.notDetermined`。
     - 将来の `SystemSettingsOnly` ケースは現状 iOS では発生しないため `default → .denied` 扱い（モデルには残す）。
   - `func requestNotificationPermission() async -> NotificationPermissionState` を実装:
     - `try await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound])`。
     - 結果に応じて再度 `currentNotificationPermissionState()` を呼んで返す。
     - 例外時は `os_log` で `NotificationPermission` タグでログ出力（共通仕様 F）し、`.denied` を返すか直前状態を維持。
   - `func openAppNotificationSettings()` を実装:
     - `UIApplication.shared.open(URL(string: UIApplication.openSettingsURLString)!)`。
     - 開けなかった場合の例外ログ。

2. **`NotificationPermissionCard.swift` 新規作成（Toggle UI）**
   - 既存 `NotificationCard` のサブトグル行と同じ見た目（`RoundedRectangle(cornerRadius: 20, style: .continuous)` + `.fill(FujuBankPalette.surface)` + `.shadow(...)`、左に「プッシュ通知」+ サブ説明、右に `Toggle`（ラベル非表示））。
   - `Toggle` の `isOn` バインディングは OS 許可状態を反映: `.granted` / `.systemSettingsOnly` → `true`、`.notDetermined` / `.denied` → `false`。Toggle は受動的なコンポーネントとして扱い、ユーザー操作で `isOn` を直接更新しない（`Binding(get: ..., set: { _ in /* タップアクション */ })` パターン）。
   - Toggle タップ時の挙動を状態で分岐:
     - `.notDetermined`: `Task { state = await requestNotificationPermission() }`
     - `.denied`: `openAppNotificationSettings()`
     - `.granted` / `.systemSettingsOnly`: `openAppNotificationSettings()`（iOS 仕様上アプリから revoke できない）
   - 要求中は `@State var isRequesting: Bool` で Toggle を `.disabled(true)`（共通仕様 F）。
   - SwiftUI Preview で 4 状態を並べる。

3. **`NotificationSettingsView` 拡張（マスター/サブ階層）**
   - `VStack(spacing: 16)` 内、既存 `NotificationCard` の **前** に `NotificationPermissionCard` を挿入。
   - 親 View で以下を保持:
     - `@State private var permissionState: NotificationPermissionState = .notDetermined`
     - `@Environment(\.scenePhase) private var scenePhase`
   - `.task { permissionState = await currentNotificationPermissionState() }` で初回取得。
   - `.onChange(of: scenePhase) { _, newPhase in if newPhase == .active { Task { permissionState = await currentNotificationPermissionState() } } }` で復帰時に再取得（共通仕様 E）。
   - 既存 `NotificationCard` のサブトグル（着金 / 転送）を `.disabled(!isMasterOn)` で OS 許可状態に応じて操作不可にする（`isMasterOn = state == .granted || state == .systemSettingsOnly`）。
   - `permissionState` の `.onChange` で `非ON → ON` 遷移を検知し、`viewModel.setDepositEnabled(true)` / `viewModel.setTransferEnabled(true)` でサブトグル両方を一括 ON に上書きする（権限ダイアログ経由・`scenePhase` 復帰経由のいずれでも）。初回コンポジションで既に ON だった場合は上書きしない。

4. **Xcode プロジェクト反映**
   - 新規 Swift ファイルを `iosApp/iosApp.xcodeproj/project.pbxproj` に追加（既存の Account 配下と同列）。

5. **動作確認** — 「動作確認手順」セクション参照。

6. **PR 作成**
   - ブランチ: `feature/client-bank-15-push-notification-permission-ios`
   - 対象: `main`

## 完了条件

- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が通る（shared 影響なし担保）
- [ ] iOS シミュレータでビルド・起動できる（Xcode `iosApp` スキーム）
- [ ] iOS シミュレータ初回起動: アカウント → 通知設定 → 「許可する」→ システムダイアログ表示 → 許可で `Granted` 表示
- [ ] 拒否で `Denied` 表示 → 「OS 設定で開く」タップで OS 設定が起動 → 戻るとカードが再評価される（`.scenePhase` 復帰）
- [ ] 既存の着金 / 転送トグルがリグレッションなく動作し、再起動後も値が保持される
- [ ] Android 版 (`client-bank-14`) と並行して同じ共通仕様 A〜F を満たしている
- [ ] 共通仕様セクションが `client-bank-14-push-notification-permission-android.md` と完全一致
- [ ] プッシュ通知許可カードが Toggle UI で実装され、許可状態を `isOn` で受動的に反映する
- [ ] マスター OFF → ON 遷移時に着金 / 転送サブトグルが両方自動 ON になる（権限ダイアログ経由・`scenePhase` 復帰経由のいずれでも）
- [ ] マスター OFF 状態（`.notDetermined` / `.denied`）でサブトグルが操作不可（`.disabled(true)`）になる
- [ ] マスター ON 後にサブトグルを個別に ON/OFF できる

## 動作確認手順

1. **iOS シミュレータ・初回起動**: アプリ初回 → アカウント → 通知設定 → 「許可する」タップ → システムダイアログ表示 → 「許可」→ カード `Granted` に切替。
2. **iOS シミュレータ・拒否経路**: アプリ削除 → 再起動 → 「許可する」→ 「許可しない」→ カードが `Denied` に切替 → 「OS 設定で開く」タップ → 設定アプリ起動 → 設定で許可に変更 → アプリに戻ると `.scenePhase = .active` で再取得され `Granted` に切替。
3. **設定アプリで通知オフ → アプリ復帰**: `Granted` 状態でアプリを開いた状態 → 設定アプリで通知をオフ → アプリ復帰 → カードが `Denied` に切替。
4. **既存トグル**: 着金 / 転送トグルが従来通り永続化される（リグレッションなし）。
5. **アプリ内オン × OS 未許可の並列表示**: 着金トグルをオン、OS 許可を `Denied` にして、両方が独立して表示されることを目視確認（共通仕様 D）。

## 想定される懸念・リスク

- **`.notDetermined` → `requestAuthorization` を一度しか呼べない iOS 仕様**: 一度ユーザーが拒否すると `.denied` に固定され、アプリから再ダイアログ表示は不可。共通仕様 C 通り「OS 設定で開く」に切り替えるため UX 的には自然。
- **`.provisional` / `.ephemeral` の扱い**: 本仕様では `.granted` にマッピング。実通知配信時の挙動は今回スコープ外なので、Push 連携時に再検討する。
- **`scenePhase` の `.active` 復帰判定**: シミュレータの Home Indicator スワイプ等で `.inactive` を経由する場合があるが、共通仕様では `.active` 復帰のみで再取得すれば十分。
- **Xcode プロジェクトへのファイル追加**: `pbxproj` を手で編集するか Xcode GUI で追加。直近の client-bank-13 (パスワード変更) と同じパターンを参考にする。
- **Android との仕様乖離リスク**: 共通仕様セクションが両ファイルでドリフトしないよう、本タスクと client-bank-14 のレビューを **必ず同時に** する。

## 関連リンク

- ペアタスク (Android): [`client-bank-14-push-notification-permission-android.md`](./client-bank-14-push-notification-permission-android.md)
- 元タスク（Android、deprecated）: [`client-bank-6-notification-permission-android.md`](./client-bank-6-notification-permission-android.md)
- 前提タスク (Android アカウント設定): [`client-bank-4-account-settings-android.md`](./client-bank-4-account-settings-android.md)
- 前提タスク (iOS アカウント設定): [`client-bank-5-account-settings-ios.md`](./client-bank-5-account-settings-ios.md)
- Apple Developer: [`UNUserNotificationCenter.requestAuthorization`](https://developer.apple.com/documentation/usernotifications/unusernotificationcenter/1649527-requestauthorization)
- Apple Developer: [`UIApplication.openSettingsURLString`](https://developer.apple.com/documentation/uikit/uiapplication/1623042-opensettingsurlstring)

---

## Notion タスク登録用サマリ

- **タイトル**: 銀行アプリクライアント：通知許可（OS 連動）iOS 実装
- **プレフィックス**: client-bank-15
- **ブランチ命名**: `feature/client-bank-15-push-notification-permission-ios`
- **計画書パス**: `docs/tasks/client-bank-15-push-notification-permission-ios.md`
- **依存タスク**: client-bank-5 完了済み
- **ペアタスク**: client-bank-14（Android）
- **PR 構成**: 1 本（iOS のみ。`:shared` 変更なし）
