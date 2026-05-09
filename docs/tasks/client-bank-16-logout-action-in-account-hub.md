# client-bank-16: AccountHub にログアウト導線を追加する

> **優先度**: **最優先（16=最優先）**。MFA リデザイン（`mfa-verify-screen-redesign.md`）が
> 完了次第、これを直近で出して MVP UX を即改善する。
> **依存**: 単体でリリース可能。後続タスク（17 / 18 / 19）はこのタスクで導入される
> ログアウト導線が前提。

## 概要

AccountHub 画面（Figma `697:8394`）の **既存 `SettingsCard` の末尾**（通知 / プライバシー設定 /
パスワード変更の下）に **ログアウト**行を追加し、タップで確認ダイアログを出して
`AuthRepository.logout()` を呼ぶフローを Android / iOS 両プラットフォームに実装する。
logout 完了で `SessionStore.clear()` まで連動し、`SessionState.Unauthenticated` に戻ることで
既存のルートシェル経由で Login 画面へ自動遷移する。

行のラベル色は他の設定項目と同じ通常スタイル（黒テキスト）。**destructive（赤）扱いは
確認ダイアログの「ログアウト」ボタンのみ**に閉じる。新セクション・独立ボタンは作らない。

## 背景・目的

- 現状 fuju-bank-app には **ユーザー操作でログアウトする UI が存在しない**。
  `AuthRepository.logout()` 自体は shared にあるが、UI からは一切呼ばれていない。
- 結果として一度ログインすると、アプリ再インストール / TokenStorage 手動破棄以外で
  セッションを切る手段がない。MFA / 多端末運用 / debug 中のユーザー切替えのいずれにも支障。
- AccountHub 画面に「設定」セクションが既に存在し、「通知 / プライバシー / パスワード変更」
  と並んで配置できる場所がある。今回はその末尾に「ログアウト」行を増設する。
- 本タスク完了後、後続の `client-bank-17/18/19` で「サーバ側 refresh 失敗時の自動 clear」
  「ログアウト時の VM ローカル state 破棄」「access_token expiresAt の proactive 監視」を
  順次積み上げる。

### 既存資産の前提

- `AuthRepository.logout()`（`shared/src/commonMain/.../AuthRepository.kt`）:
  - `authApi.logout()` を叩いて refresh_family revoke を試みる。
  - **サーバが落ちていてもローカル `tokenStorage.clear()` は無条件で実行する**
    （現行コード 88-94 行）。本タスクの「失敗しても黙って Login に戻す」方針は
    この実装にそのまま乗る。
  - 戻り値は `NetworkResult<Unit>`（成功 / Failure / NetworkFailure）。
- `SessionStore.clear()`（`shared/src/commonMain/.../SessionStore.kt`）:
  - `_state.value = SessionState.Unauthenticated` にするだけ。冪等。
- ルートシェル:
  - Android `App.kt` は `sessionState is Authenticated` を見て `RootScaffold()` を出し、
    `Unauthenticated` で `LoginScreen` に戻す。
  - iOS `AppRoot.swift` は `SessionViewModel.state` を `switch` して同等の分岐を持つ。
  - **clear() するだけで両プラットフォームとも自動的に Login 画面へ戻る**ため、
    ナビゲーションを個別に書き直す必要は無い。
- `SettingsRow` / `SettingsCard`（`composeApp/src/androidMain/.../features/account/components/SettingsRow.kt`）:
  - `SettingsRowSpec(label, onClick)` の List を `SettingsCard(rows = ...)` に渡すだけで
    1 枚の白角丸カードに行が並ぶ。本タスクは **末尾に 1 件 spec を追加するだけ**で済む。
  - iOS 側 `SettingsCardView` も `Row(label:action:)` の List を受ける同等構造。

## 影響範囲

