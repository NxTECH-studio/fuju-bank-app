# server-bank-24: `/ledger/transfer` を external_user_id (ULID) 受け入れに揃える

> 本タスクは Rails 製の **fuju-bank-backend** リポジトリ側で実施する作業の計画書。
> 計画書ファイル自体はクライアント計画書 `docs/tasks/transfer-external-user-id-alignment.md` と
> 一元参照したいため、本リポジトリ（`fuju-bank-app`）の `docs/tasks/` に置く。
> 実装着手時にバックエンドリポジトリへ Notion タスクと併せて移送する想定。

## 概要

PR #101 (`users-search-authcore-delegation`) で `GET /users/search` のレスポンス `id` が
**bank PK (bigint) → AuthCore ULID (string, 26 文字 = `external_user_id`)** に切り替わったが、
`POST /ledger/transfer` は **依然 `User.find(transfer_params[:to_user_id])`** で bank PK を期待する
ままになっており、search → transfer の流れが現状 develop で繋がっていない。

本タスクでは `LedgerController#transfer` の `to_user_id` / `from_user_id` を
**`external_user_id` (ULID) 受け取り** に切り替え、`/ledger/mint` の `resolve_recipient!` パターンに
揃える。cross-service identity を `external_user_id` (ULID) に一本化する長期方針
（`users-search-cross-service-identity.md` の B + 最小 C）の **transfer 側カバレッジ** に相当する。

## 背景・目的

### 現状の不一致

| エンドポイント | `*_user_id` の意味 | 解決ロジック | ファイル |
|---|---|---|---|
| `POST /ledger/mint` | external_user_id (ULID) | `resolve_recipient!` + `UserProvisioner.call(external_user_id:)` | `app/controllers/ledger_controller.rb` |
| `POST /ledger/transfer` | **bank PK (bigint)** | `User.find(...)` | `app/controllers/ledger_controller.rb` |
| `GET /users/search` レスポンス `id` | **AuthCore ULID (= external_user_id)** | AuthCore 委譲 | `app/services/authcore/user_search_client.rb` |

クライアントは search で得た ULID をそのまま `to_user_id` に詰めて送るため、
現行 `LedgerController#transfer` では `User.find(ULID_string)` → 数値変換失敗 / `ActiveRecord::RecordNotFound`
になる。MVP の送金フロー（`client-bank-22`）が end-to-end で動かない直接原因。

### 採用方針

`/ledger/mint` が既に `external_user_id` 入力を `UserProvisioner.call(external_user_id:)` で
ULID → `User` に lazy 解決する経路を持っているため、これを **transfer にも適用** する。
クライアント側は search レスポンスの ULID をそのまま `to_user_id` に渡せばよく、
中間で bank PK を意識する必要がなくなる。

cross-service identity の長期方針（`bank.users.public_id` はキャッシュ扱い、識別子は AuthCore
ULID に統一）とも整合する。`bank.users.id` (bigint) は **内部リレーション専用** に閉じていく。

## スコープ

### 含む

- `LedgerController#transfer` の `User.find(to_user_id)` / `User.find(from_user_id)` を
  ULID resolve 経路に切替（`resolve_recipient!` の流用または相当物の導入）
- ULID 形式バリデーション（`User::ULID_REGEX` 既存定数を利用、不正時 422 `VALIDATION_FAILED`）
- `from_user_id` を body から落として **JWT current_user に倒すかどうか** の決定（Open Question 1）
- `transfer_params` の strong parameters 定義の調整
- `serialize_transaction` レスポンスの `from_user_id` / `to_user_id` 表現を必要に応じて
  external_user_id (ULID) 形式に揃える（現状そもそも返していないなら触らない）
- request spec `spec/requests/ledger_transfer_spec.rb` の全面書き換え:
  - `from_user_id: from_user.id`（bank PK）→ `from_user_id: from_user.external_user_id`
  - 自分宛送金禁止 / 残高不足 / idempotency / 422 系の既存ケースを ULID 前提に
  - bank に未存在の ULID を `to_user_id` に渡した場合の挙動を新規ケース追加
    （lazy provision で新規 User を作るか、422 で弾くかは Open Question 2）
