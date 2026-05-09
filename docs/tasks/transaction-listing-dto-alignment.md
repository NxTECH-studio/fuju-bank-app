# 取引一覧 API の DTO 整合（client / server schema 乖離の修正）

## 概要

`/users/:id/transactions` の **実 backend payload** と client 側 `TransactionDto` /
`TransactionListResponse` が完全に乖離している。client 側を server payload に合わせて
整合させ、release ビルドで取引一覧が deserialize 可能な状態にする。
本タスクは `transaction-history-production-ready.md` の **前提タスク**。

## 背景・目的

- ホーム画面のモック削除タスク (`transaction-history-production-ready.md`) を
  着手前にレビューしたところ、「`UserRepository.transactions(userId)` をそのまま再利用」
  という前提が**実は成立していない**ことが判明した。
- 既存の取引履歴画面 (`TransactionListViewModel` / `TransactionsFlowIos` /
  `TransactionDetailFlowIos`) も同じ DTO 経由なので、**release ビルドで黙ってエラーを返している
  可能性が高い**（debug ビルドは `useDummyData = true` のダミー 25 件で取り繕われていて、
  table 上問題が表面化しない）。
- 本タスクは fuju-bank-backend PR #80 とは独立の問題。PR #80 は `/ledger/mint`（書き込み側）
  のみを変更しており、本タスクが対象とする listing API は schema が PR #80 以前から乖離していた。

## Server payload の実態（コントローラ source ベース）

`fuju-bank-backend/app/controllers/user_transactions_controller.rb`（main、2026-04-18 から不変）:

```ruby
render(json: { data: entries.map { |e| serialize(e, account_id) } })

def serialize(entry, account_id)
  tx = entry.ledger_transaction
  {
    entry_id: entry.id,
    transaction_id: tx.id,
    transaction_kind: tx.kind,                       # "mint" | "transfer"
    direction: entry.amount > 0 ? "credit" : "debit",
    amount: entry.amount.abs,                        # 常に正
    artifact_id: tx.artifact_id,                     # nullable (PR #80 以降の代理 mint で NULL)
    counterparty_user_id: counterparty_user_id(tx, account_id),  # transfer のときのみ非 null
    memo: tx.memo,
    metadata: tx.metadata,
    occurred_at: tx.occurred_at.iso8601,
    created_at: tx.created_at.iso8601,
  }
end
```

| 項目 | server 実態 | client 想定 (TransactionDto) | 差分 |
|---|---|---|---|
| Wrapper key | `data` | `transactions` | ❌ |
| Tx ID | `transaction_id` | `id` | ❌ |
| Direction 表現 | `direction: "credit"/"debit"` | `from_user_id` / `to_user_id` の比較 | ❌ |
| 相手 ID | `counterparty_user_id` | `from_user_id` / `to_user_id` 個別 | ❌ |
| Amount 符号 | abs（常に正） | 想定なし（Long そのまま） | △ |
| 追加フィールド | `entry_id` / `memo` / `metadata` / `created_at` | なし | サーバ拡張 |

→ kotlinx.serialization は wrapper key (`data` vs `transactions`) で落ちる。
production で `UserRepository.transactions(userId)` を呼ぶと必ず deserialization 例外。

## 影響範囲

- モジュール: `shared`
- ソースセット:
  - `shared/src/commonMain/kotlin/studio/nxtech/fujubank/data/remote/dto/TransactionDto.kt`
  - `shared/src/commonMain/kotlin/studio/nxtech/fujubank/data/repository/UserRepository.kt`
  - `shared/src/commonTest/kotlin/studio/nxtech/fujubank/data/remote/api/UserApiTest.kt`
  - `shared/src/commonTest/kotlin/studio/nxtech/fujubank/data/repository/UserRepositoryTest.kt`
- 破壊的変更（shared 内部のみ。ホーム / 取引履歴画面の表示モデルは無変更で吸収できる想定）:
  - `TransactionDto` のフィールドを完全入れ替え
  - `TransactionListResponse.transactions` → `data`
  - `UserRepository.transactions(userId)` のシグネチャは保持。direction 推定ロジックを
    server `direction` + `transaction_kind` から決める方式へ
