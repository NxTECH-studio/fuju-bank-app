# バックエンド API 連携のタスク分割

## 概要

バックエンド（fuju-bank-backend）はすでに完成している。デザイン確定を待たずにクライアント側の Kotlin 実装（shared モジュールの API 連携層）を先に終わらせるため、PR 同士が衝突しない粒度でタスクを分割する計画。

## 背景・目的

- **現状**: shared モジュールはサンプルの `Greeting` / `Platform` のみ。Ktor / kotlinx.serialization / Koin 等の KMP ネットワーク基盤が未導入。
- **狙い**: バックエンド完成済みの利を活かし、デザイン待ちになる前に shared 側の DTO / API クライアント / Repository を固め切る。デザイン確定後は Compose UI と SwiftUI が Koin から依存を取り出すだけで済む状態にする。
- **制約**: PR を小さく保ち、`libs.versions.toml` / `shared/build.gradle.kts` / `di/SharedModule.kt` といった "みんなが触りたがるファイル" の直列編集を最小限にする。

## 影響範囲

- モジュール: `shared`（メイン）/ `composeApp`（Koin 起動のみ）/ `iosApp`（Koin 起動のみ）
- ソースセット: `commonMain`（大半）/ `androidMain`（OkHttp エンジン・EncryptedSharedPreferences）/ `iosMain`（Darwin エンジン・Keychain）
- 破壊的変更: なし（新規追加が中心）
- 追加依存（T0-1 でまとめて追加）:
  - `io.ktor:ktor-client-core` / `ktor-client-content-negotiation` / `ktor-client-logging` / `ktor-client-auth` / `ktor-serialization-kotlinx-json` / `ktor-client-okhttp`（android）/ `ktor-client-darwin`（ios）/ `ktor-client-websockets`
  - `org.jetbrains.kotlinx:kotlinx-serialization-json`
  - `org.jetbrains.kotlinx:kotlinx-coroutines-core`
  - `org.jetbrains.kotlinx:kotlinx-datetime`
  - `io.insert-koin:koin-core` / `koin-android` / `koin-compose`
  - `androidx.security:security-crypto`（android, TokenStorage 用）
  - Kotlin Serialization Gradle plugin

## 技術方針（推奨構成）

| 層 | 技術 | 配置 |
|---|---|---|
| HTTP クライアント | Ktor Client + ContentNegotiation(JSON) + Logging + Auth | `shared/commonMain` + `expect/actual` エンジン |
| シリアライズ | kotlinx.serialization | `commonMain` |
| 非同期 | kotlinx.coroutines（`Flow` / `StateFlow`） | `commonMain` |
| DI | Koin（モジュール分割、`SharedModule` で aggregate） | `commonMain` + 各層で module 定義 |
| トークン保管 | expect `TokenStorage` / Android: EncryptedSharedPreferences / iOS: Keychain | `expect/actual` |
| WebSocket | Ktor WebSockets（ActionCable 互換レイヤーは自前） | `commonMain` |

## タスク一覧（PR 単位 / 各サブタスクに個別の実装計画書あり）

凡例: 依存は "これが先に merge されている必要あり" の意味。同一 Phase 内は原則並行可能。各タスクは個別の `docs/tasks/api-t*-*.md` を `/start-with-plan` で流して実装する。

### Phase 0: 基盤（直列）

- [ ] [T0-1: KMP ネットワーク基盤の依存追加](./api-t0-1-add-networking-deps.md) — libs.versions.toml + shared/build.gradle.kts。依存: なし
- [ ] [T0-2: パッケージ骨格の作成](./api-t0-2-package-skeleton.md) — 並行タスク用のディレクトリ下地。依存: T0-1

### Phase 1: HTTP / 認証保管の基盤（並行可能）

- [ ] [T1-1: HttpClientFactory（expect/actual）](./api-t1-1-http-client-factory.md) — Ktor HttpClient + Android(OkHttp)/iOS(Darwin)。依存: T0-2
- [ ] [T1-2: TokenStorage（expect/actual）](./api-t1-2-token-storage.md) — Android: EncryptedSharedPreferences / iOS: Keychain。依存: T0-2
- [ ] [T1-3: 共通 API エラー型](./api-t1-3-api-error-types.md) — ApiError / ApiErrorCode / NetworkResult。依存: T0-2

### Phase 2: DTO 定義（全て並行可能 / 1 ファイル = 1 PR）

- [ ] [T2-1: AuthDto](./api-t2-1-auth-dto.md) — AuthCore login / refresh
- [ ] [T2-2: UserDto](./api-t2-2-user-dto.md) — POST /users / GET /users/:id
- [ ] [T2-3: TransactionDto](./api-t2-3-transaction-dto.md) — mint / transfer 統合
- [ ] [T2-4: ArtifactDto](./api-t2-4-artifact-dto.md)
- [ ] [T2-5: LedgerTransferDto](./api-t2-5-ledger-transfer-dto.md) — Idempotency-Key 含む
- [ ] [T2-6: ActionCableEventDto](./api-t2-6-action-cable-event-dto.md) — credit イベントペイロード

