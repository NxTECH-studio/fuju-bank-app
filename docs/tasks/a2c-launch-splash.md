# A2c: 起動スプラッシュ（Launch Screen / bootstrap 中の表示）

## メタ情報

- **Phase**: 1
- **並行起動**: A3（ホーム画面）と並列実装可能
- **依存**: A2b（`SessionStore.bootstrap()` がすでに導入済み）
- **同期点**: なし。Figma の Launch Screen アセット（node `383-18577`）が揃っていれば着手可。

## 概要

アプリ起動時、`SessionStore.bootstrap()` 実行中に表示される **ネイティブ Launch Screen のみ** を実装する。

- iOS: `LaunchScreen.storyboard` を最小構成（背景色 + ロゴ ImageView）にし、bootstrap 完了までは SwiftUI 側の `SplashView` で同等の見た目を維持する。
- Android: `androidx.core:core-splashscreen` の `installSplashScreen()` を使い、`setKeepOnScreenCondition` で bootstrap 完了まで保持する。

**スコープ外**: 旧 a2c-splash-welcome.md にあった「初回ログイン直後の Welcome シーケンス（テキスト → ロゴのクロスフェード演出）」は本タスクから除外する。後続タスクで別途扱う。

## 背景・目的

- A2b 完了直後の現状、iOS / Android ともに `SessionStore.bootstrap()` が走っている間に無装飾の白画面（あるいは OS デフォルトのスプラッシュ）が一瞬表示される。Figma 通りのロゴ Launch Screen を出すべき。
- 旧計画 (`a2c-splash-welcome.md`) は「起動スプラッシュ」と「初回 Welcome 演出」を 1 タスクに混ぜていて Step が肥大化していた。今回は **bootstrap 中の表示** に絞ってリリースサイクルを短くする。

## 旧計画ファイルの取り扱い

- `docs/tasks/a2c-splash-welcome.md` は **削除する**（退避ではない）。
  - `git rm docs/tasks/a2c-splash-welcome.md`
  - `_archive/` ディレクトリは作成しない。
- 旧計画にあった Welcome シーケンス関連の検討（永続フラグ `welcome_shown` / `WelcomePreferences` / `WelcomeSequence` Composable / SwiftUI View など）は本タスクには含めない。必要になった時点で別タスクとして起票する。

## 表示フロー

```
[App 起動]
  ↓
ネイティブ Launch Screen (383-18577)        ← OS が最初に出す
  ↓ （Activity / SwiftUI 起動）
SessionStore.bootstrap() 実行中
  - Android: installSplashScreen() の keepOnScreenCondition で保持
  - iOS:     SwiftUI ルートで SplashView を表示
  ↓ bootstrapped == true になった瞬間
SessionState 評価
  ├─ Unauthenticated → LoginView
  └─ Authenticated   → Home（A3 が未完なら placeholder）
```

## 影響範囲

- モジュール: `composeApp/`, `shared/`, `iosApp/`
- ソースセット:
  - `shared/src/commonMain/...`: `SessionStore` に `bootstrapped: StateFlow<Boolean>` を追加（公開 API 追加のみ、破壊的変更なし）
  - `composeApp/src/androidMain/...`: `MainActivity` で `installSplashScreen()` + `setKeepOnScreenCondition`
  - `composeApp/src/androidMain/res/...`: スプラッシュテーマ追加
  - `composeApp/src/commonMain/composeResources/...`: ロゴアセット
  - `iosApp/iosApp/...`: `LaunchScreen.storyboard` 最小編集 + `SplashView.swift` 新規 + `iOSApp` ルートで bootstrap 完了まで `SplashView` 表示
- 破壊的変更: なし（`SessionStore.bootstrapped` は新規追加プロパティ）。
- 追加依存:
  - Android: `androidx.core:core-splashscreen` （`gradle/libs.versions.toml` 新規追加）
  - iOS / shared: なし

## 表示時間（min-duration）

- **min-duration: 2000ms 固定**。`bootstrap()` が早く終わってもロゴを最低 2 秒は見せる。
- Figma 側にアニメーション尺の指定が出てきた場合は、実装時に上振れ・下振れ可（Figma の指示を優先する）。値はプラットフォーム間で揃える定数として配置する：
  - Compose: `composeApp/src/androidMain/.../splash/SplashConfig.kt` に `const val MIN_DURATION_MS = 2000L`
  - iOS: `iosApp/iosApp/Features/Splash/SplashConfig.swift` に `static let minDuration: TimeInterval = 2.0`

## bootstrap 完了通知の方式（推奨: Option C）

