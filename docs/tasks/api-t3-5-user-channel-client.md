# T3-5: UserChannelClient（ActionCable WebSocket）

## 概要

Rails ActionCable の `UserChannel` を WebSocket で購読し、`credit` イベントを `Flow<CreditEventDto>` として露出する。

## 背景・目的

リアルタイム着金通知の核。ActionCable は純粋な WebSocket ではなくフレームに独自プロトコル（welcome / ping / subscribe / confirm_subscription）が乗るため、専用クライアントが必要。

## 影響範囲

- モジュール: shared
- ソースセット: commonMain
- 破壊的変更: なし
- 追加依存: なし（Ktor WebSockets は T0-1 で追加済み）

## 実装ステップ

1. `shared/src/commonMain/.../data/remote/api/UserChannelClient.kt`:
   - `class UserChannelClient(private val client: HttpClient, private val cableUrl: String, private val scope: CoroutineScope)`
   - `fun subscribe(userId: String): Flow<CreditEventDto>` — 内部で `client.webSocket(cableUrl)` を起動、`{"command":"subscribe","identifier":"..."}` 送信、`ping` は無視、`message.type == "credit"` のみ `CreditEventDto` にデコードして emit。
   - 再接続ロジック: `Flow` の `retry { it is IOException }.buffer()` で指数バックオフ（1s, 2s, 4s, 上限 30s）。
2. `commonTest` に最低限のデコードテストを追加（WebSocket は MockEngine で完全には検証できないため、フレーム parse 部分のみ単体テスト）。

## 検証

- [ ] `./gradlew :shared:allTests`
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`

## 依存

- T1-1, T1-3, T2-6

## 技術的な補足

- `cableUrl` は `wss://.../cable` 形式。認証は最初のフレームで `command: "subscribe"` に JWT を含めるか、HTTP 接続時の `Authorization` ヘッダに乗せる（backend 仕様を確認）。
- `CoroutineScope` はアプリスコープで注入（Koin）。Android は ProcessLifecycle、iOS は `MainScope` を Koin モジュール側で切り替える。
- 型 `JsonElement` のまま保持していた `message` を `Json.decodeFromJsonElement<CreditEventDto>(message)` で最終的にデコード。
