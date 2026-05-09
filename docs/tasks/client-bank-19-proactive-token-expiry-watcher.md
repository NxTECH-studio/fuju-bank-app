# client-bank-19: access_token expiresAt の proactive 監視

> **優先度**: **中（19=中、最後に着手）**
> **依存**: `client-bank-17`（refresh 失敗で clear）と `client-bank-18`
> （ローカル state 破棄）の完了が前提。proactive logout 経路が
> 既存 clear フローと衝突しないようにするため。
> **ステータス**: 本ドキュメントは概要 + 未決事項中心。
> 実装着手時にもう一度 `/create-task` で詰める前提。

## 概要

`TokenStorage.loadExpiresAt()` で取得できる access_token の絶対期限を背景で監視し、
**期限切れ直前に自前で refresh を kick する**（or 期限切れ後に強制 logout する）
仕組みを追加する。現状は「API を叩いて 401 が返って初めて refresh する」という
受動的な refresh だけなので、ユーザーのファーストアクションが必ず一度待たされる /
直前に backend が落ちていると気付かない、という UX 課題を解消する。

## 背景・目的

- 現状の挙動:
  - access_token は AuthCore から `expires_in` 付きで配布され、
    `tokenStorage.saveAccess(token, expiresAt = now + expiresIn)` で永続化されている。
  - `expiresAt` は **保存しているだけで利用していない**（コード上明確にコメントされている）。
  - refresh は Ktor `Auth` plugin の `refreshTokens` ブロックが 401 を見て初めて起動する。
- 問題:
  - 「アプリを開いた瞬間にホーム残高 GET → 401 → refresh → retry」という二段階通信が
    必ず走り、初回描画が遅い。
  - access_token の自然失効と同時に backend 側 refresh family も失効しているケース
    （長時間アプリ未起動 / refresh cookie expire 後）では `client-bank-17` の経路で
    Unauthenticated に戻されるが、ユーザーは「バックグラウンドにしばらく置いて開いたら
    勝手に Login 画面に戻った」体験になる。事前に「再ログインが必要」と気付ける UX が
    望ましい。
- 目標:
  - access_token の `expiresAt - {threshold}` のタイミングで proactive に refresh する
    監視タイマー or Lifecycle hook を設置。
  - 期限切れ後にバックグラウンドから戻ってきた場合、最初の API call の前に refresh が
    終わっているか、refresh が無効と確定していれば即 `Unauthenticated` に倒す。

## 影響範囲

- モジュール: `shared` 中心 / `composeApp` (Lifecycle 接続) / `iosApp` (Scene Phase 接続)
- ソースセット:
  - `shared/src/commonMain/kotlin/studio/nxtech/fujubank/session/TokenExpiryWatcher.kt` (新規)
    - `SessionStore.state` が Authenticated の間だけタイマーを起動し、
      `expiresAt - threshold` で `AuthRepository.refresh()` を kick する。
    - 失敗時は `client-bank-17` のフローと同じく `SessionStore.clear()` に倒す。
  - `shared/src/commonMain/kotlin/studio/nxtech/fujubank/session/SessionStore.kt` 変更
    （Watcher を組み込むか、外部から observe 可能なフックを公開）
  - Android: `App.kt` / `MainActivity.kt` の `Lifecycle.ON_RESUME` で Watcher の
    on-demand check を発火。
  - iOS: `AppRoot.swift` の `ScenePhase` 観測で同等処理。
- 破壊的変更: shared に新規クラス追加のみ。既存 API は無変更。
- 追加依存: なし（kotlinx-coroutines の `delay` / `Clock.System.now()` のみ）

## 実装ステップ（概要）

### A. 共通方針

1. **監視方式の選定（未決事項）**
   - **(a) タイマー方式**: Authenticated 遷移時に
     `delay(expiresAt - now - threshold)` してから refresh を kick。
     シンプルだがタイマー精度がアプリのバックグラウンド/sleep に依存。
   - **(b) on-demand check 方式**: Lifecycle.ON_RESUME / ScenePhase=.active で
     `if (now > expiresAt - threshold) refresh()` を実行。
     アプリ復帰のタイミングで都度チェック。タイマー不要だが、長時間
     foreground のときに切れる懸念。
   - **(c) ハイブリッド**: foreground 中は (a) のタイマー、バックグラウンド復帰時は (b)
     の on-demand check。両方の長所をとる。
   - 推奨: **(c) ハイブリッド** だが、MVP では (b) のみで十分という判断もありうる。
