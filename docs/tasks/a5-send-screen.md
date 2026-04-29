# A5: 送金画面

## メタ情報

- **Phase**: 2
- **並行起動**: ✅ A3 / A4 / A6 と並列可能
- **依存**: A2b（SessionStore.Authenticated）/ A3（残高表示 UI を流用するなら）
- **同期点**: なし

## 概要

`POST /ledger/transfer` を叩く UI。MVP は受取人を `external_user_id` 手入力 + 額入力。QR 送金 (`qr-payment-foundation-mpm`) は別タスクで対応するため本スコープ外。

## 影響範囲

- iOS 新規:
  - `iosApp/iosApp/Features/Send/SendView.swift`
  - `iosApp/iosApp/Features/Send/SendViewModel.swift`
- Android 新規:
  - `composeApp/.../features/send/SendScreen.kt`
  - `composeApp/.../features/send/SendViewModel.kt`
- shared:
  - 既存 `LedgerRepository.transfer(...)` を利用。

## 実装ステップ

1. **ViewModel**:
   - 入力 state: `recipientExternalId: String` / `amountFuju: String`（数値変換は submit 時）
   - `submit()` → `ledgerRepository.transfer(recipient, amount, idempotencyKey = UUID)`
   - 成功時: SessionStore に refresh トリガを送る（or 戻り値を Snackbar 表示）

2. **Idempotency-Key**:
   - 二重 submit 防止のため UUID v4 を生成して送信
   - submit 中はボタン無効化

3. **UI**:
   - 受取人入力欄（バリデーション: ULID 26 文字 or 公開 ID 4-16 文字。最終的に backend が決定するのでフロントは緩く）
   - 金額入力欄（整数 fuju 単位、現在残高を上限としてヒント表示）
   - 確認ダイアログ（受取人 / 額を表示して最終確認）
   - 結果 Snackbar / Toast

4. **エラー表示**:
   - `INSUFFICIENT_BALANCE`（仮）/ `RECIPIENT_NOT_FOUND` / `RATE_LIMIT_EXCEEDED`
   - bank backend の現状エラーコードを `ApiErrorCode` で確認、未網羅なら追加

5. **完了後の挙動**:
   - 成功時: A3 ホーム画面に戻る + 残高 / 履歴 を再取得
   - A6 経由で WebSocket イベントが先に来る場合の重複処理（同じトランザクション ID で UI が反応しても整合する）

## 検証チェックリスト

- [ ] 送金成功で自分の残高が即時減算
- [ ] 二重 submit でも 1 件しか送られない（Idempotency-Key 効く）
- [ ] 残高超過の額を送ったらエラー表示
- [ ] エラー後に残高は変化しない
