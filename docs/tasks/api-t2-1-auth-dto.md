# T2-1: AuthDto（AuthCore login / refresh）

## 概要

AuthCore の login / token refresh エンドポイントの request / response DTO を kotlinx.serialization で定義する。

## 背景・目的

`AuthApi`（T3-1）で使う型を先に固めることで、Repository 実装の待ち時間をなくす。他の DTO タスクと完全並行可能。

## 影響範囲

- モジュール: shared
- ソースセット: commonMain
- 破壊的変更: なし
- 追加依存: なし

## 実装ステップ

1. `shared/src/commonMain/.../data/remote/dto/AuthDto.kt`:
   - `@Serializable data class LoginRequest(val email: String, val password: String)`
   - `@Serializable data class RefreshRequest(val refreshToken: String)`
   - `@Serializable data class TokenResponse(val accessToken: String, val refreshToken: String, val subject: String, val expiresIn: Long)` — フィールド名は AuthCore 側の snake_case に合わせて `@SerialName("access_token")` 等を付与。
2. `commonTest` に各 DTO のシリアライズ/デシリアライズのラウンドトリップテストを追加。

## 検証

- [ ] `./gradlew :shared:allTests`

## 依存

- T0-1, T0-2, T1-3

## 技術的な補足

- AuthCore の正確なレスポンス仕様は別リポジトリ依存。未確定のフィールドがあれば `@OptIn`/`nullable` で吸収する。
- `subject` は ULID（26 文字）固定長であることをコメントで明記する。