2. **threshold の決定（未決事項）**
   - 残り 60 秒 / 30 秒 / 10 秒 を切ったら proactive refresh。
   - access_token の有効期限が短い（数分〜数十分）想定。backend 側の `expires_in` を要確認。
3. **expire 検知時の挙動（未決事項）**
   - **デフォルト**: 自動 refresh kick。失敗したら `client-bank-17` と同じく
     `SessionStore.clear()` に倒す（追加実装不要、AuthTokenRefresher 経由で済ませる）。
   - 警告ダイアログ「セッションが間もなく切れます」を出すかは UX 検討余地あり。
     MVP では出さない方針が無難。

### B. 共通実装 (shared)

4. **`TokenExpiryWatcher.kt` 新規**
   - `SessionStore.state` を collect して、Authenticated に入った瞬間に
     `tokenStorage.loadExpiresAt()` を取得し、必要なら delay → refresh kick。
   - Unauthenticated / MfaPending では監視を停止する（Job をキャンセル）。
   - on-demand `checkNow()` メソッドを公開し、Android Lifecycle / iOS ScenePhase から呼べるようにする。
   - 失敗時の `SessionStore.clear()` は **本 Watcher からは呼ばない**。
     `AuthRepository.refresh()` を `installAuth = true` の HttpClient 経由で呼べば
     `AuthTokenRefresher` 経由で `client-bank-17` の clear 経路に乗る…と思いきや、
     refresh 自体が `installAuth = false` の AuthApi 経由なので Watcher が直接呼ぶと
     `AuthTokenRefresher` は経由しない。そのため Watcher 内で `NetworkResult` を見て
     失敗時に `tokenStorage.clear() + sessionStore.clear()` を呼ぶ必要がある。
     → `client-bank-17` で抽出した clear ロジックを `SessionStore.invalidate()` のような
     共通メソッドに切り出して再利用するのが望ましい（実装着手時に再検討）。

5. **DI 登録**
   - `single { TokenExpiryWatcher(get(), get(), get()) }` を `sessionModule` 等に追加。
   - 起動は `client-bank-18` の `SessionResetCoordinator` と同じ初期化経路で `start()` を呼ぶ。

### C. プラットフォーム接続

6. **Android: Lifecycle.ON_RESUME で `checkNow()` 呼び出し**
   - `MainActivity.onResume()` で Watcher を取得して `checkNow()` を呼ぶ、もしくは
     Compose の `LifecycleEventEffect(Lifecycle.Event.ON_RESUME)` で同等。
7. **iOS: `ScenePhase == .active` 遷移で `checkNow()` 呼び出し**
   - `AppRoot.swift` で `@Environment(\.scenePhase)` を観測して `.active` 遷移時に呼ぶ。

### D. テスト

8. **`TokenExpiryWatcherTest.kt` 新規**
   - `expiresAt` を「現在時刻 + 1 秒」に設定して Watcher を start → 1 秒後に
     refresh が呼ばれることを `Turbine` などで検証。
   - 既に切れている expiresAt で `checkNow()` を呼ぶと即 refresh が走る。
   - `Unauthenticated` 中は呼ばれない。
9. **時刻のテスタビリティ**
   - `nowMillis: () -> Long` を Watcher コンストラクタに inject（既存
     `AuthRepository` パターンと揃える）。

## 検証

- [ ] `./gradlew build` が通る
- [ ] `./gradlew :shared:allTests` が通る（新規 Watcher テスト含む）
- [ ] `./gradlew :composeApp:assembleDebug` が通る
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が通る
- [ ] **Android 実機**:
  - [ ] access_token 期限が短い debug ビルドでアプリを開きっぱなしにして、
    閾値超え時に refresh が自動実行されることを Logcat で確認
  - [ ] バックグラウンド長時間放置後の復帰で `checkNow()` が refresh を kick することを確認
  - [ ] refresh 失敗時に `client-bank-17` 経路と同じく Login に戻ることを確認