### 採用案: `SessionStore` に `bootstrapped: StateFlow<Boolean>` を追加

既存実装 (`shared/src/commonMain/kotlin/studio/nxtech/fujubank/session/SessionStore.kt`) には `private var bootstrapped: Boolean = false` がすでに存在する。これを `MutableStateFlow<Boolean>` に置き換え、外部から read-only な `StateFlow<Boolean>` として公開する。

```kotlin
class SessionStore {
    private val _bootstrapped = MutableStateFlow(false)
    val bootstrapped: StateFlow<Boolean> = _bootstrapped.asStateFlow()

    private val bootstrapMutex = Mutex()

    suspend fun bootstrap(
        authRepository: AuthRepository,
        userRepository: UserRepository,
    ) {
        bootstrapMutex.withLock {
            if (_bootstrapped.value) return
        }
        try {
            // 既存の access 復元 / refresh / getMe ロジック
        } finally {
            _bootstrapped.value = true   // 成功・失敗どちらでも完了を通知
        }
    }
}
```

### 採用理由（他案との比較）

| 案 | 概要 | 評価 |
| :-- | :-- | :-- |
| (a) `MainActivity` から `SessionStore.state` を `lifecycleScope.launch` で collect | `state` の遷移を見て splash を外す | `state` は `Unauthenticated` 初期値と「bootstrap 後の Unauthenticated」が区別できず、splash を外すタイミングを誤判定する。NG |
| (b) `App()` Composable に `onSplashReady: () -> Unit` を渡す | commonMain → androidMain にコールバックを伝搬 | Compose 側でしか拾えず、`installSplashScreen().setKeepOnScreenCondition` から見るのが難しい。iOS の SwiftUI とも合わせづらい。 |
| **(c) `SessionStore.bootstrapped: StateFlow<Boolean>` を追加** | shared 側に bootstrap 完了の真実を一元化 | Android (`MainActivity` の `setKeepOnScreenCondition { !sessionStore.bootstrapped.value }`) も iOS (`SplashView` で `bootstrapped.collect`) も同じ Flow を観測できる。既存フィールドを Flow 化するだけで侵襲度も低く、commonMain と androidMain の責務分離が明確。**採用。** |

## 実装ステップ

### Step 1: 旧計画ファイルの削除

- `git rm docs/tasks/a2c-splash-welcome.md`
- 削除のみ。`_archive/` 等への退避はしない。

### Step 2: shared - `SessionStore.bootstrapped` の Flow 化

- `shared/src/commonMain/kotlin/studio/nxtech/fujubank/session/SessionStore.kt` を編集。
  - `private var bootstrapped: Boolean` を `private val _bootstrapped = MutableStateFlow(false)` に置換。
  - `val bootstrapped: StateFlow<Boolean> = _bootstrapped.asStateFlow()` を公開。
  - `bootstrap()` の早期 return 条件を `_bootstrapped.value` で判定。
  - 関数末尾（成功・失敗どちらのパスでも）に `_bootstrapped.value = true` を立てる。`try/finally` で確実に通知する。
- 既存の二重起動防止セマンティクス（`bootstrapMutex` で 1 回だけ実体処理が走る）は維持する。

### Step 3: ロゴアセット取り込み

- Figma `383-18577` からロゴを export。
- iOS: `iosApp/iosApp/Assets.xcassets/FujuLogo.imageset/`（@1x / @2x / @3x）。
- Android: `composeApp/src/commonMain/composeResources/drawable/fuju_logo.xml`（vector 推奨）。

### Step 4: Android - core-splashscreen 導入

- `gradle/libs.versions.toml`:
  ```toml
  [versions]
  androidx-core-splashscreen = "1.0.1"

  [libraries]
  androidx-core-splashscreen = { group = "androidx.core", name = "core-splashscreen", version.ref = "androidx-core-splashscreen" }
  ```
- `composeApp/build.gradle.kts` の `androidMain` 依存に `libs.androidx.core.splashscreen` を追加。
- `composeApp/src/androidMain/res/values/colors.xml` に `fuju_splash_bg` を追加（Figma の背景色）。
- `composeApp/src/androidMain/res/values/themes.xml`:
  ```xml
  <style name="Theme.App.Starting" parent="Theme.SplashScreen">
      <item name="windowSplashScreenBackground">@color/fuju_splash_bg</item>
      <item name="windowSplashScreenAnimatedIcon">@drawable/fuju_logo</item>
      <item name="postSplashScreenTheme">@style/Theme.App</item>
  </style>
  ```
