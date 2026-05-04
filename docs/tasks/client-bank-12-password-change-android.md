# client-bank-12 パスワード変更画面（Android）

## 概要

アカウントハブ画面の「設定」セクションに「パスワード変更」行を追加し、タップで遷移する `PasswordChangeScreen` を実装する。Figma `799:13327` を踏襲しつつ、ユーザー指示で「現在のパスワード / 新しいパスワード / 新しいパスワード（確認）」の 3 入力欄＋保存ボタン構成にする。バックエンド連携は今回スコープ外で、保存処理は ViewModel 内のダミー（疑似遅延 → 成功扱い）で完結させる。

## 背景・目的

- Figma `697:8394` のハブ更新で「パスワード変更」が設定セクションに追加されたが、実装は未着手。今回はその先頭スコープとして UI のみ Android 先行で組み込む。
- バックエンド API は未提供のため、UI 完成度と画面遷移の組み込みを先に固めて、後続タスクで実 API に差し替えやすい形にしておく。
- 法的文書／プライバシー設定と同じく、長文・キーボードを多用する画面なのでボトムナビは隠す。

## 影響範囲

- モジュール: `:composeApp` のみ（`:shared` には触れない／触る場合は最小限のダミー UseCase 追加に留める）
- ソースセット: `composeApp/src/androidMain`
- 破壊的変更: なし（公開 API / shared framework 影響なし）
- 追加依存: なし（`gradle/libs.versions.toml` 変更不要。既存の Compose Multiplatform / Material3 / Lifecycle のみで実装可能）

## 関連ファイル

参考実装:
- `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/account/AccountHubScreen.kt` — 設定セクションに行追加
- `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/account/PrivacySettingsScreen.kt` — タイトルバー（戻る `<` + 中央タイトル 17sp Bold）の参考
- `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/account/components/AccountInfoEditSheet.kt` — 保存ボタンスタイル（`Button` / 高さ 48dp / `RoundedCornerShape(16.dp)` / `FujuBankColors.BrandPink` / `disabledContainerColor = FujuBankColors.Hairline` / 「保存」ラベル）の参考
- `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/navigation/RootDestination.kt` — ルート定義
- `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/shell/RootScaffold.kt` — 画面ディスパッチ＋ボトムナビ表示制御＋`RootDestinationSaver`
- `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/theme/FujuBankColors.kt` / `FujuBankTypography.kt` / `NotoSansJP`

## 新規 / 変更ファイル一覧

新規:
- `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/account/PasswordChangeScreen.kt`
- `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/account/PasswordChangeViewModel.kt`

変更:
- `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/account/AccountHubScreen.kt`
  - 設定セクションの `SettingsCard` に 3 行目「パスワード変更」を追加。`onNavigatePasswordChange: () -> Unit` を引数に追加。
- `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/navigation/RootDestination.kt`
  - `data object PasswordChange : RootDestination` を追加。
- `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/shell/RootScaffold.kt`
  - `when (destination)` に `PasswordChange` 分岐を追加して `PasswordChangeScreen` をディスパッチ。
  - `showBottomBar` の非表示リストに `PasswordChange` を追加（法的文書と同方針）。
  - `accountFamily` 判定に `PasswordChange` を含めてアカウントタブをアクティブ表示に保つ。
  - `RootDestinationSaver` の `save` / `restore` に `"passwordChange"` キーを追加。
  - `AccountHubScreen` 呼び出しに `onNavigatePasswordChange = { destination = RootDestination.PasswordChange }` を渡す。

## 実装ステップ

1. **`RootDestination` 拡張**
   - `data object PasswordChange : RootDestination` を追加。

2. **`PasswordChangeViewModel` 作成**
   - `androidx.lifecycle.ViewModel` を継承。
   - `StateFlow<PasswordChangeUiState>` を 1 本持つ:
     ```
     data class PasswordChangeUiState(
         val current: String = "",
         val newPassword: String = "",
         val confirm: String = "",
         val isSubmitting: Boolean = false,
         val errorMessage: String? = null,
         val isSubmitted: Boolean = false, // 成功で true → Screen 側が onBack する
     )
     ```
   - 入力ハンドラ: `onCurrentChange` / `onNewChange` / `onConfirmChange`（変更時に `errorMessage` をクリア）。
   - `canSubmit: Boolean` を派生プロパティ or `derivedStateOf` 同等で持つ。条件:
     - 3 欄すべて非空
     - `newPassword != current`
     - `newPassword == confirm`
     - `!isSubmitting`
   - `submit()`: `viewModelScope.launch { isSubmitting=true; delay(800ms 程度); isSubmitting=false; isSubmitted=true }`（ダミー成功）。

