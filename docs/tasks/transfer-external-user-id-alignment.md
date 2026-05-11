# `/users/search` / `/ledger/transfer` の external_user_id 移行追従

## 概要

bank-backend develop に取り込まれた **PR #101 (`users-search-authcore-delegation`)** で
`GET /users/search` のレスポンス `id` が **bank PK (bigint) → AuthCore ULID (string, 26 文字 = `external_user_id`)** に変わった。
クライアントは現状 `UserSearchResultDto.id: Long` を前提にしているため、release ビルドで search API
を叩いた瞬間に JSON deserialization で落ちる状態にある。本タスクではこの DTO / セッション / 入力バリ
デーションをまとめて ULID 中心に揃え、送金フロー (`client-bank-22`) を develop に対して再び動く状態に戻す。

本タスクは **バックエンドの先行タスク** （`/ledger/transfer` が `to_user_id` / `from_user_id` を
`external_user_id` (ULID) で受け取るように `/ledger/mint` 同等に揃える対応）が develop にマージ
されたことを **前提条件** とする。先行タスクが未マージの段階では本タスクの実装ステップを進めない。

なお `client-bank-22-money-transfer` は 2026-05-10 に PR #92 で main にマージ済み。
そのため本タスクは「`client-bank-22` の Phase 内追加」ではなく **main から独立した追従タスク**
として起票し、CLAUDE.md のブランチ運用に従って `feature/<task-id>-transfer-external-user-id-alignment`
を main から切る。

## 背景・依存関係

- PR #99 (`users-search-by-public-id`) の追従はクライアント側 `6a9b1a0`「公開ID中心に切替」で完了済み。
  ただし `id` の意味は **bank 内部 PK のまま** で、`UserSearchResultDto.id: Long` / テスト fixture
  `"id": 21` の前提だった。
- PR #101 で `GET /users/search` は AuthCore `/v1/users/search` 委譲に切り替わり、レスポンス `id` は
  AuthCore の `id` (= ULID, = bank の `external_user_id`) になる。
  bank-backend `UsersController#search` が `Authcore::UserSearchClient` の payload を素通しする
  実装に変わっているため、bank 側で再変換は入らない (`fuju-bank-backend/app/controllers/users_controller.rb`)。
- 一方 `LedgerController#transfer` は develop 時点で **依然 `User.find(to_user_id)`**（bank PK 前提）
  のまま。search → transfer をそのまま繋ぐと型不一致で 404 / 例外になる。
  そのため **バックエンドで `/ledger/transfer` を ULID 受け入れに揃える先行タスクが必要**
  （`/ledger/mint` の `resolve_recipient!` 流用が筋。`UserProvisioner.call(external_user_id:)` で
  ULID → bank `User` を解決する経路はすでに mint で確立されている）。

依存関係:

```
[backend] /ledger/transfer の external_user_id 化  ─┐
                                                   └→ [client] 本タスク（DTO/Session/入力ガード/テスト）
[backend] PR #101 (users-search-authcore-delegation)─┘
```

## スコープ

### 含む

- `UserSearchResultDto.id` の型を `Long` → `String` に変更（ULID 文字列）
- `UserRepository.searchByPublicId` の domain 変換から不要な `toString()` を削除
- `SessionStore` に格納する `userId` の意味を **bank PK → external_user_id (ULID)** に切替
  - `LoginViewModel` / `SignUpFlowViewModel` / `MfaVerifyViewModel` / `AuthFlowIos` の
    `sessionStore.setAuthenticated(...)` 呼び出し箇所をすべて見直す
  - `POST /users/me` の `UserResponse` から取得できる識別子と、AuthCore access_token の `sub`
    どちらを源泉にするかをコード上で明示する（後述「未決事項」参照、現状は `UserResponse.sub` が
    nullable のため AuthCore 側を一次ソースに倒す可能性が高い）
- `SendFlowViewModel` の検索クエリ入力バリデーション強化:
  - サーバ側仕様 `/\A[a-zA-Z0-9]+\z/` と `2..32` 文字に合わせ、ViewModel で前段ガード
  - UI に「英数字のみ / 最大 32 文字」のヒント or 入力フィルタを追加（Android / iOS 双方）
- 既存テスト fixture の更新:
  - `UserRepositoryTest` の `"id": 21` 等の整数を ULID 文字列に差し替え
  - `SessionStoreTest` の `setAuthenticated(userId = "7")` を ULID に差し替え
  - `LedgerApiTest` の `toUserId = "usr_to"` 等は意味的に既に String なので形だけ ULID に揃える
- README / `client-bank-22` 計画書の DTO 表 / フロー図を ULID 前提に追記（差分のみ）

### 含まない

