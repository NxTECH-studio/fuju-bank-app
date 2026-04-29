# Debug: ログイン画面に「認証スキップ」ボタンを追加

## 概要

`composeApp` (Android) と `iosApp` のログイン画面に、debug ビルド限定で表示される「認証をスキップしてログイン後画面へ進む」ボタンを追加する。UI 確認のために毎回正規ログインを通さなくて済むようにする。release ビルドには一切露出させない。

## 背景・目的

- A3 以降のホーム画面 / 各 UI の確認のたびに、ログインフォームへ正しい資格情報を入れてバックエンドを叩く必要があり開発のテンポが落ちる。
- 認証状態を持たない開発者でも、ログイン後画面のレイアウトを Figma と比較できるようにしたい。
- 認証バックエンドが落ちている／未配備のときでも UI を起動して確認できる退避路にもなる。
- 想定利用者は **開発者本人がローカルで debug ビルドを使うとき** のみ。デザイナー配布や CI スクリーンショットは現時点ではスコープ外。

## 採用方針（ヒアリング結果）

- **配置**: ログイン画面 (`LoginScreen.kt` / `LoginView.swift`) の下部に「ログインせず進む」ボタンを 1 つ追加（Welcome 画面側には追加しない）。
- **ビルド分岐**: Android は `BuildConfig.DEBUG`、iOS は `#if DEBUG`。release ビルドではコード自体が UI に到達しない。
- **挙動**: 押下時に `SessionStore.state` などの認証状態は **書き換えない**。ルート Composable / ルート View が局所的に「強制的にログイン後画面を表示する」フラグを立てるだけ。アプリ再起動でフラグはリセットされる（永続化しない）。
- **遷移先**: 現状は `AuthenticatedPlaceholder` / `AuthenticatedPlaceholderView`（A3 で実装される本体に置き換わる）。
- **`Welcome` 画面の扱い**: `signupCompletionSignal.pending` を立てないため Welcome は経由せず、直接プレースホルダに飛ぶ。

## 影響範囲

- モジュール: `composeApp/`、`iosApp/`（`shared/` には変更なし）
- ソースセット:
  - `composeApp/src/androidMain/kotlin/...`:
    - `App.kt`: ルート分岐に `bypassAuth` フラグを追加
    - `features/auth/LoginScreen.kt`: debug 時のみ表示する CTA を追加
  - `iosApp/iosApp/...`:
    - `App/AppRoot.swift`: `bypassAuth` `@State` を追加して分岐
    - `Features/Auth/LoginView.swift`: `#if DEBUG` ブロックで CTA を追加
  - `composeApp/build.gradle.kts`: `android.buildFeatures.buildConfig = true` を有効化（現状未設定で `BuildConfig.DEBUG` が生成されないため）
- 破壊的変更: なし（debug でのみ追加 UI が出るだけ。`SessionState` API は触らない）
- 追加依存: なし（`libs.versions.toml` 変更なし）

## 実装ステップ

### 1. Android: `BuildConfig` の有効化

- `composeApp/build.gradle.kts` の `android { ... }` に `buildFeatures { buildConfig = true }` を追加し、`studio.nxtech.fujubank.BuildConfig.DEBUG` が生成されることを確認する。

### 2. Android: ルート Composable に bypass フラグを追加

- `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/App.kt`:
  - `var bypassAuth by rememberSaveable { mutableStateOf(false) }` を追加（画面回転耐性のため `rememberSaveable`、ただしプロセス kill では消える）。
  - `when (val state = sessionState)` の前に `if (bypassAuth) { AuthenticatedPlaceholder(userId = "debug-bypass") ; return@Surface }` 相当の早期分岐を入れる。
  - `LoginScreen` 呼び出しに `onDebugSkip` を渡す。**`BuildConfig.DEBUG` が `true` の場合のみ非 null** にして渡す（release では `null`）。

### 3. Android: LoginScreen に debug 用 CTA を追加

- `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/auth/LoginScreen.kt`:
  - 関数シグネチャに `onDebugSkip: (() -> Unit)? = null` を追加。
  - `BottomCta` の直下、または `SignupLink` の下に `if (onDebugSkip != null) { DebugSkipButton(onClick = onDebugSkip) }` を配置。
  - スタイルは本番 CTA と区別がつくように、グレー枠 + 細字（例: `OutlinedButton` 相当の見た目、文言「[DEBUG] ログインせず進む」）。本番 UI に紛れ込んだら一目で分かるようにする。
  - Preview 関数 (`LoginScreenLayoutPreview`) にも `onDebugSkip = {}` を渡してレンダリング確認できるようにする。

### 4. iOS: AppRoot に bypass フラグを追加

