# A2d: 既存「ようこそ」画面をサインアップ動線へ転用（Welcome rewire）

## メタ情報

- **Phase**: 1
- **並行起動**: A2f（サインアップ画面本体）と並行で進めるが、**A2d は A2f と同時マージ前提**。A2d 単体ではユーザー導線に到達できない
- **依存**:
  - A2b（`SessionStore.state` / `SessionState.Authenticated` 遷移が確立済み）
  - A2c（旧 `a2c-splash-welcome.md` の Welcome シーケンス案を本タスクへ引き継ぎ）
- **同期点**: A2f の「サインアップ完了 → SessionState.Authenticated 遷移」フックが確定するタイミング
- **スコープ外**:
  - A2f 本体（サインアップ画面のフォーム / バリデーション / API 呼び出し）の実装
  - A3（ホーム画面）本体の実装
  - 既存ログインフロー（A2b）の挙動変更

## 概要

Figma で「ようこそ」画面として用意されているコンポーネントを、**サインアップ完了直後にだけ 1 度表示される画面**として配線する。ログイン経由の `Authenticated` 遷移では表示しない。表示済みフラグを永続化し、再ログイン・再起動・複数端末いずれの経路でも同一ユーザーには 2 度表示されないようにする。

## 背景・目的

- 旧 `a2c-splash-welcome.md` で「初回ログイン直後の Welcome 演出」として検討されていたものを A2c がスコープから外したため、Welcome 表示の責務をどこに置くかが宙に浮いている。
- A2f のサインアップ画面が単体でマージされると「サインアップ成功 → いきなり Home」になり、Figma 通りの『ようこそ』導線が抜け落ちる。
- ログイン経由でも毎回 Welcome を出すのは UX 的に過剰なので、**「サインアップ画面発で Authenticated になった 1 回のみ」** という発火条件を明確に設計する必要がある。

## 表示条件（発火ロジック）

Welcome を表示する条件は次の AND：

1. `SessionState` が `Unauthenticated | MfaPending` から **`Authenticated` に遷移した瞬間**であること
2. その遷移の **トリガが A2f のサインアップ画面**であること（≠ ログイン画面 / ≠ bootstrap によるセッション復元）
3. 永続フラグ `signup_completed == false` であること（= このユーザー視点で初回サインアップ）

3 の判定後、Welcome 表示が完了したタイミングで `signup_completed = true` を書き込み、以降は表示されないようにする。

```
[サインアップ画面 (A2f)]
        │  認証成功
        │  pendingSignupWelcome = true ← ワンショットフラグ
        ▼
SessionState: Unauthenticated → Authenticated
        │
        ▼
ルート分岐:
  if pendingSignupWelcome && !signup_completed
      → WelcomeScreen
        │ 表示完了 (CTA 押下 or 自動遷移)
        │ signup_completed = true
        │ pendingSignupWelcome = false
        ▼
      Home (A3)
  else
      → Home (A3)
```

## 影響範囲

- モジュール: `composeApp/`, `shared/`, `iosApp/`
- ソースセット:
  - `shared/src/commonMain/...`:
    - `signup/SignupWelcomePreferences.kt` 新規（`signup_completed` の読み書き）
    - `signup/SignupCompletionSignal.kt` 新規（`pendingSignupWelcome` ワンショット）
    - `di/SignupModule.kt` 新規 or 既存 `SessionModule` に相乗り
  - `shared/src/commonTest/...`:
    - `signup/SignupWelcomePreferencesTest.kt` 新規（fake `Settings` 注入）
  - `composeApp/src/commonMain/...`:
    - 既存「ようこそ」Composable があれば**書き換え**（無ければ新規 `features/welcome/WelcomeScreen.kt`）
    - ルート分岐（`App.kt` 等）に Welcome ステップを 1 段挟む
  - `iosApp/iosApp/...`:
    - 既存「ようこそ」SwiftUI View があれば**書き換え**（無ければ新規 `Features/Welcome/WelcomeView.swift`）
    - `AppRoot.swift` のセッション分岐に Welcome ステップを 1 段挟む
    - `AuthFlowIos` に `pendingSignupWelcome` 観測のブリッジを追加
- 破壊的変更: なし（公開 API は追加のみ）
- 追加依存:
  - `gradle/libs.versions.toml` に `multiplatform-settings-no-arg` を新規追加
    ```toml
    [versions]
    multiplatform-settings = "1.1.1"  # 着手時点の最新を採用

    [libraries]
    multiplatform-settings-no-arg = { group = "com.russhwolf", name = "multiplatform-settings-no-arg", version.ref = "multiplatform-settings" }
    ```
  - `shared/build.gradle.kts` の `commonMain` 依存に `libs.multiplatform.settings.no.arg`

## 設計詳細（インタフェース定義レベル）

### 1. `SignupWelcomePreferences`（永続フラグ）

`SessionStore` の `MutableStateFlow + asStateFlow()` パターンに揃える。`Settings` を DI で受け取り、`commonTest` では fake を注入できるよう構造を分ける。

