# T4-4: RealtimeRepository + realtimeModule

## 概要

`UserChannelClient`（T3-5）をラップし、アプリ全体で 1 本の `Flow<CreditEvent>` として着金通知を供給する Repository を追加する。

## 背景・目的

複数画面（HUD、通知、ダッシュボード）が同じ `credit` イベント Flow を購読したい。二重に WebSocket を張らないよう Repository で `shareIn` する。

## 影響範囲

- モジュール: shared
- ソースセット: commonMain
- 破壊的変更: なし
- 追加依存: なし

## 実装ステップ

1. `shared/src/commonMain/.../domain/model/CreditEvent.kt`:
   - `data class CreditEvent(val transactionId: String, val amount: Long, val kind: TransactionKind, val counterpartyUserId: String?, val artifactId: String?, val occurredAt: Instant)`
2. `shared/src/commonMain/.../data/repository/RealtimeRepository.kt`:
   - `class RealtimeRepository(private val client: UserChannelClient, private val appScope: CoroutineScope)`
   - `fun creditEvents(userId: String): SharedFlow<CreditEvent>` — `client.subscribe(userId).map { it.toDomain() }.shareIn(appScope, SharingStarted.WhileSubscribed(5_000), replay = 0)`
3. `shared/src/commonMain/.../di/RealtimeModule.kt`:
   - `val realtimeModule = module { single<CoroutineScope>(named("appScope")) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }; single { UserChannelClient(get(), CABLE_URL, get(named("appScope"))) }; single { RealtimeRepository(get(), get(named("appScope"))) } }`
4. `commonTest` で DTO → ドメイン変換のテスト。

## 検証

- [ ] `./gradlew :shared:allTests`

## 依存

- T3-5

## 技術的な補足

- `appScope` の寿命はプロセス全体。Koin の `single` として保持し、`onClose` で cancel する処理は T5-2 / T5-3 でプラットフォーム側のライフサイクルに紐付ける。
- 1 ユーザーが複数画面で同じ Flow を購読する前提なので `shareIn` 必須。