- `iosApp/iosApp/App/AppRoot.swift`:
  - `@State private var bypassAuth = false` を追加。
  - `body` の `Group { ... }` の最初に `if bypassAuth { AuthenticatedPlaceholderView(userId: "debug-bypass") }` 分岐を入れる（または `switch` の `default` ケース内で `LoginView` に `onDebugSkip` を渡す）。
  - `LoginView(viewModel: LoginViewModel())` 呼び出し時に、`#if DEBUG` で `onDebugSkip: { bypassAuth = true }` を渡し、release ビルドでは渡さない（または常に optional で `nil`）。

### 5. iOS: LoginView に debug 用 CTA を追加

- `iosApp/iosApp/Features/Auth/LoginView.swift`:
  - イニシャライザに `var onDebugSkip: (() -> Void)? = nil` を追加。
  - `bottomCta` の下に `#if DEBUG` ブロックで `if let onDebugSkip { debugSkipButton(action: onDebugSkip) }` を配置。
  - スタイルは Compose 側と揃える（OutlinedButton 風 / 「[DEBUG] ログインせず進む」）。

### 6. 動作確認

- Android Studio で debug ビルドを実機 or エミュレータに流し、ログイン画面下部にボタンが出ること、押下で `AuthenticatedPlaceholder` に遷移することを確認。
- `./gradlew :composeApp:assembleRelease` でビルドした APK にボタンが入っていないこと（コンパイル時に `BuildConfig.DEBUG` が `false` になり呼び出し側が `null` を渡すため、`DebugSkipButton` がそもそも合成されない）を、デコンパイル or `apkanalyzer` で念のため確認。
- Xcode の `Debug` configuration で iOS シミュレータ (`iPhone 15` 等) に流し、同様に表示・遷移を確認。
- Xcode の Scheme を `Release` に切り替えて Build → archive 直前まで確認し、`#if DEBUG` 内のシンボルが残らないことを確認。

## 検証

- [ ] `./gradlew :composeApp:assembleDebug` が通る
- [ ] `./gradlew :composeApp:assembleRelease` が通る（`BuildConfig.DEBUG` が `false` になる release でも参照箇所がコンパイル可能であること）
- [ ] `./gradlew :shared:allTests` が通る（shared 変更なしだが念のため）
- [ ] Android debug ビルドでログイン画面に「[DEBUG] ログインせず進む」ボタンが表示され、押下で `AuthenticatedPlaceholder` に遷移する
- [ ] Android release ビルドではボタンが表示されない
- [ ] iOS Debug configuration でログイン画面に同等のボタンが表示され、押下で `AuthenticatedPlaceholderView` に遷移する
- [ ] iOS Release configuration でボタンが表示されない（`#if DEBUG` で削除されている）
- [ ] アプリを kill → 再起動するとフラグがリセットされ、再度ログイン画面が表示される

## 技術的な補足

- **なぜ `SessionStore.state` を書き換えないか**: ダミーの `SessionState.Authenticated` を流すと `SessionStore` の保有する `userId` / トークン状態と齟齬が出て、bootstrap 復元や API リトライ経路にゴミデータが流れ得る。debug でも `SessionStore` の整合性は保ちたい。代わりに UI 層のローカル state で「強制的にプレースホルダを出す」だけにとどめ、A3 のホーム実装が始まったらホーム本体側でも同じフラグを尊重して描画できるようにする。
- **`rememberSaveable` を使う理由**: Android の画面回転で `bypassAuth` が消えるとデバッグ中に意図せずログイン画面に戻されて煩わしい。プロセス kill で消えるのは許容。
- **iOS で `bypassAuth` を AppRoot に置く理由**: Swift では `#if DEBUG` がコンパイル時に評価されるので、release ビルドでは `bypassAuth` も `onDebugSkip` 関連コードも完全に消える設計が自然。AppRoot 側で `@State` を保持しつつ、`#if DEBUG` ブロックでのみ `LoginView` に non-nil の callback を渡す形にする。
- **`BuildConfig` を有効化する副作用**: `composeApp/build.gradle.kts` で `buildConfig = true` を有効にすると、`studio.nxtech.fujubank.BuildConfig` クラスが生成される。`shared/` の `BuildKonfig` とは別物（namespace が `studio.nxtech.fujubank` vs `studio.nxtech.fujubank.shared`）なので衝突しない。
- **デザイン上の注意**: ボタンは Figma にない要素なので、本番 UI と紛れない警告色（または OutlinedButton + 「[DEBUG]」プレフィクス）にする。Figma 比較スクリーンショットを撮るときは隠せるよう、可能ならボタン位置は Figma 上は空白の領域に配置する。
