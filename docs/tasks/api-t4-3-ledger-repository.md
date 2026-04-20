# T4-3: LedgerRepository + ledgerModule

## 概要

`LedgerApi`（T3-4）をラップし、送金フロー（idempotency 管理、MFA 分岐）を扱う Repository を追加する。

## 背景・目的

送金は retry 時の同一 idempotencyKey 再利用、MFA_REQUIRED 時の MFA 画面誘導など UI 層にとって最も複雑なフロー。Repository に閉じ込める。

## 影響範囲

- モジュール: shared
- ソースセット: commonMain
- 破壊的変更: なし
- 追加依存: なし

## 実装ステップ

1. `shared/src/commonMain/.../data/repository/LedgerRepository.kt`:
   - `class LedgerRepository(private val ledgerApi: LedgerApi)`
   - `sealed class TransferResult { data class Success(val newBalance: Long, val transactionId: String) : TransferResult(); data class MfaRequired(val retryKey: String) : TransferResult(); data class Failure(val error: ApiError) : TransferResult() }`
   - `suspend fun transfer(from: String, to: String, amount: Long, memo: String?, retryKey: String? = null): TransferResult` — `retryKey == null` の場合は新規採番、再試行の場合は同一キー。
2. `shared/src/commonMain/.../di/LedgerModule.kt`:
   - `val ledgerModule = module { single { LedgerApi(get()) }; single { LedgerRepository(get()) } }`
3. `commonTest` に「新規送金 / 残高不足 / MFA_REQUIRED 後の retryKey 引き継ぎ」を検証。

## 検証

- [ ] `./gradlew :shared:allTests`

## 依存

- T3-4

## 技術的な補足

- `MfaRequired` に含める `retryKey` は「MFA 認証後に UI がこのキーで `transfer` を再呼び出しする」フローを想定。
- `Idempotency-Key` 再利用時の挙動（backend が同一レスポンスを返す or 新規処理として扱う）は backend 仕様を確認し、本 Repository のリトライ戦略を確定させる。