- README `主要 API` 表の `/ledger/transfer` 行に「`from_user_id` / `to_user_id` は external_user_id (ULID)」と追記
- `UserResponse` (`/users/me` 等) に `sub`（ULID）を **非 null で返す** よう調整（Open Question 3、
  bundle すると client 側 SessionStore 切替が同時にアンブロックされる）

### 含まない

- `/ledger/mint` 側の挙動変更（既に ULID 受け入れ済み）
- `GET /users/search` の追加変更（PR #101 完了済み）
- `bank.users.public_id` カラムの削除（cross-service identity の長期方針別タスクで対応）
- 送金時 MFA step-up (`server-bank-23`) の配線
- AuthCore 側の変更（不要）

## API 仕様（変更点）

### `POST /ledger/transfer`

**変更前 (develop)**:

```json
{
  "ledger": {
    "from_user_id": 7,        // bank PK (bigint)
    "to_user_id": 12,         // bank PK (bigint)
    "amount": 100,
    "memo": "thanks",
    "metadata": {}
  }
}
```

**変更後 (本タスク)**:

```json
{
  "ledger": {
    "from_user_id": "01HX4T8K7N9P2QABC0DEF1Y0K1",  // external_user_id (ULID 26 文字)
    "to_user_id":   "01HX4T8K7N9P2QABC0DEF1Y0K2",  // external_user_id (ULID 26 文字)
    "amount": 100,
    "memo": "thanks",
    "metadata": {}
  }
}
```

> Open Question 1 で `from_user_id` を body から削除する案を採用した場合は `from_user_id` フィールドは消える。

### バリデーション / エラー

| ケース | HTTP | code | 備考 |
|---|---|---|---|
| `to_user_id` 欠落 | 422 | `VALIDATION_FAILED` | "to_user_id is required" |
| `to_user_id` が ULID 形式違反 | 422 | `VALIDATION_FAILED` | "to_user_id must be a ULID (external_user_id)" |
| `from_user_id` と `to_user_id` が同一 ULID | 422 | `VALIDATION_FAILED` | "cannot transfer to self"（既存 `Ledger::Transfer` 内チェックを ULID 同値で動かす） |
| `to_user_id` の ULID が bank に未存在 | 200 | （正常） | `UserProvisioner.call(external_user_id:)` で lazy 作成。Open Question 2 で確定 |
| `amount` <= 0 | 422 | `VALIDATION_FAILED` | 既存維持 |
| 残高不足 | 422 | `INSUFFICIENT_BALANCE` | 既存維持 |
| 認証 / introspection 系 | 401 | `UNAUTHENTICATED` / `TOKEN_INACTIVE` | 既存維持 |
| Idempotency-Key 欠落 | 422 | `VALIDATION_FAILED` | 既存維持 |

### サーバ実装の流れ（疑似コード）

```ruby
# app/controllers/ledger_controller.rb

def transfer
  to_user   = resolve_party!(transfer_params[:to_user_id],   field: :to_user_id)
  from_user = resolve_party!(transfer_params[:from_user_id], field: :from_user_id)
  # ↑ Open Question 1 で from_user_id を body から落とす場合は
  #   `from_user = current_user` に置き換える

  tx = Ledger::Transfer.call(
    from_user: from_user,
    to_user: to_user,
    amount: transfer_params[:amount].to_i,
    idempotency_key: idempotency_key!,
    memo: transfer_params[:memo],
    metadata: transfer_params[:metadata].to_h,
    occurred_at: parse_occurred_at(transfer_params[:occurred_at]),
  )

  render(json: serialize_transaction(tx), status: :ok)
end

private

# mint 側 `resolve_recipient!` と共通化する想定。命名は generic に倒す。
def resolve_party!(external_user_id, field:)
  raise ValidationFailedError.new(message: "#{field} is required") if external_user_id.blank?
  raise ValidationFailedError.new(message: "#{field} must be a ULID (external_user_id)") \
    unless User::ULID_REGEX.match?(external_user_id)

  UserProvisioner.call(external_user_id: external_user_id)
end
```

