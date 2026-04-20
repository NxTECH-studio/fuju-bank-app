# T3-4: LedgerApi（送金）

## 概要

`POST /ledger/transfer` を呼ぶ API クライアントを実装する。`Idempotency-Key` の自動採番と MFA_REQUIRED の伝播を含む。

## 背景・目的

送金は副作用を持つ唯一のエンドポイント。リトライ時の重複防止（idempotency）と MFA 分岐があり、専用の責務として切り出す。

## 影響範囲

- モジュール: shared
- ソースセット: commonMain
- 破壊的変更: なし
- 追加依存: なし

## 実装ステップ

1. `shared/src/commonMain/.../data/remote/api/LedgerApi.kt`:
   - `class LedgerApi(private val client: HttpClient, private val idempotencyKeyFactory: () -> String = { uuid4().toString() })`
   - `suspend fun transfer(fromUserId: String, toUserId: String, amount: Long, memo: String? = null, idempotencyKey: String = idempotencyKeyFactory()): NetworkResult<TransferResponse>`
   - 実装: `Idempotency-Key` をヘッダと body の両方に載せる。
2. `commonTest` に MockEngine テストを追加（成功 / INSUFFICIENT_BALANCE / MFA_REQUIRED / 同一 idempotencyKey 再試行）。

## 検証

- [ ] `./gradlew :shared:allTests`

## 依存

- T1-1, T1-3, T2-5

## 技術的な補足

- `uuid4()` は `kotlinx-datetime` には無い。`kotlin.uuid.Uuid`（Kotlin 2.0.20+ 実験 API）が利用可能なので `@OptIn(ExperimentalUuidApi::class)` で採用する。
- `idempotencyKeyFactory` を引数化しておくことで、Repository（T4-3）側がリトライ時に同一キーを再利用できる。
