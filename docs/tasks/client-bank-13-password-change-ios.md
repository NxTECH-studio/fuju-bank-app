# client-bank-13 パスワード変更画面（iOS）

## 概要

`AccountHubView` の「設定」セクションに「パスワード変更」行を追加し、タップで遷移する `PasswordChangeView`（SwiftUI）を実装する。Android (`client-bank-12`) と 1:1 のレイアウトと挙動を保ち、3 入力欄（現在 / 新規 / 確認）＋保存ボタン構成にする。バックエンド連携は今回スコープ外で、`ObservablePasswordChangeViewModel` 内のダミー（疑似遅延 → 成功扱い）で完結。

## 背景・目的

- KMP 規約上、Android で実装した画面は iOS でも同等に提供する必要がある（ユーザーメモリ「KMP は iOS/Android 両対応必須」）。
- 法的文書／プライバシー設定で確立した「タブバーを隠す + 自前ボトムバーを隠す」の合わせ技を、本画面でも踏襲する（直近コミット `a0a07df` `a7a2695` `563d06d` と同方針）。

## 影響範囲

- モジュール: `iosApp` のみ（`:shared` には触れない）
- 破壊的変更: なし
- 追加依存: なし

## 関連ファイル

参考実装:
- `iosApp/iosApp/Features/Account/AccountHubView.swift` — 設定セクションに行追加、`AccountDestination` に case 追加
- `iosApp/iosApp/Features/Account/PrivacySettingsView.swift` — ヘッダー（戻る `<` + 中央タイトル 17pt Bold）の参考
- `iosApp/iosApp/Features/Account/ObservablePrivacySettingsViewModel.swift` — `ObservableObject` 派生 VM の作り
- `iosApp/iosApp/Features/Account/Components/` — `SettingsCardView` / `SettingsRowView` / `AccountInfoEditSheetView` の保存ボタンスタイル
- 直近コミット `a0a07df` / `a7a2695` / `563d06d` — 法的文書／プライバシー設定でのタブバー＋自前ボトムバー非表示パターン

## 新規 / 変更ファイル一覧

新規:
- `iosApp/iosApp/Features/Account/PasswordChangeView.swift`
- `iosApp/iosApp/Features/Account/ObservablePasswordChangeViewModel.swift`

変更:
- `iosApp/iosApp/Features/Account/AccountHubView.swift`
  - 設定セクション `SettingsCardView` に「パスワード変更」行を追加（`onSelectDestination(.passwordChange)`）。
  - `AccountDestination` に `case passwordChange` を追加。
- `RootTabView`（または iOS 側のナビゲーションを束ねている画面 / アカウント `NavigationStack` の `navigationDestination(for: AccountDestination.self)` がある場所）
  - `.passwordChange` 分岐で `PasswordChangeView` を表示。
  - 法的文書／プライバシー設定と同じパターンで、表示時に `.toolbar(.hidden, for: .tabBar)` を適用し、自前ボトムバーがあれば非表示にする。
  - 実ファイル名・配置は `iosApp/iosApp/` を確認した上で、既存 `.privacyPolicy` / `.termsOfService` の分岐に倣う。
- `iosApp/iosApp.xcodeproj/project.pbxproj`
  - 新規 Swift ファイルを Xcode プロジェクトに追加（参照を pbxproj に書き込む）。

## 実装ステップ

1. **`ObservablePasswordChangeViewModel.swift` 作成**
   - `final class ObservablePasswordChangeViewModel: ObservableObject` で実装。
   - `@Published var current: String = ""`、`@Published var newPassword: String = ""`、`@Published var confirm: String = ""`
   - `@Published var isSubmitting: Bool = false`
   - `@Published var isSubmitted: Bool = false`
   - `var canSubmit: Bool { !current.isEmpty && !newPassword.isEmpty && !confirm.isEmpty && newPassword != current && newPassword == confirm && !isSubmitting }`
   - `func submit()`:
     ```
     guard canSubmit else { return }
     isSubmitting = true
     Task { @MainActor in
         try? await Task.sleep(nanoseconds: 800_000_000)
         isSubmitting = false
         isSubmitted = true
     }
     ```