- `AndroidManifest.xml` の `application` または `MainActivity` の `android:theme` を `@style/Theme.App.Starting` に切り替え。

### Step 5: Android - in-app Splash と OS splash の二段構え

> **計画変更（実装時に判断）**: 当初は `setKeepOnScreenCondition` で OS splash を保持して
> bootstrap 完了 + min-duration を待つ案だったが、Material splash screen API は中央正方形
> アイコン 1 枚しか描画できず、Figma node 175-2457 の横長合成（icon + wordmark + 装飾）を
> 表現できないことが判明したため、以下の二段構成に変更した。

#### OS splash（背景色のみのフラッシュ）

- `Theme.App.Starting` の `windowSplashScreenAnimatedIcon` を `@android:color/transparent`
  に設定し、OS splash の中央アイコンを抑止
- `windowSplashScreenBackground` には `@color/fuju_splash_bg` (`#F6F7F9`) のみを指定
- `MainActivity.installSplashScreen()` は呼ぶ（androidx.core:core-splashscreen で API 24+ に
  互換 backport が効く）が `setKeepOnScreenCondition` は使わない（OS splash は活動が
  ready になり次第即閉じる）

#### in-app Splash（Figma 通りの合成）

- `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/splash/SplashScreen.kt` を新規作成し、
  背景色 + Subtract 装飾 (`fuju_splash_decoration.xml`) + 合成ロゴ (`fuju_logo.xml`) を ZStack 配置
- `App()` Composable で `splashFinished: Boolean` を `rememberSaveable` で保持し、bootstrap +
  min-duration を満たすまで `SplashScreen()` を表示
  ```kotlin
  var splashFinished by rememberSaveable { mutableStateOf(false) }
  if (!splashFinished) {
      LaunchedEffect(Unit) {
          val startedAt = SystemClock.elapsedRealtime()
          sessionStore.bootstrap(authRepository, userRepository)
          val elapsed = SystemClock.elapsedRealtime() - startedAt
          val remaining = SplashConfig.MIN_DURATION_MS - elapsed
          if (remaining > 0) delay(remaining)
          splashFinished = true
      }
  }
  ```
- `SplashConfig.MIN_DURATION_MS = 2000L` は `shared/src/commonMain` に配置し、Android / iOS
  両方が単一の真実源として参照する

#### 設計のポイント

- `rememberSaveable` で `splashFinished` を保持することで、画面回転で Activity が再生成されても
  Splash が再 2 秒表示されない
- `SystemClock.elapsedRealtime()` を採用（端末スリープ中も進む）。`uptimeMillis()` ではないことに注意
- bootstrap 起動を `App()` Composable 内に置くことで、`MainActivity` から `SessionStore` への
  Koin 注入が不要になる

### Step 6: iOS - LaunchScreen.storyboard 最小編集

- `iosApp/iosApp/Resources/LaunchScreen.storyboard`（または現在の Launch Screen 定義先）を編集。
  - 背景色を `fuju_splash_bg` 相当に。
  - 中央に `FujuLogo` ImageView を配置。
- `Info.plist` の `UILaunchStoryboardName` を確認（既存設定を流用）。
- storyboard はあくまで OS が最初に出す静的画面。bootstrap 中の保持は Step 7 の SwiftUI 側で行う。

### Step 7: iOS - SwiftUI `SplashView` で bootstrap 完了まで表示

- `iosApp/iosApp/Features/Splash/SplashView.swift` を新規作成。
  - storyboard と同じ見た目（背景色 + 中央ロゴ）を SwiftUI で再現。
- `iosApp/iosApp/Features/Splash/SplashConfig.swift`:
  ```swift
  enum SplashConfig {
      static let minDuration: TimeInterval = 2.0
  }
  ```
- ルート（既存の `iOSApp` / `AppRoot.swift` 周辺）で次のように分岐：
  ```swift
  @State private var splashFinished = false

  var body: some View {
      ZStack {
          if splashFinished {
              AppRoot()  // 既存の SessionState 分岐ルート
          } else {
              SplashView()
                  .task {
                      let start = Date()
                      // SessionStore.bootstrapped を Flow → AsyncSequence で観測
                      for await done in sessionStore.bootstrapped.asAsyncSequence() {
                          if done { break }
                      }
                      let elapsed = Date().timeIntervalSince(start)
                      let remaining = max(0, SplashConfig.minDuration - elapsed)
                      if remaining > 0 {
                          try? await Task.sleep(nanoseconds: UInt64(remaining * 1_000_000_000))
                      }
                      splashFinished = true
                  }
          }
      }
  }
  ```
