# T2-5: LedgerTransferDto

## 概要

`POST /ledger/transfer` の request / response DTO を定義する。`Idempotency-Key` と MFA 連携を意識する。

## 背景・目的

送金フローの基礎型。リトライ時の重複防止（idempotency）、introspection + MFA 要求時のエラー分岐が特殊なので独立タスクにする。

## 影響範囲

- モジュール: shared
- ソースセット: commonMain
- 破壊的変更: なし
- 追加依存: なし

## 実装ステップ

1. `shared/src/commonMain/.../data/remote/dto/LedgerTransferDto.kt`:
   - `@Serializable data class TransferRequest(@SerialName("from_user_id") val fromUserId: String, @SerialName("to_user_id") val toUserId: String, val amount: Long, @SerialName("idempotency_key") val idempotencyKey: String, val memo: String? = null)`
   - `@Serializable data class TransferResponse(@SerialName("transaction_id") val transactionId: String, @SerialName("new_balance") val newBalance: Long)`
2. `commonTest` にラウンドトリップテストを追加。

## 検証

- [ ] `./gradlew :shared:allTests`

## 依存

- T0-1, T0-2, T1-3

## 技術的な補足

- `idempotencyKey` はクライアント側で ULID / UUIDv4 を採番する。ヘッダと body の両方に乗せるのが README 指定（どちらでも良いが統一するなら両方に乗せる）。
- MFA_REQUIRED は 403 エラーレスポンスとして返るため、この DTO ではなく `ApiError`（T1-3）のパスで扱う。