- バックエンドの `/ledger/transfer` 改修自体（本タスクの **前提条件**、別タスク）
- `serialize_user` / `UserResponse` 側に AuthCore の `sub` を返すかどうかの設計（必要なら別タスクで起票）
- 送金時 MFA step-up（`server-bank-23` 系）の配線
- `bank.users.public_id` キャッシュ運用の将来削除（`users-search-cross-service-identity.md` 後続）

## 現状ギャップ（コードベース根拠）

| 項目 | 現状 | あるべき姿 |
|---|---|---|
| `UserSearchResultDto.id` 型 | `Long`（`shared/src/commonMain/.../dto/UserSearchDto.kt:19`） | `String`（ULID） |
| `UserRepository.toDomain()` | `id = id.toString()` で Long → String（`.../repository/UserRepository.kt:111`） | `id = id`（既に String） |
| `UserRepositoryTest` モック | `"id": 21` / `"id": 7` 等の整数（`.../UserRepositoryTest.kt:360, 428`） | `"01HX4T8K7N9P2QABC0DEF12345"` 形式 |
| `SessionStore.userId` | `provision.value.id`（bank PK の `.toString()`、`LoginViewModel.kt:98` 等） | AuthCore `sub`（ULID） |
| `SendFlowViewModel` クエリガード | `MIN_SEARCH_LENGTH = 2` のみ（`.../SendFlowViewModel.kt:264`） | `2..32` + `[a-zA-Z0-9]` |
| `LedgerController#transfer` の `to_user_id` 受け入れ | bank PK（develop） | external_user_id ← **前提タスクで対応** |

## 影響範囲

### モジュール / ソースセット

- `shared/src/commonMain/.../data/remote/dto/UserSearchDto.kt` — DTO 型変更
- `shared/src/commonMain/.../data/repository/UserRepository.kt` — `toDomain()` 変換調整
- `shared/src/commonTest/.../data/repository/UserRepositoryTest.kt` — fixture 差し替え
- `shared/src/commonTest/.../session/SessionStoreTest.kt` — `setAuthenticated` の userId を ULID に
- `shared/src/commonTest/.../data/remote/api/LedgerApiTest.kt` — `usr_to` 等を ULID 体裁に
- `shared/src/iosMain/.../session/AuthFlowIos.kt` — `setAuthenticated(provision.value.id)` 経路
- `composeApp/src/androidMain/.../features/auth/LoginViewModel.kt` — 同上
- `composeApp/src/androidMain/.../features/auth/MfaVerifyViewModel.kt` — 同上
- `composeApp/src/androidMain/.../features/signup/SignUpFlowViewModel.kt` — 同上
- `composeApp/src/androidMain/.../features/send/SendFlowViewModel.kt` — クエリ入力ガード
- `composeApp/src/androidMain/.../features/send/SendRecipientScreen.kt` — 入力 UI ヒント
- `iosApp/iosApp/Features/Send/...` — iOS 側の検索入力フィルタとヒント
- `docs/tasks/client-bank-22-money-transfer.md` — 「公開ID = ULID」前提の追記（軽い差分）

### 公開 ABI / 既存ユーザーへの影響

- `shared` framework の公開 shape: `UserSearchResult.id: String` は変わらない（型は元から String）
- `SessionStore` に保存される `userId` の **意味** が bank PK → ULID に変わるため、
  既存ユーザーの起動時に SessionStore へ持ち越されている値が bank PK のままだとミスマッチが発生する。
  → KeychainTokenStorage / SharedPreferences に永続化された userId は **アプリ起動時に
  `/users/me` 等で再取得して上書きする** ルートで吸収する（再ログイン強制は避ける）。
- Notion task DB のクライアントタスクとして `銀行アプリクライアント：/users/search の external_user_id 移行追従`
  で起票（メモ欄に本ファイルのパス）。

## 実装ステップ

### A. DTO / Repository / テスト fixture（shared）

1. `UserSearchDto.kt`:
   - `val id: Long` → `val id: String`
   - コメントを「AuthCore の ULID (= bank の external_user_id)。`POST /ledger/transfer` の `to_user_id` に渡す」へ
2. `UserRepository.toDomain()`: `id = id.toString()` を `id = id` に
3. `UserRepositoryTest`:
   - `"id": 21` → `"id": "01HX4T8K7N9P2QABC0DEF1Y0K1"` のような Crockford Base32 26 文字
   - `setAuthenticated(userId = "7")` → ULID に差し替え、`searchByPublicId_excludes_self_when_authenticated`
     の assertion も ULID 比較に
4. `SessionStoreTest`: 永続化 / 復元アサーションが文字列長前提になっていないか確認、必要なら ULID に差し替え

### B. セッション ID の意味切替（shared / composeApp / iosApp）

5. `POST /users/me` レスポンスから AuthCore `sub` を読める経路があるか確認:
   - `UserResponse.subject: String?` （`UserDto.kt:20`、現行 nullable）が ULID を返してくれるなら
     ここを `setAuthenticated` の引数に使う
   - 返してくれないなら、AuthCore `/v1/user/profile` レスポンスから取って渡す（`AuthCoreUserResponse` 経由）