- モジュール: `composeApp` / `iosApp`
- ソースセット:
  - `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/account/`
    - `AccountHubScreen.kt`
      - `SettingsCard.rows` 末尾に `SettingsRowSpec(label = "ログアウト", onClick = { ... })` を追加。
      - 確認ダイアログ用の `rememberSaveable<Boolean>` state と `AlertDialog` を追加。
    - `AccountHubViewModel.kt`
      - `AuthRepository` / `SessionStore` をコンストラクタ注入。
      - `logout()` アクション + `isLoggingOut: StateFlow<Boolean>` を追加。
  - `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/shell/RootScaffold.kt`
    - `AccountHubViewModel` 生成箇所に `AuthRepository` / `SessionStore` を注入。
  - `iosApp/iosApp/Features/Account/`
    - `AccountHubView.swift`
      - `SettingsCardView.rows` 末尾に「ログアウト」行を追加。
      - `@State var showLogoutConfirm` + `.confirmationDialog` を追加。
    - `ObservableAccountHubViewModel`（`@Published var isLoggingOut` と `logout()` を追加）。
  - `shared/src/iosMain/kotlin/studio/nxtech/fujubank/session/AuthFlowIos.kt`
    - Swift から呼びやすい `logoutAndClear(authRepository, sessionStore, onResult)` を追加
      （`loginAndProvision` 等と同じパターン）。
- 破壊的変更:
  - `AccountHubViewModel` のコンストラクタに `authRepository: AuthRepository` /
    `sessionStore: SessionStore` を追加する（呼び出し側 `RootScaffold.kt` の DI を更新）。
  - shared ABI（`AuthRepository.logout` / `SessionStore.clear`）は無変更。
    `AuthFlowIos.kt` への関数追加は加算のみで既存 ABI には影響しない。
- 追加依存: なし（既存 `AuthRepository` / `SessionStore` / Material3 `AlertDialog` /
  SwiftUI `confirmationDialog` のみ使用。destructive ボタン色は既存
  `FujuBankColors.Error = Color(0xFFD32F2F)` を流用する）。
- **`SettingsRow` / `SettingsRowView` には destructive variant を追加しない**
  （ログアウト行は通常スタイルで描画する方針が確定したため、共通コンポーネントは無改修）。

## 実装ステップ

### A. 共通方針

1. **logout はサーバ失敗でも黙ってローカル clear する**
   - `AuthRepository.logout()` は既にサーバ失敗時もローカル `tokenStorage.clear()` を
     実行するため、UI 側でエラートースト / エラーダイアログは **一切出さない**。
   - VM 側では `authRepository.logout()` の `NetworkResult` を分岐で握りつぶし
     （`is Success / is Failure / is NetworkFailure` のいずれでも何もしない）、
     最後に必ず `sessionStore.clear()` を呼んで `Unauthenticated` に倒す。
     - 実装の最短形は `authRepository.logout()` の戻り値を捨てて `sessionStore.clear()`
       を呼ぶだけ。`when` で 3 ケース全て no-op にする冗長分岐は不要。
   - ネットワーク失敗時は backend 側 refresh_family が残るが、access_token は短命なので
     実害は限定的。サーバ側 revoke の取りこぼしは `client-bank-17` で別途対応。
2. **logout 中の二度押し防止**
   - VM 内に `isLoggingOut: StateFlow<Boolean>` を持ち、true の間は
     - ログアウト行をタップしてもダイアログを開かない（`if (!isLoggingOut) showLogoutConfirm = true`）
     - ダイアログの「ログアウト」ボタンを `enabled = !isLoggingOut`
   - 行右端に小さな `ProgressView` / `CircularProgressIndicator` を出すかは任意（最小実装ではボタン
     disabled のみで足りる）。
3. **共通 ABI は無変更**
   - shared 側に新公開 API を追加しない。既存 `AuthRepository.logout()` を VM 内で直接呼ぶ。
   - iOS から suspend fun を呼ぶラッパーとして `AuthFlowIos.kt` に
     `logoutAndClear(...)` を追加するが、shared 公開 ABI ではなく Swift から見える
     ヘルパーレベル（`loginAndProvision` 等と同列）。

### B. Android 実装

