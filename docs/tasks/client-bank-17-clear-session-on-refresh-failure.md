# client-bank-17: refreshTokens 失敗時に SessionStore.clear() を連動させる

> **優先度**: **高（17=高）**
> **依存**: `client-bank-16`（AccountHub にログアウト導線）の完了が前提。
> ログアウト導線が無い状態で `clear()` だけ強化しても、ユーザーが認知できず混乱する
> （いきなり Login 画面に戻る感覚になる）。
> **後続**: `client-bank-18`（ローカル state 破棄）でこの clear 経路に乗ったときの
> VM 側挙動を整える。

## 概要

Ktor `Auth` プラグインの `refreshTokens` ブロック（`shared/src/commonMain/.../HttpClientFactory.kt`
+ `AuthModule.kt`）で **refresh が失敗した場合に `SessionStore.clear()` を自動的に呼ぶ**
連動を追加する。現在は refresh 失敗で `null` を返すだけで、SessionStore は
`Authenticated` のままになり、UI が「ログイン中なのに API は全部 401」という
半死状態になる問題を解消する。

## 背景・目的

- 現状 `AuthModule.kt` の `AuthTokenRefresher`:
  ```kotlin
  AuthTokenRefresher {
      when (val result = get<AuthRepository>().refresh()) {
          is NetworkResult.Success -> get<TokenStorage>().loadAccess()
          is NetworkResult.Failure, is NetworkResult.NetworkFailure -> null
      }
  }
  ```
  失敗時に null を返すだけで、Ktor は単に再試行しなくなる。SessionStore は
  `Authenticated(userId)` のまま放置される。
- 結果として:
  - 全ての認証付き API（`/users/me` / `/transactions` 等）が永続的に 401 を返す
  - ユーザーは「ホーム画面が表示されているのに残高が出ない / 履歴が空」という状態に
    陥り、自力では復旧できない（ログアウト導線は `client-bank-16` で導入済み前提）
- backend（authcore）側で refresh_family が revoke される条件:
  - サーバが MFA 強制再認証を要求（`MFA_REQUIRED`）
  - reuse 検知 / `TOKEN_REVOKED`
  - refresh_token cookie の自然失効
  いずれも「ユーザー操作なしには復旧不能」なので、即 `Unauthenticated` に倒すのが正解。

### 既存資産の前提

- `AuthRepository.refresh()`:
  - 成功 → `tokenStorage.saveAccess(...)` で新 access を保存。
  - 失敗 → `tokenStorage` には何もしない。
- `SessionStore.clear()`: `_state.value = SessionState.Unauthenticated`。冪等。
- `TokenStorage.clear()`: access_token + expiresAt を削除。
- HttpClientFactory の `Auth { bearer { refreshTokens { ... } } }` は
  `installAuth = true` のクライアント（bank API 用）でのみ起動する。
  AuthApi 用の `installAuth = false` クライアントには影響しない（既存設計）。

## 影響範囲

- モジュール: `shared`
- ソースセット:
  - `shared/src/commonMain/kotlin/studio/nxtech/fujubank/di/AuthModule.kt`
    （`AuthTokenRefresher` の DI 定義に `SessionStore` 注入 + 失敗時 clear 連動）
  - `shared/src/commonMain/kotlin/studio/nxtech/fujubank/network/AuthTokenRefresher.kt`
    （ドキュメンテーションコメントの更新のみ。インターフェースは無変更）
  - `shared/src/commonTest/kotlin/studio/nxtech/fujubank/`
    （新規テスト: refresh 失敗で SessionStore が Unauthenticated に倒れることを検証）
- 破壊的変更:
  - shared 内部の DI 定義のみ変更。公開 API（`AuthTokenRefresher` インターフェース、
    `AuthRepository`、`SessionStore`）の signature は無変更。
- 追加依存: なし

## 実装ステップ

### A. 共通方針

