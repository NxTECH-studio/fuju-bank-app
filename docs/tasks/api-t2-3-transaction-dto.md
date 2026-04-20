# T2-3: TransactionDto

## 概要

`GET /users/:id/transactions` の mint / transfer 統合レスポンスを DTO 化する。

## 背景・目的

取引履歴画面の基礎データ。mint と transfer が単一のリスト（`transaction_kind` で区別）で返るため、sealed 的なデコードを行う。

## 影響範囲

- モジュール: shared
- ソースセット: commonMain
- 破壊的変更: なし
- 追加依存: なし

## 実装ステップ

1. `shared/src/commonMain/.../data/remote/dto/TransactionDto.kt`:
   - `@Serializable data class TransactionListResponse(val transactions: List<TransactionDto>)`
   - `@Serializable data class TransactionDto(val id: String, @SerialName("transaction_kind") val kind: TransactionKind, val amount: Long, @SerialName("from_user_id") val fromUserId: String?, @SerialName("to_user_id") val toUserId: String?, @SerialName("artifact_id") val artifactId: String?, @SerialName("occurred_at") val occurredAt: String)`
   - `@Serializable enum class TransactionKind { @SerialName("mint") MINT, @SerialName("transfer") TRANSFER }`
2. `commonTest` にラウンドトリップテストを追加（mint / transfer それぞれのサンプル）。

## 検証

- [ ] `./gradlew :shared:allTests`

## 依存

- T0-1, T0-2, T1-3

## 技術的な補足

- mint の場合は `from_user_id` が null、transfer の場合は `artifact_id` が null になる可能性あり。nullable で受ける。
- ページング（`cursor` / `next_cursor`）が必要かは backend 仕様を確認し、必要ならこのタスク内で盛り込む。
