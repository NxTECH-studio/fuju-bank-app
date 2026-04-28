# post-env-switch-roadmap: env 切り替え後に本番リリースまで必要な実装計画（app 側）

## 概要

`prod-base-url-env-switch.md` で BuildKonfig による `BANK_API_BASE_URL` / `CABLE_URL` の切替は完了。一方 iOS / Android のどちらも UI は実質スモークテストボタン (`Click me!` / `Smoke test: UserApi.get`) しか存在しない。本ドキュメントは「shared 層は揃っている」状態から本番リリースまでの残タスクを依存順で並べた実装計画書。

各タスクは原則 1 PR = 1 サブドキュメント (`docs/tasks/A*-*.md`) に分割して `/start-with-plan` で流す前提で章立てしている。

## AuthCore の現状（前提・重要）

> 認証基盤は別リポジトリで実装済み。詳細は `/Users/ryota/Documents/works/proj-fuju/fuju-system-authentication/README.md`。

- **実体**: Go 製の独立サービス (`fuju-system-authentication`)。ローカルは `:8080`、本番予定は `https://authcore.fujupay.app`（仮、backend B4 で確定）。
- **エンドポイント**: `POST /v1/auth/register` / `/v1/auth/login` / `/v1/auth/refresh` / `/v1/auth/logout` / `/v1/auth/mfa/{register,enable,disable,verify}` / `/v1/auth/{connect,callback,disconnect}/:provider` / `/v1/user/{profile,public_id,icon}`。
- **ログイン**: `POST /v1/auth/login` body `{ identifier, password }`（メール **or 公開ID**）→ 200 で `{ access_token, token_type, expires_in }` + `Set-Cookie: refresh_token=...`（**MFA 無し**）または `{ pre_token, mfa_required: true, expires_in }`（**MFA 有り**、続けて `/v1/auth/mfa/verify` を呼ぶ）。
- **Refresh Token は HttpOnly cookie 配送** (`Path=/v1/auth; SameSite=Lax; Secure; Max-Age=2592000`)。**ボディには含まれない**。
- **JWT**: RS256 / `aud=authcore` / `iss=authcore` / `sub` = ULID 26 文字 / `type=access` / TTL 15 分。
- **`mfa_verified` の semantics** (api-summary §4.4): per-token / per-session のフラグ。「`false` を MFA 未設定と解釈してはいけない」。同じユーザーでも別 token family なら値が異なる。
- **AuthCore も独自の user 情報を持つ**: `GET /v1/user/profile` で `{ id, email, public_id, icon_url, mfa_enabled, created_at }` が取れる。bank の `User` (balance_fuju, name, public_key) とは別物 — A3 で 2 系統を merge する設計。
- **既存 app 実装との致命的な乖離（A2 で全部直す）**:
  - `shared/.../data/remote/api/AuthApi.kt`: `/sessions` `/sessions/refresh` を叩いている → 実際は `/v1/auth/login` `/v1/auth/refresh`
  - `shared/.../data/remote/dto/AuthDto.kt`: `LoginRequest { email, password }` → 実際は `{ identifier, password }`。`TokenResponse` が `refresh_token` をボディで持つ前提 → 実際はボディに無い（cookie）。
  - 上記は A2 着手時に **DTO + AuthApi + AuthRepository + テスト** をまとめて差し替える。

## バックエンド側の現状（前提）

> 本ロードマップを進める上で前提となる backend 側の実態。詳細は `fuju-bank-backend/docs/tasks/post-env-switch-roadmap.md` を参照。

- **本番は既に稼働中**: `https://api.fujupay.app` / `wss://api.fujupay.app/cable` は GitHub Actions の `cd.yml` で main push 時に Proxmox CT へ自動デプロイされている。`/up` は 200 を返す。
- **未完了（バックエンド側 B1〜B5）**:
  - **B1**: ActionCable Connection の JWT 認証（現状 `params[:user_id]` チェックのみ）
  - **B2**: `Users#create` の lazy provisioning 化（現状 `external_user_id` を body で受け取る暫定実装）
  - **B3**: CORS の方針決定
  - **B4**: AuthCore サーバ実体の整備（鍵発行 / `AUTHCORE_BASE_URL` 確定）
  - **B5**: 本番 ENV (AUTHCORE_*) を `cd.yml` / `compose.prod.yml` に注入