1. **どこで clear するか** — 第一候補は `AuthTokenRefresher` の DI 定義内
   - 関心の閉じ込めとして HttpClient プラグイン経由の refresh 失敗だけを扱う点で
     最も狭く、影響範囲が読みやすい。
   - `AuthRepository.refresh()` 自体に clear を埋め込むと、`SessionStore.bootstrap()`
     からの初回 refresh 失敗時にも `clear()` が走るが、そもそも未認証状態なので
     問題はない（むしろ整合的）。とはいえ責務が分散するため AuthModule に閉じ込める方を採用する。
2. **「明示的に 401 が返ったときだけ clear」 vs 「Failure / NetworkFailure 全てで clear」**
   - **デフォルト方針**: `NetworkResult.Failure`（ApiError = HTTP エラー応答）のみ clear し、
     `NetworkResult.NetworkFailure`（オフライン / DNS エラー等）では clear せず null だけ返す。
     後者で clear すると地下鉄等の一時的な圏外で毎回ログアウトさせられて UX 破壊。
   - ただし、その判定だと「サーバ 5xx が一瞬出ただけで logout」も起きうる。要相談（未決事項）。
3. **toast / snackbar 通知**
   - shared 層から UI 通知を直接出す手段はない。本タスクでは `SessionStore.clear()` を呼ぶだけに
     とどめ、UI 側の通知は `client-bank-18` で別途扱う or 後続 polish。

### B. 実装 (`AuthModule.kt`)

4. **`AuthTokenRefresher` の DI 定義に `SessionStore` を注入**
   ```kotlin
   single<AuthTokenRefresher> {
       val authRepository = get<AuthRepository>()
       val tokenStorage = get<TokenStorage>()
       val sessionStore = get<SessionStore>()
       AuthTokenRefresher {
           when (val result = authRepository.refresh()) {
               is NetworkResult.Success -> tokenStorage.loadAccess()
               is NetworkResult.Failure -> {
                   // 明示的な API エラー（401/403/MFA_REQUIRED/TOKEN_REVOKED 等）。
                   // refresh_family が revoke された可能性が高いので即 Unauthenticated に倒す。
                   tokenStorage.clear()
                   sessionStore.clear()
                   null
               }
               is NetworkResult.NetworkFailure -> {
                   // 一時的な通信エラー。clear はせず null 返却で Ktor 側に再試行可能性を残す。
                   null
               }
           }
       }
   }
   ```
   - `tokenStorage.clear()` は `SessionStore.clear()` の前に呼ぶ。順序が逆だと、
     UI が `Unauthenticated` を観測した瞬間に `LoginScreen` から `bootstrap()` が
     再走することはないが、念のため整合性を保つ。
   - Koin の DI で `SessionStore` を `single` で登録済みである前提（既存のはず）。
     未登録なら `sessionModule` 等を確認して必要であれば登録する。
5. **AuthRepository.refresh() 内では SessionStore に触らない**
   - bootstrap 経由の refresh は SessionStore.bootstrap が結果を見て自前で
     state を倒す責務を持つため、AuthRepository が SessionStore を知らない設計を維持する。

### C. テスト

6. **`shared/src/commonTest/.../AuthModuleRefresherTest.kt` (新規)** または既存の
   AuthRepositoryTest 隣に置く:
   - **fake `AuthRepository`**: `refresh()` が `NetworkResult.Failure` を返すよう設定
   - **fake `TokenStorage`** / **`SessionStore` 実体**を組み合わせて
     `AuthTokenRefresher.refresh()` を直接呼び、`sessionStore.current` が
     `Unauthenticated` になっていることを assert する。
   - `NetworkFailure` を返す経路では `Authenticated` のままであることを assert する。

### D. 動作確認用 debug ヘルパ（任意）