4. **`AccountHubViewModel.kt` リファクタ**
   ```kotlin
   class AccountHubViewModel(
       private val profileProvider: AccountProfileProvider,
       private val authRepository: AuthRepository,
       private val sessionStore: SessionStore,
   ) : ViewModel() {

       val profile: StateFlow<AccountProfile> = profileProvider.profile

       private val _isLoggingOut = MutableStateFlow(false)
       val isLoggingOut: StateFlow<Boolean> = _isLoggingOut.asStateFlow()

       fun logout() {
           if (_isLoggingOut.value) return
           _isLoggingOut.value = true
           viewModelScope.launch {
               // サーバ呼び出しの結果に関わらず最後に clear する。
               // logout() 自身がサーバ失敗時もローカル tokenStorage.clear() を行うため
               // 戻り値は意図的に無視する（UI へのエラー通知は出さない方針）。
               runCatching { authRepository.logout() }
               sessionStore.clear()
               _isLoggingOut.value = false
           }
       }

       // 既存 updateDisplayName / updateEmail はそのまま
   }
   ```
   - `runCatching` は予期せぬ例外（CancellationException 以外）が握りつぶされないよう、
     `kotlin.coroutines.cancellation.CancellationException` を再 throw する形に整える
     （プロジェクトの他箇所のパターンに合わせる）。

5. **`AccountHubScreen.kt`**
   - 既存 `SettingsCard.rows` 末尾に「ログアウト」を追加し、確認ダイアログ用 state を導入:
     ```kotlin
     var showLogoutConfirm by rememberSaveable { mutableStateOf(false) }
     val isLoggingOut by viewModel.isLoggingOut.collectAsStateWithLifecycle()

     SettingsCard(
         rows = listOf(
             SettingsRowSpec(label = "通知", onClick = onNavigateNotifications),
             SettingsRowSpec(label = "プライバシー設定", onClick = onNavigatePrivacy),
             SettingsRowSpec(label = "パスワード変更", onClick = onNavigatePasswordChange),
             SettingsRowSpec(
                 label = "ログアウト",
                 onClick = { if (!isLoggingOut) showLogoutConfirm = true },
             ),
         ),
     )

     if (showLogoutConfirm) {
         AlertDialog(
             onDismissRequest = { if (!isLoggingOut) showLogoutConfirm = false },
             title = { Text("ログアウトしますか？") },
             text = { Text("再度利用するには再ログインが必要になります。") },
             confirmButton = {
                 TextButton(
                     enabled = !isLoggingOut,
                     onClick = {
                         showLogoutConfirm = false
                         viewModel.logout()
                     },
                 ) {
                     // destructive: 既存 FujuBankColors.Error (= 0xFFD32F2F) を使用。
                     Text("ログアウト", color = FujuBankColors.Error)
                 }
             },
             dismissButton = {
                 TextButton(
                     enabled = !isLoggingOut,
                     onClick = { showLogoutConfirm = false },
                 ) { Text("キャンセル") }
             },
         )
     }
     ```
   - `SessionStore.clear()` で `Unauthenticated` に切り替わると `App.kt` のルート分岐が
     `LoginScreen` を再描画するため、画面遷移コードは AccountHub 側に書かない。
   - **ログアウト行は通常スタイル（黒テキスト + chevron）**。`SettingsRow` への
     destructive variant 追加は行わない。

6. **`RootScaffold.kt` の DI 更新**
   - `AccountHubViewModel` 生成箇所に `authRepository` / `sessionStore` を注入:
     ```kotlin
     AccountHubViewModel(
         profileProvider = KoinPlatform.getKoin().get<AccountProfileProvider>(),
         authRepository = KoinPlatform.getKoin().get<AuthRepository>(),
         sessionStore = KoinPlatform.getKoin().get<SessionStore>(),
     )
     ```

### C. iOS 実装