- **アプリ側に効いてくるタイミング**:
  - **A2 (ログイン)**: B1 + B2 + B4 + B5 が本番で揃う前は実 AuthCore に繋がらない。Debug ビルドでローカル AuthCore モックに繋いで先行開発するのは可能。
  - **A6 (リアルタイム HUD)**: B1 が本番で揃わないと release ビルドで安全に有効化できない（他人の HUD を覗ける状態）。
  - **A1 (AUTHCORE_BASE_URL の BuildKonfig 化)**: backend B4 で本番 URL（仮: `https://authcore.fujupay.app`）が確定したら release 値を更新する。Debug 値は先に決めて入れて OK。

## 並列セッション（app セッション / backend セッション）作業ガイド

> Claude Code を 2 セッション並列で動かす場合の役割分担と同期ポイント。

- **app セッション（このドキュメント）が単独で進められるタスク**:
  - A1（Phase 0）/ A8 / A9 → backend / AuthCore と一切同期不要。
  - **A2a（shared 層改修）→ ローカル AuthCore (`:8080`) を立てれば単独で完了可能**。
  - A3 / A4 / A5 / A7 の **画面とコンポーネント実装** → モック Repository or Debug ビルドのスモーク状態で開発を進められる。本番疎通は B 系完了後に確認。
- **backend セッションの完了を待つタスク**:
  - A2b の本番疎通テスト → B2 (`POST /users/me`)
  - A6（release で有効化）→ B1（subprotocol で JWT を載せる仕様確定）
- **同期点（両セッションで合意が必要）**:
  - **AUTHCORE_BASE_URL 値**: backend B4 で確定する `https://authcore.fujupay.app` を A1 の release 値として共有。
  - **JWT を Cable に乗せる方法**: backend B1 で `Sec-WebSocket-Protocol: bearer, <jwt>` を採用予定 → app A6 の `UserChannelClient` を Ktor の `WebSockets` で `request { headers.append("Sec-WebSocket-Protocol", "bearer, $jwt") }` する形に直す。
  - **`POST /users/me` の I/O 仕様**: backend B2 で確定 → app 側 A2b のログイン直後呼び出しで合わせる。
- **コンフリクト回避**:
  - `shared/` モジュールは原則 app セッションのみが触る。backend セッションは触らない。
  - DTO / API クライアント (`shared/.../data/remote/`) はバックエンドの I/O 仕様変更に追従する必要があるので、B2 で `POST /users/me` の I/O が変わるなら app セッションが追従 PR を出す（backend セッションは Rails 側のみ修正）。
  - **AuthCore (`fuju-system-authentication`) は両セッションともに「読み取り専用の前提」として扱う**。仕様変更が必要なら別 PR を AuthCore リポジトリに出す（このロードマップの範囲外）。

## 依存関係サマリ

```
A1 (AUTHCORE_BASE_URL を BuildKonfig 化)
  └→ A2a (shared 層を AuthCore 実 API に合わせる)
       └→ A2b (ログイン UI) ─┬─→ A3 (残高/プロフィール) ─→ A5 (送金画面)
                              ├─→ A4 (取引履歴)
                              └─→ A6 (リアルタイム HUD) ─→ A7 (Artifact 投稿)

  A8 (iOS Build Configuration / Scheme)         並行可能 ─→ 本番リリース
  A9 (CI で release build スモーク)
```

- **A2a は単独で進められる**（AuthCore はローカル `:8080` で立てて検証可能、backend 待ち不要）。
- **A2b の本番疎通は backend B2 (`POST /users/me`) 完了待ち**。AuthCore のみで login 自体は通る。
- **A6 は backend B1 (Cable JWT) 完了待ち**。
- **A1 は backend B4 で AuthCore の本番 URL が確定したら release 値を更新**（暫定値で先行 OK）。

---

## 実装順序（Phase 別）

### Phase 0: 設定の最終仕上げ（先に潰しておく小タスク）

#### A1. AUTHCORE_BASE_URL を BuildKonfig に寄せる

- **Why**: 現状 `shared/.../data/remote/NetworkConstants.kt:4` に `https://authcore.fuju-bank.local` をハードコード。`prod-base-url-env-switch.md` で意図的にスキップしたが、ログイン画面 (A2) で実 URL に向ける前にここを埋めないと release ビルドが事故る。
- **対象ファイル**:
  - `shared/build.gradle.kts`（buildkonfig ブロックに `AUTHCORE_BASE_URL` を追加）
  - `shared/src/commonMain/.../data/remote/NetworkConstants.kt`（削除 or `BuildKonfig.AUTHCORE_BASE_URL` 参照に差し替え）
  - `shared/src/commonMain/.../di/AuthModule.kt`（`NetworkConstants.AUTHCORE_BASE_URL` 参照箇所）
  - `shared/src/commonMain/.../di/BuildConfigFacade.kt`（`defaultAuthCoreBaseUrl()` を追加）