2. **`PasswordChangeView.swift` 作成**
   - `struct PasswordChangeView: View`、`@StateObject private var viewModel = ObservablePasswordChangeViewModel()`、`@Environment(\.dismiss) private var dismiss`、`let onSuccess: () -> Void`（成功 toast 等を親で扱う場合）。
   - 構造:
     ```
     VStack(spacing: 0) {
         header  // PrivacySettingsView と同じヘッダー（戻る + 「パスワード変更」）
         ScrollView {
             VStack(alignment: .leading, spacing: 12) {
                 secureField(title: "現在のパスワード", text: $viewModel.current, contentType: .password)
                 secureField(title: "新しいパスワード", text: $viewModel.newPassword, contentType: .newPassword)
                 secureField(title: "新しいパスワード（確認）", text: $viewModel.confirm, contentType: .newPassword)
                 saveButton  // AccountInfoEditSheetView の保存ボタンと同スタイル
             }
             .padding(.horizontal, 16)
             .padding(.vertical, 16)
         }
         .scrollIndicators(.hidden)
     }
     .frame(maxWidth: .infinity, maxHeight: .infinity)
     .background(FujuBankPalette.background.ignoresSafeArea())
     .navigationBarHidden(true)
     .toolbar(.hidden, for: .tabBar)  // 法的文書と同方針
     .onChange(of: viewModel.isSubmitted) { _, newValue in
         if newValue {
             onSuccess()
             dismiss()
         }
     }
     ```
   - `secureField`: `SecureField` を `RoundedRectangle(cornerRadius: 16)` 背景＋プレースホルダ色 `#dadbdf` で囲った再利用可能なヘルパ View。フォーカス時のボーダー色は `FujuBankPalette.brandPink`。
   - `saveButton`: 高さ 48、`RoundedRectangle(cornerRadius: 16)` 背景、`FujuBankPalette.brandPink`、文字白「保存」、`disabled(!viewModel.canSubmit)` 時は `FujuBankPalette.hairline` 背景。`AccountInfoEditSheetView` のボタン実装をコピー＆Adapt。
   - エラーメッセージ表示用の `Text` を 1 行設けてもよい（任意。MVP は省略可）。

3. **`AccountHubView.swift` 改修**
   - 設定セクションの `SettingsCardView` 行配列に `.init(label: "パスワード変更") { onSelectDestination(.passwordChange) }` を追加。
   - `enum AccountDestination` に `case passwordChange` を追加。

4. **アカウント `NavigationStack` ホストに分岐追加**
   - `RootTabView`（または該当ファイル）の `navigationDestination(for: AccountDestination.self)` に `.passwordChange` ケースを追加し、`PasswordChangeView(onSuccess: { ... })` を返す。
   - `onSuccess` で既存の toast ／ snackbar 経路があれば「パスワードを変更しました」を出す。無ければ MVP では何もしないで OK（dismiss のみ）。
   - 自前ボトムバーを敷いている場合は法的文書と同じ条件式に `.passwordChange` を追加して非表示にする。

5. **Xcode プロジェクト登録**
   - `iosApp.xcodeproj` に新規 2 ファイルを追加。`pbxproj` の `PBXFileReference` / `PBXBuildFile` / `PBXGroup` の 3 箇所を更新（既存ファイル追加コミットの diff を参考にする）。

6. **動作確認**
   - iOS Simulator (iPhone) でハブ → パスワード変更 → タブバーが消えること、戻る `<` でハブに戻りタブバー復帰。
   - 3 欄空 / 新==現 / 新≠確認 で保存無効、すべて満たすと有効、保存タップで疑似遅延後 dismiss + toast。

## 完了条件

- [ ] アカウントハブ「設定」セクションに「パスワード変更」行が出る
- [ ] タップで `PasswordChangeView` に遷移し、タブバー＋自前ボトムバーが非表示になる
- [ ] 戻るで `AccountHubView` に戻り、タブバーが復帰する
- [ ] バリデーション条件を満たさない間は保存ボタン disabled、満たすと enabled
- [ ] 保存タップで疑似遅延後に dismiss、toast（経路がある場合）
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が通る（shared 影響無いが念のため）
- [ ] Xcode で `iosApp` スキームを iPhone Simulator 向けにビルドして起動できる

## リスク・注意点

- **タブバー非表示の重複**: SwiftUI の `.toolbar(.hidden, for: .tabBar)` と自前ボトムバーの両方を扱っている過去の試行があるため、直近 3 コミット (`563d06d` / `a7a2695` / `a0a07df`) を必ず読み、同じパターンを踏襲する。試行錯誤の履歴は残す方針なので、もし途中の差し戻しがあっても PR/マージで残す。
- **`SecureField` の挙動**: iOS の `SecureField` は paste 時に文字数制限が独特なので、ペーストでも正常に値が入ることを simulator で確認する。
- **`onChange` の signature**: iOS 17+ では 2 引数版、それ以前は 1 引数版。プロジェクトの Deployment Target に合わせる。
- **shared への持ち上げ**: client-bank-12 と同じく今回は shared を触らない。実 API 接続時に `commonMain` で UseCase を共通化する。
- **Android と 1:1 を維持**: タイトル文言・行ラベル・ボタンラベル・バリデーションロジックは Android (`client-bank-12`) と完全一致させる。

## 技術的な補足

- 入力値はメモリ上の `@Published` のみで保持し、`@SceneStorage` には書かない（パスワードを永続化しない）。
- `submit()` の疑似遅延は `Task.sleep` ベースで OK（`@MainActor` 上で動かす）。
- `AccountHubView` の遷移は既存パターン（`onSelectDestination` コールバック → 親 `NavigationStack` の `navigationDestination(for:)`) に乗せ、`NavigationLink(value:)` を行内 `Button` と二重にしないこと（既存コメント参照）。
