# T3-2: UserApi

## 概要

`POST /users`, `GET /users/:id`, `GET /users/:id/transactions` を呼ぶ API クライアントを実装する。

## 背景・目的

ダッシュボード（残高）と取引履歴画面が直接依存する最重要エンドポイント群。

## 影響範囲

- モジュール: shared
- ソースセット: commonMain
- 破壊的変更: なし
- 追加依存: なし

## 実装ステップ

1. `shared/src/commonMain/.../data/remote/api/UserApi.kt`:
   - `class UserApi(private val client: HttpClient)`
   - `suspend fun create(request: CreateUserRequest): NetworkResult<UserResponse>`
   - `suspend fun get(userId: String): NetworkResult<UserResponse>`
   - `suspend fun transactions(userId: String): NetworkResult<TransactionListResponse>`
2. `commonTest` に MockEngine ベースのテストを追加（正常系 + 404 / UNAUTHENTICATED）。

## 検証

- [ ] `./gradlew :shared:allTests`

## 依存

- T1-1, T1-3, T2-2, T2-3

## 技術的な補足

- `client` の `defaultRequest` で Bearer トークンが付与される前提なので、このクラスは認証を意識しない。
- URL パラメータは `client.get("/users/${userId}/transactions")` でベース URL からの相対パスで書く（`HttpClient` の defaultRequest で baseUrl 設定済み）。