- **実装ポイント**:
  - debug: `http://10.0.2.2:9000`（Android）/ `http://localhost:9000`（iOS）など、AuthCore のローカル待受ポートに合わせる。**backend 側 B4 で実体が決まったら同期する**。
  - release: backend 側で確定する `https://authcore.fujupay.app`（仮）。**この値は backend B4 が確定値を出すまで暫定。確定後に PR を 1 本追加で当てる前提で OK**。
  - **並列セッション運用**: A1 自体は backend 完了を待たずに着手できる。release 値が暫定でも release ビルド自体は通るので CI まで先に整える。
- **検証**: `./gradlew :shared:generateBuildKonfig` 成功。Debug/Release それぞれで `BuildKonfig.AUTHCORE_BASE_URL` が期待値になる。

### Phase 1: 認証フロー — shared 層改修（A2a）と UI 実装（A2b）

> AuthCore の実 API と既存 shared コードに乖離がある。まず shared 層を実態に合わせて (A2a)、その上に UI を載せる (A2b) の順で進める。
> backend B1 / B2 が揃ってから本番疎通可能だが、AuthCore (`fuju-system-authentication`) はローカル `:8080` で立ち上げて先行開発できる。

#### A2a. shared 層を AuthCore 実 API に合わせる（最優先）

- **Why**: 既存 `AuthApi.kt` / `AuthDto.kt` は `/sessions` + body refresh_token 前提で書かれており、AuthCore の実 API (`/v1/auth/login` + cookie refresh) と完全に不整合。UI を載せる前にここを直す。
- **対象ファイル**:
  - `shared/.../data/remote/api/AuthApi.kt` を全面改修（`/v1/auth/login`, `/v1/auth/refresh`, `/v1/auth/logout`, `/v1/auth/mfa/verify`）
  - `shared/.../data/remote/dto/AuthDto.kt` を全面改修:
    - `LoginRequest { identifier, password }`
    - `TokenResponse { access_token, token_type, expires_in }`（**refresh_token は持たせない**）
    - `PreTokenResponse { pre_token, mfa_required, token_type, expires_in }`
    - `MfaVerifyRequest { code? , recovery_code? }`
    - login レスポンスは sealed/Either で 2 系統を表現 (`LoginResult.NeedsMfa | LoginResult.Authenticated`)
  - `shared/.../network/HttpClientFactory.kt` に **Ktor `HttpCookies` plugin** を追加し、cookie storage を `expect/actual` で:
    - Android: EncryptedSharedPreferences-backed `CookiesStorage`
    - iOS: Keychain-backed `CookiesStorage`
  - `shared/.../auth/TokenStorage.kt` の責務を整理:
    - access_token / mfa_verified flag / token_family のみ保持
    - refresh_token 自体は cookie storage に委ねる（手で持たない方針）
  - `shared/.../data/repository/AuthRepository.kt` の `login()` を `LoginResult` を返すよう改修。
  - `shared/.../data/remote/dto/AuthCookieDto.kt`（新規・必要なら cookie 永続化用の serializable wrapper）