- 公開 ABI（shared framework）への影響: `Transaction` ドメインモデルは変えないため、Swift から
  見た shape は不変。**iOS / Android 両 UI 側はノータッチで済む見込み**。
- 追加依存: なし

## 実装ステップ

### A. DTO の入れ替え

1. **`shared/src/commonMain/.../data/remote/dto/TransactionDto.kt`**
   - `TransactionListResponse.transactions: List<TransactionDto>` を
     `TransactionListResponse.data: List<TransactionDto>` に変更（`@SerialName("data")`）。
   - `TransactionDto` を以下に書き換える:
     ```kotlin
     @Serializable
     data class TransactionDto(
         @SerialName("transaction_id") val id: String,
         @SerialName("transaction_kind") val kind: TransactionKind,
         @SerialName("direction") val direction: TransactionDirectionWire,
         val amount: Long,                        // 常に正（abs）
         @SerialName("artifact_id") val artifactId: String?,
         @SerialName("counterparty_user_id") val counterpartyUserId: String?,
         @SerialName("occurred_at") val occurredAt: String,
         // 拡張フィールド (memo/metadata/created_at/entry_id) は今回 import しない。
         // 必要が出たタイミングで追加する。kotlinx.serialization は ignoreUnknownKeys=true で受ける。
     )

     @Serializable
     enum class TransactionDirectionWire {
         @SerialName("credit") CREDIT,
         @SerialName("debit") DEBIT,
     }
     ```
   - `entry_id` / `memo` / `metadata` / `created_at` は将来必要になったら追加する方針で
     今回は無視。`Json { ignoreUnknownKeys = true }` の設定が `HttpClientFactory` で
     既にされているか確認し、未設定なら本タスクで設定する。

### B. Repository マッピングの直し

2. **`shared/src/commonMain/.../data/repository/UserRepository.kt`**
   - `transactions(userId: String)` の戻り値マッピングを以下のように変える:
     ```kotlin
     return userApi.transactions(userId).map { response ->
         response.data.map { it.toDomain() }
     }
     ```
   - `myUserId` パラメータが不要になる（サーバが direction / counterparty を確定済みのため）。
   - `TransactionDto.toDomain()` のロジック:
     ```kotlin
     private fun TransactionDto.toDomain(): Transaction = Transaction(
         id = id,
         kind = kind,
         direction = resolveDirection(kind, direction),
         amount = amount,
         counterpartyUserId = counterpartyUserId,
         artifactId = artifactId,
         occurredAt = Instant.parse(occurredAt),
     )

     // mint は常に Mint 扱い (現 MVP では burn = mint+debit が発生しない契約)。
     // transfer は credit→Incoming / debit→Outgoing。
     private fun resolveDirection(
         kind: TransactionKind,
         wire: TransactionDirectionWire,
     ): TransactionDirection = when (kind) {
         TransactionKind.MINT -> TransactionDirection.Mint
         TransactionKind.TRANSFER -> when (wire) {
             TransactionDirectionWire.CREDIT -> TransactionDirection.Incoming
             TransactionDirectionWire.DEBIT  -> TransactionDirection.Outgoing
         }
     }
     ```
   - 既存の private helper `counterpartyUserId(myUserId)` / `direction(myUserId)` は削除。
   - `UserRepository.transactions` のシグネチャから `myUserId` 内部依存が消えるので、
     後で `dummyTransactions()` の `direction` セットも自然に維持できる（既にハードコード）。

### C. テスト fixture の整合

3. **`shared/src/commonTest/.../data/remote/api/UserApiTest.kt`**
   - `transactions_returns_success_for_200_payload` の fixture を server 形式に書き直す:
     ```json
     {
       "data": [
         {
           "entry_id": 100,
           "transaction_id": "txn_1",
           "transaction_kind": "mint",
           "direction": "credit",
           "amount": 500,
           "artifact_id": "art_1",
           "counterparty_user_id": null,
           "memo": null,
           "metadata": null,
           "occurred_at": "2026-04-21T00:00:00Z",
           "created_at": "2026-04-21T00:00:01Z"
         }
       ]
     }
     ```
   - `success.value.transactions` を参照しているアサーションは `success.value.data` に置換。