6. `LoginViewModel` / `MfaVerifyViewModel` / `SignUpFlowViewModel` / `AuthFlowIos` の
   `sessionStore.setAuthenticated(...)` 呼び出しを 5 の決定に合わせて切替
7. `SendFlowViewModel.submit()` で使う `from = sessionStore.userId` が ULID であることを前提に、
   `LedgerApi.transfer` に渡す `fromUserId` も ULID として扱う

### C. 検索クエリ入力ガード（composeApp / iosApp）

8. `SendFlowViewModel` に `SEARCH_MAX_LENGTH = 32` と `SEARCH_REGEX = Regex("^[a-zA-Z0-9]+$")` を追加
9. `onQueryChange` で長さ超過時は切り詰め or 弾く、非英数字はフィルタ
10. `SendRecipientScreen`（Android） / iOS の Send recipient view で入力ヒント「英数字 2〜32 文字」
    を表示。プレースホルダ / supporting text のみで、エラー表示は MIN/MAX を割った時の既存挙動踏襲
11. 既存の `SendFlowViewModel.MIN_SEARCH_LENGTH` と統一して定数を `companion object` にまとめる

### D. 動作確認 / 検証

12. `./gradlew build`
13. `./gradlew :shared:allTests`
14. `./gradlew :composeApp:assembleDebug`
15. `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`（macOS のみ）
16. Android / iOS 両端末で送金フローを通す:
    - 検索: 英数字 2 文字以上で候補が出る、非英数字 / 33 文字以上は入力時点で弾かれる
    - 候補選択 → 確認 modal → 金額入力 → 確認 dialog → 送金成功（残高反映）
    - 送金後の取引履歴に新しい transfer 行が出る
17. README / `client-bank-22` 計画書に「`id` は ULID」前提を追記

## テスト方針

- **DTO デシリアライズ**: ULID 文字列がそのまま `UserSearchResultDto.id` に入ること
- **search → transfer 経路**: ULID を `to_user_id` に詰めて backend に届くこと
  （`LedgerApiTest` で request body 内容を `usr_to` から ULID 体裁に差し替えた上で assertion）
- **入力ガード**: `SendFlowViewModel` の単体テストを追加し、
  - `qr-2 文字未満` → `NeedsMoreChars`
  - `33 文字以上` → 切り詰め or 弾く
  - `日本語 / 記号` → 弾く
  - `English_alnum_2_32_chars` → 通る
- **セッション意味切替**: `SessionStoreTest` で「永続化された旧 bank PK 値が次回起動時に
  `/users/me` で上書きされる」ケースを再現する単体テストを追加するかは要相談（後述「未決事項」）

## 完了条件

1. develop の `/users/search` を実際に叩いてレスポンスが domain `UserSearchResult` まで通る
2. 検索結果から選んだ候補で送金できて、相手の残高 / 自分の残高 / 取引履歴が正しく反映される
3. Android / iOS 両方で 1〜2 が確認できる
4. `./gradlew build` / `:shared:allTests` / `:composeApp:assembleDebug` / iOS framework link がすべて green
5. CLAUDE.md / README / `client-bank-22-money-transfer.md` に「`id` は ULID」前提が反映済み
6. Notion タスクのステータスが「完了」、メモ欄に PR URL

## 未決事項

1. **`SessionStore.userId` の源泉**
   - `UserResponse.subject: String?` が ULID を返すなら `POST /users/me` 経由で取れる
   - 返さないなら AuthCore `/v1/user/profile` を別途叩いて取る or 「access_token を decode して sub を読む」
   - **推奨**: バックエンドが `UserResponse` に `sub`（ULID）を非 null で返すよう揃えるのが筋。要 backend 確認、必要なら別タスク起票
2. **入力フィルタの強さ**
   - 「日本語入力中に英数字以外を弾く」を厳密にやると IME composition と相性が悪い
   - 妥協案: `onQueryChange` 時点では allowlist 違反のみハイライト表示し、検索 trigger 段階で
     正規表現を満たす場合だけ API 発火
3. **既存ユーザーの SessionStore 上書きタイミング**
   - 単純には `RootScaffold` 起動時の `getMe()` で上書きすればよいが、テストで再現するかは選択的

## 関連タスク

- 先行（バックエンド）: `/ledger/transfer` を `external_user_id` 受け入れに揃える（別ブランチで起票予定）
- 関連: `docs/tasks/client-bank-22-money-transfer.md`（本タスクが追従対象、PR #92 で 2026-05-10 main にマージ済み）
- 関連: `fuju-bank-backend/docs/tasks/users-search-cross-service-identity.md`（cross-service identity の長期方針）
- 関連: `fuju-bank-backend/docs/tasks/server-bank-23-transfer-mfa-verify-flow.md`（MFA step-up は別動線）