- **判断ポイント**:
  - **ネイティブで cookie を扱う方針確認**: AuthCore 側にネイティブ向けの「refresh をボディで返すモード」は無い。Ktor の `HttpCookies` で cookie jar を持つのが標準解。`Path=/v1/auth` 制限があるので /v1/auth/* 系のリクエストでのみ自動付与される（bank API には漏れない）。
  - cookie 永続化先は **EncryptedSharedPreferences / Keychain**。プレーンディスクに置かない。
- **テスト**: `AuthRepositoryTest` を `Set-Cookie` ヘッダの mock 応答ベースに書き直す（`MockEngine` で `Set-Cookie` をセットして cookie storage に乗ることを検証）。

#### A2b. ログイン画面 UI（iOS / Android 両方）

- **前提**: A2a 完了。
- **対象ファイル**:
  - 新規 iOS: `iosApp/iosApp/Features/Auth/LoginView.swift`, `LoginViewModel.swift`, `MfaVerifyView.swift`
  - 新規 Android: `composeApp/.../features/auth/LoginScreen.kt`, `LoginViewModel.kt`, `MfaVerifyScreen.kt`
  - shared: `SessionStore`（StateFlow<SessionState>: `Unauthenticated` | `MfaPending(preToken)` | `Authenticated(userId)`）を `commonMain` に新規作成。
- **実装ポイント**:
  - 入力欄ラベルは「**メールアドレス または 公開ID**」（identifier の説明）。
  - ログイン成功（MFA 無し）→ access_token を TokenStorage に保存 → `SessionStore.state = Authenticated` → ホーム遷移。
  - ログイン成功（MFA 有り）→ pre_token を一時保管（メモリ上でも可、10 分 TTL なので Keychain まで要らない）→ `MfaPending` 画面へ → `/v1/auth/mfa/verify` で TOTP 6 桁 or recovery code を送信 → 成功で `Authenticated` へ。
  - エラー: `INVALID_CREDENTIALS` (401) / `ACCOUNT_LOCKED` (429) / `MFA_REQUIRED` (403) / `RATE_LIMIT_EXCEEDED` (429) を画面でハンドル。`ApiErrorCode` の enum をこれら向けに拡張する（A2a で同梱）。
  - Compose / SwiftUI どちらも MVVM 風に「ViewModel が shared の Repository を持つ」構造で揃える。
- **lazy provisioning との接続**:
  - `Authenticated` 直後に `POST /users/me` を必ず叩く（backend B2）。これで bank 側に User レコードが無くても初回ログインで作られる。
  - レスポンスの `id` を SessionStore に持つ（ULID = AuthCore の sub と同じ）。
- **同期点**: backend `B1` (Cable JWT) はホーム以降で必要、ログイン自体はバックエンド `B2` (`POST /users/me`) のみ依存。

### Phase 2: メイン機能 UI（A3〜A7・A2 完了後は A3/A4/A6 並行可能）

#### A3. 残高 / プロフィール画面（ホーム）

- **対象**:
  - iOS: `Features/Home/HomeView.swift`, `HomeViewModel.swift`
  - Android: `features/home/HomeScreen.kt`, `HomeViewModel.kt`
- **データソース**: `UserRepository.get(currentUserId)` → `User.balanceFuju` / `name` / `publicKey` を表示。
- **UI**: 残高（fuju 単位）を大きく出す + プロフィール簡易表示。Pull-to-refresh で再取得。

#### A4. 取引履歴画面

- **対象**:
  - iOS: `Features/Transactions/TransactionListView.swift`, `TransactionListViewModel.swift`
  - Android: `features/transactions/TransactionListScreen.kt`, ViewModel。
- **データソース**: 既存の transactions API（shared 側に既存）。ページング対応は将来。MVP は最新 N 件。
- **UI**: kind (`mint` / `transfer_in` / `transfer_out`) でアイコン分け、相手・額・日時を 1 行で表示。

#### A5. 送金画面

- **対象**:
  - iOS: `Features/Send/SendView.swift`, `SendViewModel.swift`
  - Android: `features/send/SendScreen.kt`, ViewModel。
- **データソース**: `LedgerRepository.transfer(...)`。
- **実装ポイント**:
  - 送金先を `external_user_id` か QR か（QR は `qr-payment-foundation-mpm` 系で別途）で受け取るか UI 仕様確定が必要 → MVP は手入力で OK。
  - 二重送信防止のため Submit 中はボタン無効 + Idempotency-Key を渡す（backend 側既存）。
  - 成功後は A3 の残高と A4 の履歴を再取得（`SessionStore` に refresh トリガを置く形）。

#### A6. リアルタイム HUD（UserChannel 接続）

- **Why**: 入金通知 / 送金成功通知をリアルタイムで反映するために UserChannel を購読。
- **対象**:
  - shared: 既存 `UserChannelClient` / `RealtimeRepository` を使用。
  - iOS: `Features/Home/HomeView.swift` に SwiftUI Task で `RealtimeRepository.events.collect { ... }`。
  - Android: ホーム ViewModel の coroutine スコープで collect。
- **同期点**: backend `B1` 完了が前提（JWT 認証なしの WebSocket だとリリースに乗せられない）。`UserChannelClient` は subprotocol または query で JWT を渡すように書き換える（B1 の決定に合わせる）。

#### A7. Artifact 投稿画面

- **Why**: アプリのコア機能の片翼。感情を担保とする artifact を投稿して mint をトリガする UI。
- **対象**:
  - iOS / Android で `Features/Artifact/PostArtifactView/Screen` を新規。
  - shared: 既存 `ArtifactRepository.create(...)` を呼ぶ。
- **MVP**: テキスト + メタデータのみ。画像・添付は後続。

### Phase 3: 配布前のリリース整備（A2 以降と並行可能）

#### A8. iOS Build Configuration / Scheme 分割

- **Why**: KMP 側は `assembleRelease` / `linkRelease*` で flavor=release が走るが、Xcode 側で Debug/Release の Bundle ID・App Icon・xcconfig を切る運用が未整備。TestFlight 配布する直前に必ず必要。
- **対象ファイル**:
  - `iosApp/Configuration/`（既存・拡張）
  - `iosApp.xcodeproj/project.pbxproj`
- **実装ポイント**:
  - Bundle ID を Debug = `studio.nxtech.fujubank.dev`, Release = `studio.nxtech.fujubank` のように分ける。
  - Release scheme で KMP の Release framework を使うようにビルドフェーズを確認（BuildKonfig が release 値を埋めているか）。
  - App Icon を Debug でオーバーレイして取り違え事故を防ぐ。

#### A9. CI で release build スモーク

- **Why**: Release flavor で本番 URL が確実に埋まっているかを CI で担保しないと、`-Pbuildkonfig.flavor=release` 付け忘れリグレッションが復活しうる。
- **対象**:
  - `.github/workflows/release-smoke.yml`（新規）
  - `./gradlew :composeApp:assembleRelease`（署名なし）
  - `./gradlew :shared:linkReleaseFrameworkIosArm64`
  - 生成物に対して `strings` で `api.fujupay.app` が含まれることを assert する簡易チェック。
- **既存タスク**: `setup-github-actions-ci.md` がベース。スモーク列を追加する形。

---

## 検証チェックリスト（リリース前）

- [ ] A1: Debug/Release ビルドそれぞれで `BuildKonfig.AUTHCORE_BASE_URL` が期待値
- [ ] A2: ログイン → トークン保存 → セッション復元（再起動後）まで iOS / Android 両方で通る
- [ ] A3: 残高が API レスポンスと一致、Pull-to-refresh で更新
- [ ] A4: 取引履歴が時系列で正しく表示、kind の分類が正しい
- [ ] A5: 送金成功で残高が減算、相手の残高が増えること（B6 と連動して E2E で確認）
- [ ] A6: 送金時に WebSocket 経由で残高更新通知が 5 秒以内に反映
- [ ] A7: Artifact 投稿が成功し、mint 結果が A6 経由で残高に反映
- [ ] A8: TestFlight に Debug / Release を別 Bundle ID で並列インストールできる
- [ ] A9: CI で Release ビルドが生成され、本番 URL が埋め込まれていることをスモーク

## サブタスク一覧（PR 単位）

凡例: 🟢 単独着手可能 / 🟡 ローカル前提整備で着手可能 / 🔴 他タスク完了待ち

### Phase 0
- 🟢 [A1: AUTHCORE_BASE_URL を BuildKonfig 化](./a1-authcore-base-url-buildkonfig.md)

### Phase 1
- 🟡 [A2a: shared 層を AuthCore 実 API に合わせる](./a2a-shared-authcore-api-align.md) — ローカル AuthCore 起動で先行可能
- 🔴 [A2b: ログイン画面 UI](./a2b-login-screen-ui.md) — A2a 完了後

### Phase 2（A2b 完了後、相互並列可能）
- 🟢 [A3: ホーム画面（残高 / プロフィール）](./a3-home-balance-profile.md)
- 🟢 [A4: 取引履歴画面](./a4-transaction-history.md)
- 🟢 [A5: 送金画面](./a5-send-screen.md)
- 🟡 [A6: リアルタイム HUD](./a6-realtime-hud.md) — backend B1 仕様確定後に subprotocol 確定

### Phase 3
- 🟢 [A7: Artifact 投稿画面](./a7-artifact-post.md) — A2b 完了後すぐ着手可
- 🟢 [A8: iOS Build Configuration / Scheme 分割](./a8-ios-build-configuration.md) — いつでも
- 🟢 [A9: CI で release build スモーク](./a9-ci-release-smoke.md) — いつでも

## 関連ドキュメント

- バックエンド側ロードマップ: `fuju-bank-backend/docs/tasks/post-env-switch-roadmap.md`
- 既存: `prod-base-url-env-switch.md`（Phase -1 として完了済み）
- 既存: `api-integration-task-split.md`（shared 層の前提整備、完了済み）
