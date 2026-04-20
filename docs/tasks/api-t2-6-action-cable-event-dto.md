# T2-6: ActionCableEventDto

## 概要

`UserChannel` から push される `credit` イベントのペイロードを DTO 化する。ActionCable 固有のエンベロープ（`type`, `identifier`, `message` など）も合わせて定義する。

## 背景・目的

T3-5 の `UserChannelClient` が WebSocket フレームから `credit` イベントを取り出すための型を先に固める。

## 影響範囲

- モジュール: shared
- ソースセット: commonMain
- 破壊的変更: なし
- 追加依存: なし

## 実装ステップ

1. `shared/src/commonMain/.../data/remote/dto/ActionCableDto.kt`:
   - `@Serializable data class CableEnvelope(val type: String? = null, val identifier: String? = null, val message: JsonElement? = null)`
   - `@Serializable data class CableIdentifier(val channel: String, @SerialName("user_id") val userId: String)` — `identifier` は JSON 文字列化された JSON なのでクライアント側で `Json.encodeToString` して送る。
2. `shared/src/commonMain/.../data/remote/dto/CreditEventDto.kt`:
   - `@Serializable data class CreditEventDto(val type: String, val amount: Long, @SerialName("transaction_id") val transactionId: String, @SerialName("transaction_kind") val transactionKind: String, @SerialName("artifact_id") val artifactId: String?, @SerialName("from_user_id") val fromUserId: String?, val metadata: JsonElement? = null, @SerialName("occurred_at") val occurredAt: String)`
3. `commonTest` に `credit` イベントのサンプル JSON → `CreditEventDto` デコードのテストを追加。

## 検証

- [ ] `./gradlew :shared:allTests`

## 依存

- T0-1, T0-2, T1-3

## 技術的な補足

- ActionCable のメッセージは `{"type":"welcome"}`, `{"type":"confirm_subscription", "identifier":"..."}`, `{"identifier":"...", "message":{...}}` など type が混在するので `CableEnvelope` の `type` と `message` の両方を nullable にする。
- `message` 内の `type: "credit"` でアプリ側イベントを判別。
