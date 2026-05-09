# client-bank-18: logout / Unauthenticated 遷移時のローカル state を破棄する

> **優先度**: **中（18=中）**
> **依存**: `client-bank-16` または `client-bank-17` のいずれかが完了していれば動かせる。
> どちらも未完だと「`Unauthenticated` 遷移が発生しない」ため挙動を確認できない。
> 推奨は 16 → 17 → 18 の順。
> **後続**: `client-bank-19`（proactive 監視）はこの reset 経路に依存して
> 「expire 検知 → clear → 全 VM reset」のフローが成立することを前提にする。

## 概要

`SessionStore.state` が `Authenticated → Unauthenticated` に遷移した瞬間に、各画面の
`ViewModel` が抱えるローカルキャッシュ（残高 / 取引履歴 / 通知設定 / プロフィール編集状態など）を
破棄して、**次回ログイン時に前ユーザーの情報が混ざらない**よう保証する。

## 背景・目的

- 現状、`SessionStore.clear()` で `Unauthenticated` に切り替わっても、
  以下の VM / Provider はインスタンスが残るかキャッシュが残るため、再ログイン時に
  前ユーザーのデータが瞬間的に表示されうる:
  - `HomeViewModel`: `HomeUiState.Loaded(profile = ...)` の最後の値
  - `TransactionListViewModel`: `TransactionListUiState.Loaded(items = ...)`
  - `TransactionDetailViewModel`: `Transaction` を引数で受け取る形なので画面引数次第
  - `AccountHubViewModel`: `AccountProfileProvider`（**Koin singleton**）の
    in-memory state が完全に残る
  - `DummyAccountProfileProvider`: 編集された displayName / email がプロセス内に永続
  - `NotificationSettingsViewModel` / `PrivacySettingsViewModel`: prefs は残ってよいが、
    UI 上の編集中状態が混ざる懸念
- AccountHub に至っては、Provider が同一インスタンスのため **別ユーザーでログインしても
  前ユーザーの編集名が表示される**深刻な問題が出る可能性がある。
- 本タスクで「Unauthenticated 遷移時に各 VM / Provider が自身を reset する」契約を整え、
  `client-bank-16/17` で導入された clear 経路全てから安全に再ログインできる状態にする。

### 既存資産の前提

- `SessionStore.state: StateFlow<SessionState>` を全 VM が collect 可能。
- `SessionStoreIos.observeSession(...)`: iOS から StateFlow を観測するための既存ブリッジ。
- Android の `RootScaffold.kt` は `viewModel(...)` で各画面遷移時に VM を生成する。
  ホーム / 履歴 / 詳細は **destination 切替で VM の lifecycle が切れる** ため、
  そもそも `Unauthenticated` 遷移後に再ログインしても VM 自体は新規生成される
  （ただし `AccountProfileProvider` のような Koin singleton は別問題）。
- iOS の `RootTabView.swift` / 各 View は `@StateObject` で VM を保持するため、
  RootTabView 自体が unmount されない限り VM は生き続ける。
  `AppRoot.swift` が `Unauthenticated` で `RootTabView` を unmount するため、
  iOS でも基本的に VM は自然破棄される（要確認）。

→ 結局のところ、**「VM のライフサイクルがルート画面 unmount に紐付いて切れる」**
ケースと、**「Koin singleton で残る Provider / Repository キャッシュ」**ケースの 2 軸で
対応を考える必要がある。

## 影響範囲

- モジュール: `composeApp` / `iosApp` / `shared`
- ソースセット:
  - `shared/src/commonMain/kotlin/studio/nxtech/fujubank/account/AccountProfileProvider.kt`
    （`reset()` メソッド追加 or `DummyAccountProfileProvider` を Unauthenticated 観測で初期化）
  - `shared/src/commonMain/kotlin/studio/nxtech/fujubank/session/SessionResetCoordinator.kt`
    (新規 / 任意): `SessionStore.state` を観測して Unauthenticated 遷移時に各 Provider の
    reset を呼ぶ調停役。
  - `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/home/HomeViewModel.kt`
  - `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/transactions/TransactionListViewModel.kt`
  - （iOS 側）`iosApp/iosApp/Features/Home/...` / `iosApp/iosApp/Features/Transactions/...`
    の対応する VM