## 影響範囲

- `app/controllers/ledger_controller.rb` — `transfer` アクション書き換え、`resolve_party!` 抽出
- `app/services/ledger/transfer.rb` — 「自己送金禁止」チェックの `@from_user.id == @to_user.id` は
  bank PK 比較のままで OK（ULID から resolve した後の `User` 比較なので）。要動作確認
- `app/controllers/users_controller.rb#serialize_user` — `UserResponse` に `sub` (ULID) を含めるか確認
  （Open Question 3）
- `spec/requests/ledger_transfer_spec.rb` — 全面書き換え（既存テストは bank PK 前提なので破棄）
- `spec/requests/users_spec.rb` — `UserResponse.sub` を返す方針なら expectation 追加
- `README.md` — 主要 API 表の `/ledger/transfer` 行 + `serialize_user` 説明の追記
- `docs/tasks/INDEX.md` — 本タスク追記

破壊的変更:

- `POST /ledger/transfer` の payload セマンティクスが変わる（bank PK → ULID）
- 既存クライアントは即時 404 / 422 になる。client 側追従タスク
  (`transfer-external-user-id-alignment.md`) と **同じ PR / 同じデプロイで揃える** 運用が必要
- backend と client の merge 順:
  - backend を develop に merge → 即 staging に展開 → client は staging 向けで動作確認
  - client を main に merge → release ビルドで本番が ULID 前提に揃う

## 実装ステップ

1. **コントローラ書き換え** (`ledger_controller.rb`)
   - `resolve_party!` 抽出（mint の `resolve_recipient!` と共通化、命名を `resolve_party_by_external_user_id!` 的に揃える）
   - `transfer` アクションを ULID resolve 経路に切替
   - `transfer_params` の strong parameter シグネチャは維持（フィールド名は同じ、型だけ string 化）
2. **「自己送金禁止」チェックの確認** (`ledger/transfer.rb:30`)
   - `@from_user.id == @to_user.id` は bank PK 比較で残し、ULID から resolve した
     同一 User インスタンス同士でも弾けることを spec で担保
3. **`UserResponse.sub` の同梱判断**（Open Question 3）
   - 採用するなら `serialize_user` に `sub: user.external_user_id` を追加
   - クライアント `SessionStore` 切替がアンブロックされ、追従コストが下がる
4. **request spec 書き換え** (`spec/requests/ledger_transfer_spec.rb`)
   - 既存の `from_user_id: from_user.id` をすべて `from_user.external_user_id` に
   - 新規ケース:
     - `to_user_id` が ULID 形式違反 → 422
     - bank 未存在の ULID で transfer → lazy provision で新規 User + Account が作られる
       （Open Question 2 の決定に応じて）
     - 自己送金禁止が ULID 経路でも動く
5. **README / INDEX 更新**
6. **手動 staging 確認**
   - `curl` でユーザー A から ユーザー B へ ULID ベースで送金
   - 既存テストアカウントの bank PK ベース送金は **404 / 422 で落ちる** ことを確認（破壊的変更の表明）
7. **クライアント追従タスクのアンブロック**
   - `transfer-external-user-id-alignment.md` の作業を開始してもらう
   - merge タイミングは client → backend が staging で繋がる順序を意識して調整

## テスト方針（RSpec）