- [ ] **iOS シミュレータ**: 上記同等を確認

## 技術的な補足

### `expiresAt` が null のケース

- `AuthRepository.expiresAtFrom()` は `nowMillis() <= 0L` の場合 null を返す。
  Watcher は `expiresAt == null` の場合 **タイマーを起動せず on-demand check だけ動かす**
  か、`nowMillis()` が確実に正の値を返すよう DI を見直す（後者推奨）。

### バックグラウンドタイマーの挙動

- iOS はバックグラウンド中 `Task.sleep(...)` が一時停止する。foreground 復帰で
  delay の残り時間が再計算されるかは要検証。一般には ScenePhase 観測の
  `checkNow()` で代替する方が確実。
- Android も Doze / Battery Saver で同様の懸念。Lifecycle 観測の方が信頼度高い。
- → **MVP では on-demand check (b) ベースで進めるのが現実解**。

### 既存 refresh ループとの衝突

- `AuthTokenRefresher` 経由の refresh と Watcher 経由の refresh が同時に走る可能性がある。
- AuthCore 側で同じ refresh_token を二重に消費すると `TOKEN_REVOKED` を起こす設計の場合、
  Mutex 等で直列化が必要。
- 対策: Watcher 内で `Mutex.tryLock` を取り、取れなければスキップ。または
  `AuthRepository.refresh()` 内に Mutex を内蔵させる（こちらの方が筋が良い）。
- 着手時に backend 仕様を再確認した上で設計する。

## Out of Scope（後続タスクで対応）

1. **「セッションが間もなく切れます」事前通知 UI** — UX 検討の余地あり。MVP 外。
2. **多端末セッション同期** — backend で SSE / WebSocket 経由の forced logout 通知を
   受ける実装。現状 backend 側にも該当 API なし。
3. **`expiresAt` を使った optimistic disable**（API call 自体を期限切れ後に走らせない） —
   Watcher の自動 refresh で代替できるため不要。

## 未決事項

- **期限切れ判定の閾値**: 残り 60 秒 / 30 秒 / 10 秒 / 0 秒で expire 扱い、のいずれか。
  backend `expires_in` の長さに依存して決める（要確認）。
- **監視方式**: タイマー (a) / on-demand (b) / ハイブリッド (c)。MVP は (b) のみで十分か。
  実装着手時に backend `expires_in` を確認した上で決定。
- **expire 検知時の挙動**:
  - 自動 refresh kick（デフォルト）
  - 強制 logout（refresh が失敗した場合のみ — `client-bank-17` の経路）
  - 警告ダイアログ表示（MVP では見送り）
- **proactive refresh と既存 401-driven refresh の競合**: Mutex で直列化する箇所を
  Watcher 側 / AuthRepository 側 / AuthTokenRefresher 側のどこに置くか。
- **`SessionStore.invalidate()` の抽出**: `client-bank-17` で書いた `tokenStorage.clear() +
  sessionStore.clear()` の組を、ここでも再利用するために shared に共通メソッドを
  生やすかどうか。生やすと API surface が増えるので、ヘルパー関数 (`internal fun
  invalidateSession(sessionStore, tokenStorage)`) として `commonMain` に置くのが妥当。
- **`expiresAt == null` 時のフォールバック**: タイマー起動を諦める / on-demand check のみ /
  そもそも expiresAt が null になる経路を塞ぐ。現状 `nowMillis = { 0L }` をデフォルトに
  している箇所があるため、DI 経由で確実に `Clock.System.now().toEpochMilliseconds()` を
  渡す改修も合わせて検討。
- **Lifecycle / ScenePhase 接続を Watcher 内に隠蔽するか UI 側で観測するか**:
  - 隠蔽案: Watcher が `expect fun installPlatformLifecycle(...)` を持ち、
    `androidMain` / `iosMain` でそれぞれ Lifecycle / ScenePhase を観測。
  - UI 側案: Compose / SwiftUI から `watcher.checkNow()` を直接呼ぶ。
  - 後者の方がシンプル。前者は再利用性高いが overengineering の懸念あり。