- 破壊的変更:
  - `AccountProfileProvider` インターフェースに `reset()` を追加する場合は実装側全てに
    影響。`DummyAccountProfileProvider` のみ実装している現状なら影響範囲は閉じる。
  - VM 側の collect 追加は public API 変更を伴わない（コンストラクタに SessionStore が
    既に注入されている VM が多いので、そうでない VM のみ DI 更新）。
- 追加依存: なし

## 実装ステップ

### A. 共通方針 — pull 方式 vs push 方式

1. **採用案: 「pull + コーディネーター」のハイブリッド**
   - **pull 方式（VM が SessionStore.state を collect）**:
     - 各 VM が `init { sessionStore.state.onEach { if (it is Unauthenticated) reset() } }`。
     - メリット: VM 単位で reset 内容を完全コントロールできる。Android では
       `viewModelScope`、iOS では `init` 内タスク + deinit でキャンセル。
     - デメリット: 全 VM に同じボイラープレートが入る。
   - **push 方式（SessionStore に listener を持たせる）**:
     - SessionStore に `onClearListeners: List<() -> Unit>` を持たせ、`clear()` で発火。
     - メリット: VM 側ボイラープレートが減る。
     - デメリット: SessionStore の責務が広がる。テストもしにくい。
   - **コーディネーター方式（採用）**:
     - 新規 `SessionResetCoordinator(sessionStore, accountProfileProvider, ...)` を Koin で
       singleton 登録し、`init` で `sessionStore.state` を collect。
     - Unauthenticated 遷移を検知したら inject した Provider 群の `reset()` を呼ぶ。
     - VM 側は **VM ライフサイクルが切れることに依存して自然破棄**にする。
       Provider 系 singleton のみ Coordinator 経由で reset。
     - メリット: VM 側に手を入れる必要がほぼ無い。Provider に閉じる。
     - デメリット: Coordinator が起動されないと動かない（DI 起動時に touch する必要あり）。

2. **対象を「VM 内 state」と「Koin singleton」に分類**
   - **VM 内 state**:
     - Android: `RootScaffold` の destination 切替 + `Unauthenticated` 遷移で
       `RootScaffold` 自体が unmount されるため自然破棄。**追加対応不要**の見込み。
       → 「念のため」Home / TransactionList の `init { sessionStore.state.onEach { ... } }`
       で防御的に reset するかは未決事項。
     - iOS: `AppRoot` で `Authenticated` 分岐を抜ける時に `RootTabView` が unmount されるため
       同上。SwiftUI 構造によっては `@StateObject` が破棄されない事もあるため要動作確認。
   - **Koin singleton (要明示 reset)**:
     - `AccountProfileProvider` (現 `DummyAccountProfileProvider`)
     - `NotificationSettingsPreferences` / `PrivacyPreferences`（永続化系。reset 要否は要相談）
     - `SignupCompletionSignal` / `SignupWelcomePreferences`（signup 用 oneshot。
       reset 不要だが Authenticated 遷移後に消費されることを再確認）
     - `SessionStore.bootstrapStarted`: bootstrap 完了済みフラグ。logout 後に再ログインしても
       bootstrap は再走させない設計なので reset 不要（要確認）。

### B. 共通実装 (shared)

3. **`AccountProfileProvider.reset()` を追加**
   ```kotlin
   interface AccountProfileProvider {
       val profile: StateFlow<AccountProfile>
       fun updateProfile(displayName: String, email: String)
       fun reset()  // 追加
   }

   class DummyAccountProfileProvider : AccountProfileProvider {
       // ...
       override fun reset() {
           _profile.value = AccountProfile(
               displayName = "山田 花子",  // ダミー初期値に戻す
               email = "hanako@example.com",
               accountId = "1293031294904",
           )
       }
   }
   ```
   - 実 API 連携時の `RemoteAccountProfileProvider` 実装でも `reset()` で
     キャッシュをクリアする想定。