```kotlin
// shared/src/commonMain/kotlin/studio/nxtech/fujubank/signup/SignupWelcomePreferences.kt
class SignupWelcomePreferences(private val settings: Settings) {
    private val _signupCompleted = MutableStateFlow(settings.getBoolean(KEY, false))
    val signupCompleted: StateFlow<Boolean> = _signupCompleted.asStateFlow()

    fun markCompleted() {
        settings.putBoolean(KEY, true)
        _signupCompleted.value = true
    }

    /** 開発時 / テスト時のリセット用。プロダクションコードからは呼ばない。 */
    fun resetForDebug() {
        settings.remove(KEY)
        _signupCompleted.value = false
    }

    private companion object { const val KEY = "signup_completed" }
}
```

- キー名: **`signup_completed`**（Boolean）
- 値の意味: 「このアプリインストール内で 1 度でも Welcome が表示完了したか」
- 多端末同期は対象外（端末ごとに 1 度ずつ表示される仕様）

### 2. `SignupCompletionSignal`（ワンショットフラグ）

Welcome を「サインアップ画面発の Authenticated 遷移」だけに限定するためのプロセス内シグナル。永続化しない。

```kotlin
// shared/src/commonMain/kotlin/studio/nxtech/fujubank/signup/SignupCompletionSignal.kt
class SignupCompletionSignal {
    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    /** A2f 側で「サインアップ API 成功 → SessionStore.setAuthenticated」の直前に呼ぶ。 */
    fun arm() { _pending.value = true }

    /** Welcome 表示が完了したら呼ぶ。次の Authenticated 遷移では発火しない。 */
    fun consume() { _pending.value = false }
}
```

- 永続化しない理由: アプリ kill → 再起動で `bootstrap()` 経由の Authenticated になるが、その経路では Welcome を出したくない
- A2f が `arm()` → `setAuthenticated()` の順で呼ぶ前提を計画書として明文化する

### 3. ルート分岐ロジック（疑似）

`pendingSignupWelcome` と `signup_completed` の AND を見て Welcome を 1 段挟む。

```
state = sessionStore.state.collect()
pending = signupCompletionSignal.pending.collect()
done = signupWelcomePreferences.signupCompleted.collect()

when (state) {
  Unauthenticated -> Login or Signup
  MfaPending      -> MfaVerify
  Authenticated   ->
      if (pending && !done) WelcomeScreen(onFinish = {
          signupWelcomePreferences.markCompleted()
          signupCompletionSignal.consume()
      })
      else Home
}
```

### 4. iOS 側ブリッジ

A2b の `AuthFlowIos` / Combine ブリッジ流儀を踏襲：

- `AuthFlowIos` に `signupCompletionSignal` / `signupWelcomePreferences` への参照を持たせる
- Swift 側からは `AuthFlowIos.armSignupWelcome()` / `AuthFlowIos.markWelcomeShown()` を呼ぶ薄い API を生やす
- 観測は既存の `StateFlow → AsyncSequence / Combine` ブリッジを再利用

## 実装ステップ

### Step 1: `multiplatform-settings-no-arg` 導入

- `gradle/libs.versions.toml` に上記 `[versions]` / `[libraries]` を追加
- `shared/build.gradle.kts` の `commonMain` 依存に `libs.multiplatform.settings.no.arg` を追加
- 同期して `./gradlew :shared:compileKotlinMetadata` が通ることを確認

### Step 2: `SignupWelcomePreferences` 実装 + 単体テスト

- `shared/src/commonMain/kotlin/studio/nxtech/fujubank/signup/SignupWelcomePreferences.kt` 新規（上記シグネチャ）
- `shared/src/commonTest/kotlin/studio/nxtech/fujubank/signup/SignupWelcomePreferencesTest.kt` 新規
  - fake `Settings`（`MapSettings` で OK）を注入
  - 検証ケース:
    - 初期値が `false`
    - `markCompleted()` 後に `signupCompleted.value == true`
    - 同じ `Settings` で再生成しても `true` が復元される（永続化されている）
    - `resetForDebug()` で `false` に戻る

### Step 3: `SignupCompletionSignal` 実装

- `shared/src/commonMain/kotlin/studio/nxtech/fujubank/signup/SignupCompletionSignal.kt` 新規
- 単体テストは状態遷移が自明なので必須としない（必要なら 1 ケースだけ `arm → consume` を確認）

### Step 4: DI 登録

- 既存 `SessionModule.kt`（or 同等の Koin module）に追加：
  - `single { Settings() }`（`no-arg` の場合プラットフォーム実装が自動解決）
  - `single { SignupWelcomePreferences(get()) }`
  - `single { SignupCompletionSignal() }`

### Step 5: 既存「ようこそ」画面の書き換え

- Compose:
  - 既存 Welcome Composable があれば、ルートからの呼び出しシグネチャを `WelcomeScreen(onFinish: () -> Unit)` に揃えて書き換え
  - 無ければ `composeApp/src/commonMain/kotlin/studio/nxtech/fujubank/features/welcome/WelcomeScreen.kt` を新規。Figma 準拠の見た目（背景・ロゴ・歓迎テキスト・CTA）
