# A4: 取引履歴画面

## メタ情報

- **Phase**: 2
- **並行起動**: ✅ A3 / A5 / A6 と並列可能
- **依存**: A2b（SessionStore.Authenticated）
- **同期点**: なし

## 概要

ユーザーの取引履歴（`mint` / `transfer_in` / `transfer_out`）を時系列で表示。MVP は最新 N 件（pagination は将来）。

## 影響範囲

- iOS 新規:
  - `iosApp/iosApp/Features/Transactions/TransactionListView.swift`
  - `iosApp/iosApp/Features/Transactions/TransactionListViewModel.swift`
  - `iosApp/iosApp/Features/Transactions/TransactionRow.swift`
- Android 新規:
  - `composeApp/.../features/transactions/TransactionListScreen.kt`
  - `composeApp/.../features/transactions/TransactionListViewModel.kt`
  - `composeApp/.../features/transactions/TransactionRow.kt`
- shared:
  - 既存 `LedgerRepository`（or 取引履歴用 API）を利用。なければ `shared/.../data/remote/api/UserTransactionsApi.kt` を新規作成（backend `GET /users/:id/transactions` 想定）。

## 実装ステップ

1. **shared 側 API 確認**:
   - `GET /users/:user_id/transactions` の DTO がすでに揃っているか確認 (`TransactionDto.kt` 既存)
   - 揃っていれば repo メソッド `LedgerRepository.fetchTransactions(userId)` を呼ぶだけ
   - 不足あれば追加

2. **ViewModel**:
   - `state: StateFlow<TransactionListState>`(`Loading | Loaded(items) | Error`)
   - `refresh()` / 起動時 fetch

3. **行 UI** (`TransactionRow`):
   - `kind` でアイコンとラベル分岐:
     - `mint` → 「発行」緑系
     - `transfer_in` → 「受取」青系
     - `transfer_out` → 「送金」赤系
   - 相手 (counterparty.name or public_id) / 額 / 日時（`kotlinx.datetime` でフォーマット）

4. **画面**:
   - iOS: `List` + `.refreshable`
   - Android: `LazyColumn` + Pull-to-refresh
   - 空状態（取引が無い）の文言

5. **HomeView から遷移**:
   - A3 のホーム画面に「取引履歴」セルを置く

## 検証チェックリスト

- [ ] 取引が時系列降順で表示
- [ ] 3 種類の kind が視覚的に区別される
- [ ] 0 件時の空状態が表示
- [ ] Pull-to-refresh で再取得
- [ ] 401 で SessionStore.clear()