7. **`shared/src/iosMain/.../AuthFlowIos.kt` に `logoutAndClear` を追加**
   ```kotlin
   /**
    * Swift から `logoutAndClear(...) { ... }` 形で呼ぶための logout ヘルパー。
    *
    * AuthRepository.logout() はサーバ失敗時もローカル tokenStorage.clear() を行うため、
    * 戻り値は意図的に無視する。常に最後に SessionStore.clear() を呼んで Unauthenticated に倒す。
    * UI へのエラー通知は出さない方針（client-bank-16 確定事項）。
    */
   fun logoutAndClear(
       authRepository: AuthRepository,
       sessionStore: SessionStore,
       onComplete: () -> Unit,
   ) {
       sessionStore.scope.launch {
           runCatching { authRepository.logout() }
           sessionStore.clear()
           onComplete()
       }
   }
   ```

8. **`ObservableAccountHubViewModel`（Swift 側）**
   - `@Published var isLoggingOut: Bool = false` を追加。
   - `logout()` アクション:
     ```swift
     func logout() {
         guard !isLoggingOut else { return }
         isLoggingOut = true
         AuthFlowIosKt.logoutAndClear(
             authRepository: KoinIos.shared.authRepository,
             sessionStore: KoinIos.shared.sessionStore,
         ) { [weak self] in
             self?.isLoggingOut = false
         }
     }
     ```
     （Koin 取得経路は既存の他 VM のパターンに合わせる）
9. **`AccountHubView.swift`**
   - 既存 `SettingsCardView.rows` 末尾に「ログアウト」行を追加し、`@State var showLogoutConfirm = false` を導入:
     ```swift
     SettingsCardView(rows: [
         .init(label: "通知") { onSelectDestination(.notifications) },
         .init(label: "プライバシー設定") { onSelectDestination(.privacy) },
         .init(label: "パスワード変更") { onSelectDestination(.passwordChange) },
         .init(label: "ログアウト") {
             if !viewModel.isLoggingOut { showLogoutConfirm = true }
         },
     ])
     .confirmationDialog(
         "ログアウトしますか？",
         isPresented: $showLogoutConfirm,
         titleVisibility: .visible,
     ) {
         // role: .destructive で iOS 標準の赤色になる（自前 color 指定不要）。
         Button("ログアウト", role: .destructive) {
             viewModel.logout()
         }
         .disabled(viewModel.isLoggingOut)
         Button("キャンセル", role: .cancel) { }
     } message: {
         Text("再度利用するには再ログインが必要になります。")
     }
     ```
   - **行は通常スタイル（黒テキスト + chevron）**。`SettingsRowView` への
     destructive variant 追加は行わない。
   - `confirmationDialog` の代わりに `Alert` を使ってもよいが、destructive ロールが
     色まで自動で扱える `confirmationDialog`（または iOS 15+ の `alert(_:isPresented:actions:message:)`）を
     優先する。実装着手時に他画面の確認ダイアログ実装を確認して合わせる。

### D. テスト & 動作確認

10. **shared テスト**
    - `AuthRepository.logout()` の既存テストはそのまま通ることを確認。本タスクでは
      `AuthRepository` 自体に変更を入れない。
    - `AuthFlowIos.logoutAndClear` の単体テストは optional（`AuthFlowIos` の他関数も
      ユニットテストは無いため、整合上はスキップ可）。
11. **手動検証（Android / iOS 両方必須）**
    - ログイン済み状態で AccountHub → 設定セクション末尾に「ログアウト」行があることを確認。
    - 行タップ → 「ログアウトしますか？」ダイアログ表示、本文「再度利用するには再ログインが必要になります。」を確認。
    - 「ログアウト」（destructive 赤）押下で Login 画面に戻ること。
    - 「キャンセル」でダイアログだけ消えること。
    - **サーバ停止 / ネットワーク切断状態**でも、エラートーストやダイアログを出さずに
      Login 画面に戻ること（`adb shell svc wifi disable` / iOS シミュレータの Network Link Conditioner 等）。
    - 行を高速二度押ししても logout 処理が二重起動しないこと（`isLoggingOut` ガード確認）。

## 検証

