# T1-3: 共通 API エラー型

## 概要

バックエンドの統一エラーレスポンス `{"error": {"code": "...", "message": "..."}}` をモデル化し、`ApiErrorCode` を enum で定義。さらに成功/失敗を表す `NetworkResult<T>` sealed class を提供する。

## 背景・目的

全 API レスポンスのエラーハンドリングを共通化し、Repository/ViewModel 層で `when (result)` で網羅的に処理できるようにする。MFA_REQUIRED など銀行特有のフロー分岐もここに集約。

## 影響範囲

- モジュール: shared
- ソースセット: commonMain
- 破壊的変更: なし
- 追加依存: なし

## 実装ステップ

1. `shared/src/commonMain/.../data/remote/ApiErrorCode.kt`:
   - `enum class ApiErrorCode { VALIDATION_FAILED, NOT_FOUND, INSUFFICIENT_BALANCE, UNAUTHENTICATED, TOKEN_INACTIVE, AUTHCORE_UNAVAILABLE, MFA_REQUIRED, UNKNOWN }`
   - `companion object { fun fromString(raw: String?): ApiErrorCode }`
2. `shared/src/commonMain/.../data/remote/ApiError.kt`:
   - `@Serializable data class ApiErrorEnvelope(val error: ApiErrorBody)`
   - `@Serializable data class ApiErrorBody(val code: String, val message: String)`
   - `data class ApiError(val code: ApiErrorCode, val message: String, val httpStatus: Int)`
3. `shared/src/commonMain/.../data/remote/NetworkResult.kt`:
   - `sealed class NetworkResult<out T> { data class Success<T>(val value: T) : NetworkResult<T>(); data class Failure(val error: ApiError) : NetworkResult<Nothing>(); data class NetworkFailure(val cause: Throwable) : NetworkResult<Nothing>() }`
   - `inline fun <T, R> NetworkResult<T>.map(transform: (T) -> R): NetworkResult<R>`
   - `suspend fun <T> runCatchingNetwork(block: suspend () -> T): NetworkResult<T>`（`HttpClient` 例外 → `ApiError` 変換）
4. `data/remote/` の marker は削除。
5. `commonTest` に `ApiErrorCode.fromString` の簡単なテストを追加。

## 検証

- [ ] `./gradlew :shared:build`
- [ ] `./gradlew :shared:allTests`

## 依存

- T0-1, T0-2

## 技術的な補足

- `runCatchingNetwork` は Ktor の `ClientRequestException` / `ServerResponseException` / `IOException` を捕捉し、`response.body<ApiErrorEnvelope>()` でエラー本文をデシリアライズする。
- `MFA_REQUIRED` は 403 ステータスで返る想定。Repository（T4-3 など）でこのコードを受けて MFA フロー `SharedFlow` を発火させる。