4. **`SessionResetCoordinator.kt` 新規（commonMain）**
   ```kotlin
   class SessionResetCoordinator(
       private val sessionStore: SessionStore,
       private val accountProfileProvider: AccountProfileProvider,
       // 必要に応じて他 Provider を追加
   ) {
       fun start() {
           sessionStore.scope.launch {
               // 直前の state を覚えておき、Authenticated → Unauthenticated 遷移を検知
               var previous: SessionState = sessionStore.current
               sessionStore.state.collect { next ->
                   val justLoggedOut =
                       previous is SessionState.Authenticated &&
                           next is SessionState.Unauthenticated
                   if (justLoggedOut) {
                       accountProfileProvider.reset()
                       // 他の Provider reset もここに追加
                   }
                   previous = next
               }
           }
       }
   }
   ```
   - `start()` を Android `App.kt` / iOS `AppRoot.swift` のアプリ起動経路で 1 回だけ呼ぶ。
     起動箇所は `Koin.start(...)` 直後 / `bootstrap()` の前で呼べば、初回起動時の
     `Unauthenticated` 初期値では何もせず、以降の遷移を捕捉できる。
   - `previous` 比較を入れる理由: アプリ起動直後の `Unauthenticated` を「ログアウト」と
     誤認しないため。

5. **`sessionModule` (Koin) に登録**
   - `single { SessionResetCoordinator(get(), get()) }` を追加。
   - DI モジュール構成を確認し、`sessionModule` 等が無ければ既存 `authModule` /
     `accountModule` のいずれかに置く。

### C. Android 実装

6. **`App.kt` の起動経路で Coordinator.start() を呼ぶ**
   ```kotlin
   @Composable
   @Preview
   fun App() {
       val koin = remember { KoinPlatform.getKoin() }
       val sessionStore = remember { koin.get<SessionStore>() }
       val resetCoordinator = remember { koin.get<SessionResetCoordinator>() }
       // 起動時に 1 回だけ start。Composable の再 composition では再起動されない。
       LaunchedEffect(Unit) { resetCoordinator.start() }
       // ...
   }
   ```

7. **VM 単位での補強（要否は未決事項）**
   - `HomeViewModel` / `TransactionListViewModel` 等で
     `init { viewModelScope.launch { sessionStore.state.collect { ... } } }` を入れて
     念のため `_state.value = Loading` に戻す案。
   - 必要性は「RootScaffold unmount 時に VM が破棄される確証があるか」次第。
     Compose の `viewModel(...)` API は `RootScaffold` が unmount されると ViewModelStore も
     破棄されるため、原則不要。
   - **本タスクではスキップ**を推奨。Coordinator + Provider reset で十分。

### D. iOS 実装

8. **`AppRoot.swift` (もしくは `iosApp.swift` の起動箇所) で Coordinator.start() を呼ぶ**
   ```swift
   init() {
       let coordinator = KoinIosKt.sessionResetCoordinator()
       coordinator.start()
   }
   ```
   - もしくは `SessionViewModel.init` 内で 1 回だけ start。
   - shared 側で `KoinIosKt` に取得関数を生やしておく
     (`fun sessionResetCoordinator(): SessionResetCoordinator = ...`)。

9. **VM 単位の補強**
   - Android 同様、`@StateObject` が `RootTabView` unmount で破棄されることを確認。
   - SwiftUI の `RootTabView` は `AppRoot` の `if Authenticated { RootTabView() }` 配下なので
     `Unauthenticated` 遷移で自然 unmount され、`@StateObject` は破棄される想定。
     念のため SwiftUI Inspector or print 文で確認。

### E. テスト

10. **`SessionResetCoordinatorTest.kt` (新規 commonTest)**
    - fake `AccountProfileProvider` を用意して `reset()` 呼び出し回数を検証。
    - `sessionStore.setAuthenticated("u1") → setUnauthenticated`（実 API では `clear()`）の
      遷移で 1 回だけ reset が呼ばれる。
    - `Unauthenticated → Unauthenticated`（初期状態）では reset が呼ばれない。
    - `Unauthenticated → MfaPending` では reset が呼ばれない。
    - `Authenticated → MfaPending`（ありえないが念のため）では呼ばれない。

11. **`AccountProfileProviderTest.kt`** (新規) — `reset()` で初期値に戻ること。

## 検証

