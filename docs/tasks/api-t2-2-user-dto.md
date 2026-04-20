# T2-2: UserDto

## 概要

`POST /users` と `GET /users/:id` の request / response DTO を定義する。

## 背景・目的

残高ダッシュボードの基礎データ。`balance_fuju` は整数（bigint）で返るため `Long` で受ける。

## 影響範囲

- モジュール: shared
- ソースセット: commonMain
- 破壊的変更: なし
- 追加依存: なし

## 実装ステップ

1. `shared/src/commonMain/.../data/remote/dto/UserDto.kt`:
   - `@Serializable data class CreateUserRequest(@SerialName("sub") val subject: String, ...)` — AuthCore `sub` を主キーにする想定に合わせる。
   - `@Serializable data class UserResponse(val id: String, @SerialName("balance_fuju") val balanceFuju: Long, @SerialName("created_at") val createdAt: String, ...)`
2. `commonTest` にシリアライズ確認テストを追加。

## 検証

- [ ] `./gradlew :shared:allTests`

## 依存

- T0-1, T0-2, T1-3

## 技術的な補足

- `balance_fuju` は `Long`（bigint）。クライアント側は小数計算に関与しない（README 参照）。
- `createdAt` は ISO8601 文字列。`kotlinx.datetime.Instant.parse` で変換する層は Repository（T4-2）で扱う。