- SwiftUI:
  - 既存 Welcome View があれば、`WelcomeView(onFinish: () -> Void)` に揃えて書き換え
  - 無ければ `iosApp/iosApp/Features/Welcome/WelcomeView.swift` を新規

### Step 6: ルート分岐への組み込み（Compose）

- `composeApp/.../App.kt`（or 既存ルート Composable）の `when (sessionState)` に Welcome 段を挟む
- `pending` と `done` の collectAsState を追加
- `onFinish` で `markCompleted()` + `consume()` を順に呼ぶ

### Step 7: ルート分岐への組み込み（SwiftUI）

- `iosApp/iosApp/App/AppRoot.swift` の `Authenticated` 分岐内で `pending && !done` の場合に `WelcomeView` を出す
- `AuthFlowIos` に観測ブリッジを追加（`pendingPublisher` / `signupCompletedPublisher` 等、A2b で使ったブリッジに合わせる）

### Step 8: A2f 側との結合フック仕様の明文化

- 本タスク内では A2f 本体は実装しない。代わりに A2f が満たすべき契約を README 的に明記：
  - 「サインアップ API 成功 → `signupCompletionSignal.arm()` を呼ぶ → `sessionStore.setAuthenticated()` を呼ぶ」の順序
  - iOS は `AuthFlowIos.armSignupWelcome()` を呼んでから認証完了をブリッジする

### Step 9: A2c 計画書のリンク更新

- `docs/tasks/a2c-launch-splash.md` の関連タスク欄を更新：
  - `**A2d**: \`a2d-welcome-as-signup-rewire.md\`（未作成）` → `（未作成）` を外し、実体パスへのリンクへ差し替え
- 本タスクのコミットに含める（A2c 計画書を別タスク扱いにしない）

### Step 10: 動作確認

- Android / iOS でサインアップ完了 → Welcome → Home の遷移を実機で確認
- アプリ kill → 再起動でログイン経由の Authenticated になっても Welcome が出ないことを確認
- 同じユーザーで再ログイン → Welcome が出ないことを確認（`signup_completed == true` が効いている）

## 検証

- [ ] `./gradlew build` が通る
- [ ] `./gradlew :composeApp:assembleDebug` が通る
- [ ] `./gradlew :shared:allTests` が通る（`SignupWelcomePreferencesTest` を含む）
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が通る
- [ ] サインアップ完了直後に Welcome 画面が 1 回だけ表示される（Android / iOS 両方）
- [ ] Welcome の CTA 押下 → Home へ遷移し、`signup_completed = true` が永続化される
- [ ] アプリ再起動 → bootstrap 復元による Authenticated 遷移では Welcome が出ない
- [ ] ログイン画面経由の Authenticated 遷移では Welcome が出ない（`pendingSignupWelcome == false` が効いている）
- [ ] 一度 Welcome を見たユーザーは、再ログイン / 再起動を経ても二度と Welcome が出ない
- [ ] `docs/tasks/a2c-launch-splash.md` の A2d リンクが実体パスへ更新されている

## 技術的な補足

- **永続化の選択理由**: `multiplatform-settings-no-arg` は Android (`SharedPreferences`) / iOS (`NSUserDefaults`) を `Settings()` だけで透過的に使える。今後 Welcome 以外でも軽量な永続フラグが必要になったときの基盤になる。SQLDelight や DataStore はこの 1 フラグのために重い。
- **キー名の名前空間**: 当面 `signup_completed` のフラットキーで運用し、フラグ種別が増えてきたら `welcome.signup_completed` のようなプレフィックス運用に切り替える（本タスクではやらない）。
- **マルチ端末・アカウント切替**: `signup_completed` はインストール単位なので、別端末では再表示される。1 ユーザー 1 回に厳密化したい場合は backend 側にフラグを持たせる必要があるが、本タスクのスコープ外。
- **`pendingSignupWelcome` を永続化しない理由**: kill → 再起動で復元された Authenticated は「サインアップ動線発」ではないため、ワンショットはプロセス内シグナルで十分。永続化するとアプリ kill タイミング次第で誤発火する。
- **テスト時のリセット**: `resetForDebug()` は本番ビルドからも呼べるが、UI からは露出させない。デバッグメニューを設ける場合は別タスクで配線する。
- **A2c との関係**: 起動スプラッシュ（A2c）と Welcome（A2d）は段ではなく**別経路**。Splash は bootstrap 中、Welcome はサインアップ完了フック。両者は表示タイミングが重ならない。

## 関連タスク

- **A2b**: ログイン画面 UI（`SessionStore` / `SessionState` / `AuthFlowIos` の前提）
- **A2c**: `a2c-launch-splash.md`（起動スプラッシュ。Welcome とは別経路）
- **A2f**: `a2f-signup-screen.md`（未作成、本タスクと**同時マージ前提**）
  - 契約: `signupCompletionSignal.arm()` → `sessionStore.setAuthenticated()` の順で呼ぶこと
- **A3**: `a3-home-balance-profile.md`（Welcome の遷移先）