- `StateFlow<Boolean>` から Swift で値を取り出すユーティリティ（`asAsyncSequence()` など）が未整備なら、既存の Koin / KMP-NativeCoroutines / 手書き collector のいずれかに合わせる（既存 `SessionState` の観測実装を踏襲する）。

### Step 8: bootstrap の起点確認

- `bootstrap()` を呼ぶ箇所（既存の `AuthFlowIos` / Compose 側エントリ）が、Splash を出している間も確実にコルーチンを起動していることを確認。
- 呼ばれない経路があると `bootstrapped` が `false` のままになり Splash が永久に出続けるので、`MainActivity.onCreate` / `iOSApp.init` のどちらかで `sessionStore.scope.launch { sessionStore.bootstrap(...) }` を確実に発火する。

## 検証

- [ ] `./gradlew build` が通る
- [ ] `./gradlew :composeApp:assembleDebug` が通る
- [ ] `./gradlew :shared:allTests` が通る（`SessionStore.bootstrapped` 追加に伴う回帰確認）
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が通る
- [ ] Android 実機 / エミュレータで起動時にロゴ Splash が表示され、bootstrap 完了 + 2 秒経過後に Login or Home に遷移する
- [ ] iOS シミュレータで起動時に LaunchScreen → SplashView → Login or Home の順で遷移する
- [ ] bootstrap が 2 秒以内に終わっても、最低 2 秒は Splash が表示される（min-duration が効いている）
- [ ] bootstrap が 2 秒以上かかった場合は完了するまで Splash が保持される
- [ ] **旧 `docs/tasks/a2c-splash-welcome.md` が削除済み**（リポジトリに残っていない）

## 技術的な補足

- `SessionStore.bootstrapped` は **完了通知専用の StateFlow**。値が `true` になった後にリセットすることは想定しない（再ログイン時もリセット不要）。
- Compose 側で `bootstrapped` を観測する必要はない（Splash の出し分けは Activity レベルの `setKeepOnScreenCondition` で完結する）。`App()` Composable の中身は触らないで済むのが本案のメリット。
- iOS の `SplashView` は LaunchScreen storyboard と見た目を一致させる。ズレるとスプラッシュ → SwiftUI への切替時にチラつきが出るので、背景色とロゴ位置は慎重に揃える。
- min-duration の値 (2000ms) は Figma 側のアニメーション尺指定が出た時点で再調整可。プラットフォーム間で値がズレないよう、定数を Compose / SwiftUI それぞれの `SplashConfig` に集約しておく。
- 「初回ログイン直後の Welcome 演出」は別タスクとして切り出す。本タスク完了後、必要なら新規 Notion タスクとして起票する。

## 関連タスク

- **A2b**: ログイン画面 UI（実装済、`SessionStore.bootstrap()` / `SessionState` 連動の前提を提供）
- **A2d**: [`a2d-welcome-as-signup-rewire.md`](./a2d-welcome-as-signup-rewire.md) — 既存「ようこそ」画面をサインアップ動線へ転用
  - 動線: サインアップで認証成功 → 「ようこそ」画面へ遷移
  - 注意: A2c の本タスクが扱う「起動スプラッシュ」とは別物。Welcome は Splash の後段ではなく、サインアップ完了フックとして発火する想定。
  - スコープ補記: A2d の実装ブランチ（`feature/a2d-welcome-as-signup-rewire`）には A2e のログイン UI 再現も同梱。詳細は A2d 本体の PR を参照。
- **A2e**: `a2e-login-redesign.md`（実装着手は A2d に同梱、計画書は未作成） — ログイン画面のリデザイン
  - Figma:
    - <https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=302-2698&m=dev>
    - <https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=364-11333&m=dev>
- **A2f**: `a2f-signup-screen.md`（未作成） — サインアップ画面本体
  - Figma:
    - <https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=383-12951&m=dev>
    - <https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=296-2092&m=dev>
    - <https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=383-14941&m=dev>
    - <https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=383-16473&m=dev>
    - <https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=383-16105&m=dev>
  - 動線メモ: A2f の認証成功時 → A2d の「ようこそ」画面へ遷移する仕様（A2d の rewire と密結合）。
- **A3**: `a3-home-balance-profile.md` — ホーム画面（Splash 完了後 Authenticated 時の遷移先）

> 補足: A2d は計画書作成・実装着手済（2026-04-29）。A2e は計画書未作成だが UI のみ A2d ブランチに同梱して先行着手済。A2f は計画書未作成のままで、後続セッションで `/create-task` する際、上記 Figma URL と動線メモを起点に着手する。