3. **`PasswordChangeScreen` 作成**
   - 画面ルート: `Column.fillMaxSize().background(FujuBankColors.Background)`、`imePadding()` を root に付与。
   - **ヘッダー**: `PrivacySettingsScreen` の `Header` と同じ構造（左 48dp の戻るボタン `ic_chevron_left` + 中央 17sp Bold タイトル「パスワード変更」）。`onBack` を受け取る。
   - **本文**: `Column.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)`、`Arrangement.spacedBy(12.dp)`。
   - **入力欄 3 つ** を `OutlinedTextField` で実装:
     - 「現在のパスワード」「新しいパスワード」「新しいパスワード（確認）」
     - `singleLine = true`
     - `visualTransformation = PasswordVisualTransformation()`
     - `keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = IME ボタンは順に Next / Next / Done)`
     - `shape = RoundedCornerShape(16.dp)`、フォーカス色は `FujuBankColors.BrandPink`、フォントは `NotoSansJP`、文字色 `FujuBankColors.TextPrimary`。
     - 高さは `OutlinedTextField` のデフォルトに任せる（Figma の 48dp は label 無しの想定だが、Compose の Outlined は label を浮かせるパターンが標準なので label 表示を採用する。Figma との細かな差は許容）。
   - **エラーメッセージ**: `uiState.errorMessage` が非 null の場合、12sp / `FujuBankColors.Error`（無ければ `Color(0xFFD32F2F)` 等）で表示。今回は表示用の領域だけ用意し、ローカル整合性チェックの結果のみ表示。
   - **保存ボタン**: `AccountInfoEditSheet` と同等（高さ 48dp / `RoundedCornerShape(16.dp)` / `FujuBankColors.BrandPink` / 白文字 / 「保存」ラベル / `disabledContainerColor = FujuBankColors.Hairline` / `disabledContentColor = FujuBankColors.TextTertiary`）。`enabled = uiState.canSubmit` で制御し、`onClick = viewModel::submit`。
   - `LaunchedEffect(uiState.isSubmitted) { if (isSubmitted) onBack() }` で成功時にハブへ戻る（必要なら `Toast`「パスワードを変更しました」を `RootScaffold` 経由で表示。ハブ側の既存 toast 経路は使わず、Screen 内で `LocalContext.current` から直接出すか、`onSuccess` コールバックを引数化して RootScaffold 側で `showToast` を呼ぶ。後者を採用すると `RootScaffold` のパターンと整合的）。

4. **`AccountHubScreen` 改修**
   - `onNavigatePasswordChange: () -> Unit` 引数を追加。
   - 設定セクションの `SettingsCard.rows` に `SettingsRowSpec(label = "パスワード変更", onClick = onNavigatePasswordChange)` を追加（位置は「通知」「プライバシー設定」の下、3 行目）。

5. **`RootScaffold` 改修**
   - `RootDestination.Account` 分岐の `AccountHubScreen` 呼び出しに `onNavigatePasswordChange` を追加。
   - `RootDestination.PasswordChange` 分岐を追加して `PasswordChangeViewModel` を `viewModelFactory` で生成し `PasswordChangeScreen(viewModel, onBack = { destination = RootDestination.Account }, onSuccess = { showToast("パスワードを変更しました") })` をディスパッチ。
   - `showBottomBar` の `false` 分岐に `RootDestination.PasswordChange` を追加。
   - `accountFamily` 判定に `RootDestination.PasswordChange` を追加。
   - `RootDestinationSaver` の save/restore に `"passwordChange"` を追加。

6. **動作確認**
   - エミュレータでハブ → パスワード変更 → 各バリデーションパターン（空欄／新==現／新≠確認／全 OK）→ 保存タップ → 800ms 後にハブへ戻り toast 表示。
   - プロセス再生成（Don't keep activities ON）でハブに戻ること（`PasswordChange` を保存して復元する場合は入力値はあえて保持しない仕様で OK。ハブに戻すのもアリ）。今回はキーを保存して画面自体は復元、`rememberSaveable` の対象は最小限に留める。

## 完了条件

- [ ] アカウントハブ「設定」セクションに「パスワード変更」行が表示される
- [ ] タップで `PasswordChangeScreen` に遷移し、ボトムナビが非表示になる
- [ ] 戻るボタンでハブに戻る（ボトムナビが再表示される）
- [ ] 3 欄空 / 新==現 / 新≠確認 のいずれかで保存ボタンが disabled
- [ ] すべて条件を満たすと保存ボタンが enabled、タップで疑似遅延後にハブへ戻り toast「パスワードを変更しました」が出る
- [ ] `./gradlew :composeApp:assembleDebug` が通る
- [ ] `./gradlew build` が通る

## リスク・注意点

- **iOS 同期**: KMP 規約上 iOS 側も対応必須（ユーザーメモリ「KMP は iOS/Android 両対応必須」）。本タスクは Android 単独だが、対になる `client-bank-13-password-change-ios.md` を別途作成し、両方マージで完了とする運用にする。
- **shared への持ち上げ**: 今回は API 連携が無いため `shared` には載せない。次タスクで実 API を導入する際に、`PasswordChangeUseCase` を `commonMain` に切り出し ViewModel から呼ぶ形へリファクタする想定。今のうちから ViewModel の `submit()` 内ロジックは差し替えやすいよう一箇所にまとめておく。
- **入力値の保持**: `rememberSaveable` でパスワード文字列を `Bundle` に書くのはセキュリティ的に避けたいので、`PasswordChangeViewModel` の `StateFlow` 内に保持し、回転対応は ViewModel のプロセス内保持に任せる（プロセス再生成では消える方針）。
- **Figma との差**: Figma は 2 欄＋保存ボタン無しだが、ユーザー指示で 3 欄＋保存ボタンを採用するため意図的な差分。計画書冒頭に明記。
- **Toast 経路**: `RootScaffold` の `showToast` を流用する形が既存実装と整合的。Screen 引数に `onSuccess: () -> Unit` を生やし、RootScaffold で toast を呼ぶ。

## 技術的な補足

- `OutlinedTextField` の `visualTransformation = PasswordVisualTransformation()` でマスク表示。`KeyboardType.Password` を組み合わせる。
- IME アクション: 1 欄目 Next、2 欄目 Next、3 欄目 Done（Done で `if (canSubmit) submit()` を発火）。
- スクロールとキーボードの干渉: root に `imePadding()` を付与し、`Column.verticalScroll` で十分。`AccountInfoEditSheet` と同様の方針。
- 色: パスワード可視化トグル（目アイコン）は今回スコープ外。Figma にも無い。