- [ ] `./gradlew build` が通る
- [ ] `./gradlew :composeApp:assembleDebug` が通る
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が通る
- [ ] `./gradlew :shared:allTests` が通る
- [ ] **Android 実機 / エミュレータ**:
  - [ ] AccountHub の「設定」末尾に「ログアウト」行が**通常スタイル（黒）**で表示される
  - [ ] タップで確認ダイアログ表示、「キャンセル」でダイアログのみ閉じる
  - [ ] 「ログアウト」（赤）で Login 画面に遷移する
  - [ ] ネットワーク切断中でも **エラー通知なしで** Login 画面に戻る
  - [ ] logout 中の二度押しで重複起動しない
- [ ] **iOS シミュレータ**: 上記同等を確認
- [ ] release ビルドで `BANK_API_BASE_URL` を本番に向けても遷移ロジックが破綻しない

## 技術的な補足

### `SessionStore.clear()` だけで Login 画面に戻る理由

- Android `App.kt` 88-99 行目 `showRoot` の判定が
  `sessionState is Authenticated` を含むため、`Unauthenticated` になった瞬間に
  `RootScaffold()` を描画しなくなり `UnauthenticatedRouter` 経由で `LoginScreen` が出る。
- iOS `AppRoot.swift` の `switch session.state` も同様に default 分岐で
  `unauthenticatedRouter` (= LoginView) に入る。
- そのためログアウト処理は「`SessionStore.clear()` を呼ぶ」だけで遷移は完結し、
  AccountHub 側のナビゲーションコードを書く必要はない。

### サーバ logout 失敗を黙殺する方針について

- `AuthRepository.logout()` は `tokenStorage.clear()` を**サーバ成否に関わらず実行**する。
  そのため UI 観点では「ログアウトボタンを押したのにログアウトできない」状態は起きえず、
  エラートーストを出してまで再試行を促す価値が無い。
- サーバ logout が失敗しても、ローカル `access_token` は失効済み扱いになり、
  以降の認証付き API は呼ばれない。
- ただし `HttpCookies` 内の refresh_token cookie は `tokenStorage` 配下ではないため、
  サーバ側で family が revoke されないケースが残る。これは `client-bank-17`（refresh 失敗時の
  自動 clear）でカバーされる予定なので本タスクでは深追いしない。

### destructive ボタンの色について

- Android: 既存の `FujuBankColors.Error = Color(0xFFD32F2F)` をそのまま流用する
  (`composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/theme/FujuBankColors.kt:53`)。
  新たに token を作る必要なし。
- iOS: SwiftUI の `Button(role: .destructive)` が自動で iOS 標準の赤
  （System red, ダーク/ライトでよしなに切り替わる）を当てるため、自前 color 指定不要。

### AccountProfileProvider の扱い

- 現状 `DummyAccountProfileProvider` がプロセス内 in-memory state を持っている。
  ログアウト後にもう一度ログインすると、Provider が同じインスタンスのまま残るため
  以前の編集内容が引き継がれる懸念がある。
- 本タスクでは AccountProfileProvider 自体には触らない。`client-bank-18`（ローカル
  state 破棄）で AccountProfileProvider のリセットも検討する。

## Out of Scope（後続タスクで対応）

1. **Ktor `refreshTokens` 失敗時の自動 clear** → `client-bank-17`
2. **logout / Unauthenticated 遷移時のローカル VM state 破棄** → `client-bank-18`
3. **access_token expiresAt の proactive 監視** → `client-bank-19`
4. **多端末セッション一覧 / 個別セッション revoke UI** — 本 MVP スコープ外
5. **logout 後の「ログアウトしました」トースト表示** — 必要なら次の polish PR で。
   現状は Login 画面へのトランジション自体がフィードバックとして機能する想定。
6. **`SettingsRow` / `SettingsRowView` への destructive variant 追加** — 本タスクで
   ログアウト行は通常スタイルとなり用途が無くなったため、必要が出たら別タスクで検討。

## 未決事項

特記事項なし（着手時点で確認が必要な未決事項は解消済み）。
