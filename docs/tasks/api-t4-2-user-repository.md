# T4-2: UserRepository + userModule

## 概要

`UserApi`（T3-2）をラップし、ドメインモデル（`User` / `Transaction`）への変換と `Flow` 化を行う Repository を追加する。対応する Koin module も定義する。

## 背景・目的

UI 層が DTO を直接扱わないようにするための境界。バックエンド側の JSON フィールド名変更を shared 層で吸収する。

## 影響範囲

- モジュール: shared
- ソースセット: commonMain
- 破壊的変更: なし
- 追加依存: なし

## 実装ステップ

1. `shared/src/commonMain/.../domain/model/User.kt`:
   - `data class User(val id: String, val balanceFuju: Long, val createdAt: Instant)`
   - `data class Transaction(val id: String, val kind: TransactionKind, val amount: Long, val counterpartyUserId: String?, val artifactId: String?, val occurredAt: Instant)`
2. `shared/src/commonMain/.../data/repository/UserRepository.kt`:
   - `class UserRepository(private val userApi: UserApi)`
   - `suspend fun create(subject: String): NetworkResult<User>`
   - `suspend fun get(userId: String): NetworkResult<User>`
   - `suspend fun transactions(userId: String): NetworkResult<List<Transaction>>`
   - DTO → ドメイン変換ロジックをファイル内 private 拡張関数で実装。
3. `shared/src/commonMain/.../di/UserModule.kt`:
   - `val userModule = module { single { UserApi(get()) }; single { UserRepository(get()) } }`
4. `commonTest` に変換ロジックのテストを追加。

## 検証

- [ ] `./gradlew :shared:allTests`

## 依存

- T3-2

## 技術的な補足

- `createdAt` / `occurredAt` は `kotlinx.datetime.Instant.parse` で変換。
- `counterpartyUserId` は mint の場合は null、transfer の場合は相手 user id。`TransactionDto.fromUserId` / `toUserId` から自分基準で決定する（`myUserId` は呼び出し側から受ける必要があるため、引数追加を検討）。