```ruby
describe "POST /ledger/transfer" do
  let!(:from_user) { create(:user, external_user_id: ulid_for("alice")) }
  let!(:to_user)   { create(:user, external_user_id: ulid_for("bob")) }

  context "正常系 (ULID)" do
    it "external_user_id (ULID) で from / to を解決して送金する"
    it "bank に未存在の to_user_id ULID は UserProvisioner で lazy 作成される"
    it "Idempotency-Key 同一なら 2 回 POST しても 1 件のみ作成・2 回目も 200"
    it "memo / metadata / occurred_at は既存挙動を維持する"
  end

  context "バリデーション" do
    it "to_user_id が ULID 形式違反なら 422"
    it "from_user_id が ULID 形式違反なら 422"
    it "from_user_id と to_user_id が同一 ULID なら 422 (cannot transfer to self)"
    it "amount <= 0 は 422"
  end

  context "破壊的変更の確認" do
    it "bank PK を to_user_id に渡すと 422 (ULID format violation)"
  end

  context "認証" do
    it "access_token なし → 401 UNAUTHENTICATED"
    it "introspection inactive → 401 TOKEN_INACTIVE"
  end
end
```

## 完了条件

1. `POST /ledger/transfer` が ULID 形式の `from_user_id` / `to_user_id` を受け取り、bank PK 経路を完全に廃止
2. `Ledger::Transfer` 自己送金禁止が ULID 経路でも有効
3. RSpec 全 green、新規 ULID ケース追加済み
4. README の API 表に追記
5. `docs/tasks/INDEX.md` に本タスク追記
6. staging で curl 動作確認（ULID ベース送金 OK、bank PK ベース送金 NG）
7. （Open Question 3 採用時）`UserResponse` が `sub` (ULID) を非 null で返す
8. クライアント追従タスク `transfer-external-user-id-alignment.md` をアンブロックして merge 順序を握る

## Open Questions

1. **`from_user_id` を body から落として JWT current_user に倒すか**
   - 候補 A: body から削除し、サーバが必ず `current_user` を使う（クライアント実装ミスでなりすまし送金が
     不可能になる、API が綺麗）
   - 候補 B: 現状維持で `from_user_id` を body で受ける（既存仕様を最小変更）
   - **推奨**: 候補 A。`from_user_id` を意図的に変更できる API は攻撃面が広い割に正当用途がない
     （代理送金は将来的に別 endpoint で扱うべき）。
2. **bank 未存在の `to_user_id` ULID の扱い**
   - 候補 A: `UserProvisioner.call(external_user_id:)` で lazy 作成して送金成立（mint と同じ）
   - 候補 B: 422 で「相手が bank にまだ存在しません」を返す
   - **推奨**: 候補 A。mint と挙動を揃え、cross-service identity 方針（AuthCore = directory）と整合
3. **`UserResponse.sub` を本タスクで非 null 返却にするか**
   - 採用すると client `SessionStore` の切替（bank PK → ULID）が同時にアンブロックされ追従コストが下がる
   - **推奨**: 採用。`serialize_user` 1 行追加のみ。`UserResponse.subject` は既に nullable 受け入れの
     クライアント DTO になっているので壊さない
4. **`Ledger::Transfer.amount` の上限導入**
   - 本タスクのスコープ外だが、ULID 化と同時に高額送金時の MFA step-up（server-bank-23）の
     `MfaRequired` include を入れるかどうかは別タスクで判断
5. **既存テストアカウントの後方互換**
   - staging 環境の既存テストアカウントは ULID ベースに切り替わるので、QA 用 curl スクリプトを更新する
     ことを QA 担当者に伝える必要あり

## 関連タスク

- 追従先（クライアント）: `docs/tasks/transfer-external-user-id-alignment.md`
- 関連（前提）: `fuju-bank-backend/docs/tasks/users-search-cross-service-identity.md`（cross-service identity 長期方針）
- 関連（同時期）: `fuju-bank-backend/docs/tasks/server-bank-23-transfer-mfa-verify-flow.md`（MFA step-up は別動線）
- 親（クライアント本体）: `docs/tasks/client-bank-22-money-transfer.md`（PR #92 で 2026-05-10 main にマージ済み）