4. **`shared/src/commonTest/.../data/repository/UserRepositoryTest.kt`**
   - 全 4 ケース (`mint` / `outgoing transfer` / `incoming transfer` / `empty list`)
     の fixture を server 形式に書き直す。
   - `transactions("usr_me")` のような呼び出しはそのまま（path param としては有効）。
   - direction の判定が server 主導になるので、テストデータに `direction: "credit"/"debit"`
     を含める。
   - `outgoing` ケース: `transaction_kind=transfer` + `direction=debit` + `counterparty_user_id=usr_other`
   - `incoming` ケース: `transaction_kind=transfer` + `direction=credit` + `counterparty_user_id=usr_other`
   - `mint` ケース: `transaction_kind=mint` + `direction=credit` + `counterparty_user_id=null`

### D. JSON 設定の確認（必要なら追加）

5. **`shared/src/commonMain/.../network/HttpClientFactory.kt`**（または相当箇所）
   - `kotlinx.serialization.json.Json { ignoreUnknownKeys = true }` が設定されているか確認。
     未設定なら追加する（server が将来フィールドを増やしてもクライアントが落ちないようにするため）。
   - 既設定ならノータッチ。

## 検証

- [ ] `./gradlew :shared:allTests` が通る（特に `UserApiTest.transactions_*` /
      `UserRepositoryTest.transactions_*` 計 6 ケース）
- [ ] `./gradlew build` が通る
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が通る
- [ ] **release ビルドで取引履歴一覧画面 (Android / iOS 両方) を開き、実 API から取引が
      表示されること**。direction 表示（受取 = ピンク色 + プラス記号 / 送金 = 黒 + マイナス記号 /
      mint = ピンク色 + プラス記号）が正しいこと。
- [ ] 取引詳細画面で同じ取引が開けること（`TransactionDetailFlowIos` も DTO 変更に追従できているか確認）。
- [ ] debug ビルドではダミー 25 件がそのまま表示されること（regression なし）。

## 技術的な補足

### なぜ「myUserId 比較で direction 推定」をやめるか

- 現状 client は `from_user_id` と `to_user_id` を受け取って `myUserId == fromUserId` で direction を決めていた。
  これは server が PK / ULID どちらを返すかの揺れに弱く、PR #80 で受取人 ID が ULID 化される
  追加リスクを抱えていた。
- 新方式では server がすでに `direction: credit/debit` を確定済みなので、client は ID 比較を
  しない。ID の表現が PK でも ULID でも吸収できる（counterparty_user_id 表示用途のみで使う）。

### 既存の `dummyTransactions()` への影響

- `dummyTransactions()` は `Transaction` ドメインモデル（`direction` 確定済み）を直接返す
  ハードコードなので、本タスクの DTO 変更とは独立。手を加えなくて良い。

### `Transaction.id` の中身

- server は `tx.id`（`ledger_transactions.id` = bigint PK の文字列化）を返す。
  client `Transaction.id` は `String` 型なので互換。短縮 ID 表示 (`takeLast(6)`) もそのまま
  通る。

### artifact_id = NULL ケース（PR #80 由来の代理 mint）

- 既に `TransactionDto.artifactId: String?` で nullable 定義されており、現行 UI も
  `?: "発行"` フォールバック済（`TransactionRow.kt:155`）。本タスクでは追加対応不要。
- subtitle 文言の使い分け（fuju 代理 mint vs Bank 内 mint）は後続の
  `transaction-history-production-ready.md` で扱う。

## Out of Scope（後続タスクで対応）

1. **ホーム画面の最近の取引モック削除** — `transaction-history-production-ready.md`
   が本タスクの merge を前提に着手する。
2. **`memo` / `metadata` / `entry_id` / `created_at` フィールドの活用** — 取引詳細画面の
   情報量を増やす際に DTO に追加する。
3. **視線秒数（gazedSeconds）の API 拡張** — server 側で DTO 拡張が必要。今回スコープ外。

## 未決事項

- なし（実装に進めるだけの情報は揃っている）。