7. **手動再現方法**
   - debug ビルドで `tokenStorage.saveAccess("invalid")` を呼んだ状態で 401 を引き起こし、
     `refreshTokens` ブロックが起動して clear が走ることを確認する。
   - もしくは backend の refresh エンドポイントを一時的に 401 固定にする。
   - これらは本番ロジックには載せず、検証用に local change として試す。

## 検証

- [ ] `./gradlew build` が通る
- [ ] `./gradlew :shared:allTests` が通る（新規テスト含む）
- [ ] `./gradlew :composeApp:assembleDebug` が通る
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が通る
- [ ] **Android 実機**:
  - [ ] 認証付き API が 401 → refresh 失敗 → SessionStore.clear() → Login 画面に戻る
  - [ ] オフライン中に同じ状況を再現しても Login に戻されない（Authenticated のまま、
    再オンラインで自然復旧）
- [ ] **iOS シミュレータ**: 上記同等を確認
- [ ] `client-bank-16` で導入したログアウトと同じ Login 画面遷移経路を辿ること

## 技術的な補足

### `Failure` vs `NetworkFailure` の判定

- `NetworkResult.Failure` は HTTP エラー応答（4xx/5xx）+ ApiError へのマッピング。
- `NetworkResult.NetworkFailure` は IOException 系（接続不能 / DNS / タイムアウト）。
- 5xx はサーバ側障害なので clear すべきではない…という意見と、refresh が 5xx を返す
  時点で family revoke の可能性がある…という意見の両方ある。デフォルトは
  「Failure 全てで clear」だが、5xx だけ除外する選択肢も残す（未決事項）。

### Ktor `Auth` プラグインの再帰起動防止

- `AuthApi` は `installAuth = false` の専用 HttpClient を使うため、refresh が 401 を
  返したときに refreshTokens ブロックが再帰起動する deadlock は既に回避されている。
- 本タスクで `SessionStore.clear()` を呼んでも、AuthRepository.logout() は
  `installAuth = false` クライアント経由なので副作用ループは発生しない。

### bootstrap 経路との関係

- `SessionStore.bootstrap()` は内部で `authRepository.refresh()` を直接呼ぶが、
  これは `installAuth = false` クライアント経由のため refreshTokens ブロックは
  起動しない。よって本タスクの変更は bootstrap 経路には影響しない。
- bootstrap 失敗時は `bootstrap()` 自身が `_state.value = Unauthenticated` を
  セットするので動作は変わらない。

## Out of Scope（後続タスクで対応）

1. **clear 時の toast / snackbar 表示** — UI 通知は `client-bank-18` で対応。
2. **VM 内ローカル state の reset** → `client-bank-18`
3. **proactive expiry 監視** → `client-bank-19`
4. **5xx だけ除外する細粒度判定** — 必要なら後続 polish。

## 未決事項

- **clear する判定の細粒度**:
  - 全 `Failure` で clear（デフォルト）
  - 401/403/特定 ApiError コードのみ clear（誤爆を避けたい場合）
  - 5xx は除外（一時障害との切り分け）
  どれを採用するかは authcore 側の `refresh` エラー仕様を再確認した上で決める。
- **clear 時にトーストで「セッションが切れました。再度ログインしてください」を表示するか**:
  - shared 層からは出せないので、SessionStore に「last clear reason」フラグを
    持たせて UI 側で観測する仕組みにするか、既存の `SessionState.Unauthenticated` に
    任意 `reason` フィールドを生やすか、もしくは別途 oneshot Flow を生やす。
  - **本タスクでは扱わず**、`client-bank-18` で UI 側の reset と合わせて検討する。
- **`tokenStorage.clear()` の責務**:
  - 現状 `AuthTokenRefresher` 内で `tokenStorage.clear()` を呼ぶか、
    `SessionStore.clear()` 内で TokenStorage を巻き込むかの設計判断。
  - SessionStore に TokenStorage を inject する選択肢もあるが、関心が広がりすぎるため
    本タスクでは `AuthTokenRefresher` 内で並列に呼ぶ方針。再考の余地あり。