- [ ] `./gradlew build` が通る
- [ ] `./gradlew :shared:allTests` が通る（新規テスト含む）
- [ ] `./gradlew :composeApp:assembleDebug` が通る
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が通る
- [ ] **Android 実機**:
  - [ ] User A でログイン → AccountHub で displayName を変更（編集 UI 復活時。MVP 中は手動 set で確認）
  - [ ] ログアウト → User B でログイン → AccountHub に User A の displayName が出ない
  - [ ] ホーム残高 / 取引履歴に User A のキャッシュが瞬間的にも出ない
  - [ ] `client-bank-17` の自動 clear 経路でも同じ動作
- [ ] **iOS シミュレータ**: 上記同等を確認

## 技術的な補足

### Coordinator の起動タイミング

- アプリのプロセスごとに 1 回だけ `start()` を呼ぶ。
- Compose の `LaunchedEffect(Unit)` / SwiftUI の `init` で呼ぶと再 composition / 再 init で
  複数回起動する懸念がある。
- 対策: Coordinator 内で `private var started = false` を持ち、二重起動を防ぐ。
  もしくは Koin `single` の生成タイミングで `start()` を init に組み込み、
  Koin 取得時に自動起動させる（呼び出し側ボイラープレート削減）。

### `SessionStore.scope` を借りる理由

- Coordinator 専用に独自 scope を作る必要は無い。SessionStore.scope は
  `SupervisorJob() + Dispatchers.Main` でアプリ全体が生きている間有効なため、
  そのまま使うのが省コスト。

### Koin singleton の Provider に reset を強制する設計判断

- インターフェースに `reset()` を生やすか、SessionResetCoordinator 側で
  「ダウンキャストしてリセット」するかの 2 択。
- 前者（インターフェースに reset）はテストしやすく Provider 実装に契約を強制できる。
- 後者は Coordinator 側で実装毎に対応が必要で密結合になる。
- → **前者を採用**（実装ステップ B-3）。

### bootstrap との競合

- `SessionStore.bootstrap()` が複数回呼ばれない設計（`bootstrapStarted` フラグ）になっている
  ため、ログアウト後の再ログインで bootstrap が再走することはない。
- 再ログインフローは `loginAndProvision` 経由で `setAuthenticated` を直接呼ぶため
  bootstrap 抜きで成立する。

## Out of Scope（後続タスクで対応）

1. **toast / snackbar での「セッションが切れました」表示** — UI 通知は別タスクで。
   shared 側に "last clear reason" を持たせる必要があるため設計余地あり。
2. **永続化された preferences (NotificationSettings / Privacy) の reset** — 永続化系は
   ユーザー横断で残しても問題ない想定。要 user 確認。
3. **proactive expiry 監視** → `client-bank-19`
4. **多端末セッション同期 / リモートからの強制 logout 受信** — MVP 外。

## 未決事項

- **対象 VM の網羅範囲**: HomeViewModel / TransactionListViewModel / TransactionDetailViewModel /
  AccountHubViewModel / WelcomeGate / NotificationSettings / PrivacySettings / PasswordChange の
  どこまで明示的に reset するか。デフォルトは「Coordinator 経由で Provider のみ reset、
  VM はライフサイクル破棄に任せる」。ライフサイクル破棄が確実でない VM があれば追加対応。
- **pull 方式 vs push 方式**: 採用案は「Coordinator 経由のハイブリッド」だが、
  各 VM 内に `sessionStore.state.collect` を直接書く pull 方式に倒すかは要相談。
  Provider まで含めて Coordinator に集約する方が見通しが良い見込み。
- **`AccountProfileProvider.reset()` の振る舞い**: ダミー初期値（山田花子）に戻すか、
  完全空 (`AccountProfile.empty`) にするか。実 API 連携時には「自分の userId が無い時は空」が
  自然なので、`empty()` factory を生やす案も検討。
- **永続化 preferences (Notification / Privacy) を reset するか**: ユーザー個別の
  通知 ON/OFF やプライバシー設定はログアウトで消すべきか維持すべきか。
  → 通常は「端末側設定」として維持する。一旦 **reset しない** 方針で進める。
- **VM 内防御的 reset の要否**: Compose / SwiftUI のライフサイクル破棄に
  100% 任せて良いか。実機で確認した上で必要なら個別 VM に collect を入れる。