各タスク共通依存: T1-3

### Phase 3: API クライアント（並行可能 / 1 エンドポイント群 = 1 PR）

- [ ] [T3-1: AuthApi](./api-t3-1-auth-api.md) — 依存: T1-1, T1-2, T1-3, T2-1
- [ ] [T3-2: UserApi](./api-t3-2-user-api.md) — 依存: T1-1, T1-3, T2-2, T2-3
- [ ] [T3-3: ArtifactApi](./api-t3-3-artifact-api.md) — 依存: T1-1, T1-3, T2-4
- [ ] [T3-4: LedgerApi](./api-t3-4-ledger-api.md) — 依存: T1-1, T1-3, T2-5
- [ ] [T3-5: UserChannelClient](./api-t3-5-user-channel-client.md) — ActionCable。依存: T1-1, T1-3, T2-6

### Phase 4: Repository + Koin モジュール（並行可能 / 1 レイヤー = 1 PR）

- [ ] [T4-1: AuthRepository + authModule](./api-t4-1-auth-repository.md) — 依存: T3-1, T1-2
- [ ] [T4-2: UserRepository + userModule](./api-t4-2-user-repository.md) — 依存: T3-2
- [ ] [T4-3: LedgerRepository + ledgerModule](./api-t4-3-ledger-repository.md) — 依存: T3-4
- [ ] [T4-4: RealtimeRepository + realtimeModule](./api-t4-4-realtime-repository.md) — 依存: T3-5
- [ ] [T4-5: ArtifactRepository + artifactModule](./api-t4-5-artifact-repository.md) — 依存: T3-3

`di/SharedModule.kt` は **この Phase では触らない**（T5-1 でまとめて aggregate）

### Phase 5: 集約 + プラットフォーム起動（直列）

- [ ] [T5-1: SharedModule aggregate + initKoin](./api-t5-1-shared-module-aggregate.md) — 依存: Phase 4 全タスク
- [ ] [T5-2: Android エントリで Koin 起動](./api-t5-2-android-koin-bootstrap.md) — 依存: T5-1
- [ ] [T5-3: iOS エントリで Koin 起動](./api-t5-3-ios-koin-bootstrap.md) — 依存: T5-1

## PR 衝突回避ルール

1. **`gradle/libs.versions.toml` は T0-1 のみ編集**。以降、追加依存が必要になったら単独 PR として切る。
2. **`shared/build.gradle.kts` は T0-1 で完成形まで持っていく**。Phase 1 以降は触らない。
3. **`di/SharedModule.kt` は T5-1 以降のみ**。Phase 4 の各タスクは自分の module ファイルを新規追加するだけ。
4. 各タスクは **新規ファイル追加を基本**とする。既存ファイルの編集は数行以下に抑える。
5. expect 宣言ファイルと actual 実装ファイルは **同一 PR で追加**（別 PR に分けない）。
6. ファイルパス例を事前に割り付け：
   - `network/` 以下 → T1-1 が占有
   - `auth/` 以下 → T1-2 が占有
   - `data/remote/dto/` → DTO ごとにファイル名でパーティション（Phase 2）
   - `data/remote/api/` → API ごとにファイル名でパーティション（Phase 3）
   - `data/repository/` → Repository ごとにファイル名でパーティション（Phase 4）
   - `di/` 以下 → module ごとにファイル名でパーティション（Phase 4）、`SharedModule.kt` のみ T5-1 で最後に作成

## 検証

各タスクの PR で必要に応じて以下を実施:

- [ ] `./gradlew build` が通る
- [ ] `./gradlew :shared:allTests` が通る
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が通る
- [ ] Android 変更を含む場合: `./gradlew :composeApp:assembleDebug`
- [ ] Phase 5-2 / 5-3 のみ: Android / iOS シミュレータで `initKoin()` 実行と最低 1 API のスモーク疎通

## スコープ外（本計画では扱わない）

- 画面実装（Compose UI / SwiftUI）— デザイン確定後に別途 `/create-task` で切る。
- ViewModel 層 — Phase 4 までで Repository が `Flow` を返す状態にしておけば、ViewModel は画面タスクに同梱して書ける。
- push 通知（APNs / FCM）統合 — ActionCable によるフォアグラウンド通知は Phase 4-4 でカバーするが、バックグラウンド push は別タスク。

## 完了条件

- README 記載の全 API（AuthCore + fuju-bank-backend + ActionCable UserChannel）が shared モジュール経由で呼び出せる。
- Android / iOS の両シミュレータで `initKoin()` が成功し、少なくとも 1 つの API（例: `GET /users/:id`）が疎通する。
- 以降のタスク（画面実装）は `SharedModule` から Repository を取り出すだけで着手できる状態。
