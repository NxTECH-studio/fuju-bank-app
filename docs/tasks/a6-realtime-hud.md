# A6: リアルタイム HUD（UserChannel 接続）

## メタ情報

- **Phase**: 2
- **並行起動**: ⚠️ A3 / A4 / A5 と並列可能だが、release 有効化は backend B1 完了待ち
- **依存**: A2b / backend B1（subprotocol JWT 認証）
- **同期点**: backend B1 で確定する subprotocol 仕様 (`Sec-WebSocket-Protocol: bearer, <jwt>` or fallback `Authorization` ヘッダ)

## 概要

ActionCable `UserChannel` を購読し、入金 / 送金成功 / mint 等のイベントを HUD としてリアルタイム反映する。bank backend B1 で JWT 接続認証が入った subprotocol に追従する。

## 影響範囲

- shared:
  - `shared/.../data/remote/api/UserChannelClient.kt`（既存・改修）
  - `shared/.../data/repository/RealtimeRepository.kt`（既存・改修）
- iOS:
  - `iosApp/iosApp/Features/Home/HomeView.swift`（HUD オーバーレイ）
  - `iosApp/iosApp/Features/Home/HomeViewModel.swift`（events 購読）
- Android:
  - `composeApp/.../features/home/HomeScreen.kt`
  - `composeApp/.../features/home/HomeViewModel.kt`

## 実装ステップ

### Step 1: UserChannelClient を JWT subprotocol 対応にする

backend B1 PR description に書かれた方式に合わせる:

**Case A: subprotocol 方式（推奨）**
```kotlin
client.webSocket(
    urlString = "$cableUrl",
    request = {
        headers.append("Sec-WebSocket-Protocol", "actioncable-v1-json, bearer, $jwt")
    },
) { ... }
```
注意: Action Cable はデフォルトで `actioncable-v1-json` subprotocol を使うので並べる順序は backend 実装と合わせる。

**Case B: Authorization ヘッダ方式（fallback）**
```kotlin
client.webSocket(
    urlString = "$cableUrl",
    request = {
        headers.append(HttpHeaders.Authorization, "Bearer $jwt")
    },
) { ... }
```

JWT は `tokenStorage.loadAccess()` から取得。期限切れなら refresh 走らせてから接続。

### Step 2: RealtimeRepository.events Flow

```kotlin
class RealtimeRepository {
    val events: SharedFlow<CreditEvent>  // 既存 model
    suspend fun connect()
    suspend fun disconnect()
}
```

HomeViewModel から起動時に `connect()`、destroy で `disconnect()`。

### Step 3: HomeView での反映

- 受信イベントの種別に応じて:
  - `CreditEvent.Mint` → 「+N fuju 発行」のトースト + 残高再取得
  - `CreditEvent.TransferIn` → 「+N fuju 受け取り」のトースト + 残高再取得
  - `CreditEvent.TransferOut` → 残高再取得のみ（A5 で既に楽観反映済の可能性）
- iOS: SwiftUI `Task { for await event in viewModel.events { ... } }`
- Android: `LaunchedEffect(Unit) { viewModel.events.collect { ... } }`

### Step 4: 再接続戦略

- ネットワーク切断 / バックグラウンド復帰時に自動再接続
- exponential backoff（1s, 2s, 4s, 最大 30s）

### Step 5: ログアウト時のクリーンアップ

`SessionStore.clear()` で `RealtimeRepository.disconnect()` を呼ぶ

## 検証チェックリスト

- [ ] 別端末で送金 → 5 秒以内に HUD 更新
- [ ] 不正 JWT で接続が reject される（backend B1 の検証）
- [ ] バックグラウンド復帰で再接続
- [ ] ログアウトで切断
- [ ] release ビルドでも本番 wss:// に繋がる

## subprotocol 確定までのフォールバック

backend B1 がまだ仕様確定していない段階では本タスクを保留 or  Authorization ヘッダ方式で先行実装し、確定後に切替。
