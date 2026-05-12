# 取引詳細 mint metadata の実データ配線

## 概要

mint kind 取引の詳細画面に、サーバが `GET /users/:id/transactions` で返している
`metadata.n_exposures` / `metadata.target_date` / `metadata.model_version` を
**DTO → ドメイン → UI** まで通し、現状ハードコードの「滞留時間 / 視線強度」モック表示を
**実 mint 契約に沿った 3 行（接触回数 / 対象日 / モデルバージョン）**に置き換える。

**確定スコープ**: mint kind のみ。transfer は client が metadata を送出していないため
DB 上の `metadata` は常に `{}` で、感情データカードは描画しない（カードごと非表示）。

**経緯（dwell_seconds 想定の廃案）**: 当初は `metadata.dwell_seconds` / `gaze_strength`
（HUD raw signals）を bank の jsonb から取り出して表示する前提で計画していた。しかし
隣接リポジトリ `fuju-emotion-model` (mining) の実コード調査の結果、
**mining → bank に送られる metadata は `{n_exposures, target_date, model_version}` の 3 キー固定**
であり、`dwell_seconds` / `gaze_strength` は HUD → mining の上流側で消費される raw signals で
bank には届かないことが判明したため、本計画書は確定 contract ベースで全面リライト済み。
旧 bank backend の DB コメント「滞留秒数・視線強度など」および README の fixture 例は古い
名残で、実 payload と一致しない（変更履歴セクション参照）。

## mining との確定 contract（コード行番号引用つき）

### payload 形状（mining 側で構築）

`fuju-emotion-model/src/fuju_emotion_model/batch/mint_dispatch.py:232-241`:

```python
metadata = {
    "model_version": active.version,         # str, 例: "v_2026-04-26" / "dummy_test"
    "n_exposures": int(agg["n"]),            # int, 集計期間中の露出（視線接触）回数
    "target_date": target_date.isoformat(),  # ISO date "YYYY-MM-DD", 集計対象日
}
```

補強根拠:

- `fuju-emotion-model/src/fuju_emotion_model/services/bank_client.py:109` のコメント:
  `Conventionally {tenant_id, model_version, n_exposures}`
- `fuju-emotion-model/tests/e2e/test_full_pipeline.py:270-272` assertion:
  実 payload `{"n_exposures":3,"target_date":"2026-05-10","model_version":"dummy_test"}` と完全一致
- mining は 1 日 1 回 / author / target_date 単位で集約 mint:
  `idempotency_key = f"{author_id}-{target_date}-{model_version}"`

### bank backend 側の取り扱い

- `fuju-bank-backend/app/controllers/ledger_controller.rb:39, 60`:
  client/mining から送られた metadata Hash を **バリデーションなしで素通し** で jsonb に保存
- `fuju-bank-backend/app/controllers/user_transactions_controller.rb:44`:
  GET レスポンスで metadata を **そのまま** 返す
- ローカル DB は現状空（development / test 共に 0 件）

### client 側の現状

- `shared/.../api/LedgerApi.kt:23-43`: client は `/ledger/transfer` を叩くが
  **`TransferRequest` に metadata フィールドなし** → transfer の DB metadata は常に `{}`
- client は `/ledger/mint` を叩く実装がない（mining 専用経路）
- `shared/.../dto/TransactionDto.kt:19-21`: コメント通り `Json { ignoreUnknownKeys = true }`
  で metadata キーを黙殺中 → UI に届かない

## UI 表示確定

### mint kind の詳細画面「感情データ」カード

```
感情データ
  接触回数         3 回                # n_exposures (Int)
  対象日           2026/05/10          # target_date (ISO date → YYYY/MM/DD で表示)
  モデルバージョン  dummy_test          # model_version (文字列そのまま)
```

- 表示順は上記固定（接触回数 → 対象日 → モデルバージョン）。
- フォーマット:
  | フィールド | 入力型 | 表示文言 | 例 |
  |---|---|---|---|
  | `n_exposures` | `Int` | `"${value} 回"` | `3 回` / `12 回` |
  | `target_date` | ISO `YYYY-MM-DD` 文字列 | `YYYY/MM/DD`（日本式スラッシュ） | `2026/05/10` |
  | `model_version` | `String` | 文字列そのまま | `dummy_test` / `v_2026-04-26` |
- `model_version` は `dummy_test` も含めて **そのまま表示する**。理由: 運用デバッグ・本番モデル
  切替後の信頼根拠として版を可視化したい。文字列加工（例: `dummy_test` を「デモ」に翻訳）はしない。

### 表示分岐

| kind | metadata 状態 | カード表示 |
|---|---|---|
| `mint` | 3 キーすべて存在 | 3 行表示 |
| `mint` | 一部キーのみ存在（防御） | 存在する行のみ表示。欠落行は描画しない |
| `mint` | `metadata == {}` または全キー欠落 | **カードごと非表示** |
| `transfer` | （DB 上は常に `{}`） | **カードごと非表示** |

「個別キー欠落 → 行のみ非表示」は mining contract が 3 キー固定で必ず set される前提に
反する防御的フォールバック。3 行全部欠けたら結局カードは空なのでカードごと非表示に倒す。

## 影響範囲

- モジュール: `shared` / `composeApp` / `iosApp`
- ソースセット:
  - `shared/src/commonMain/`:
    - `data/remote/dto/TransactionDto.kt` （`metadata` を利用フィールドに昇格 + 新規
      `MintMetadataDto` 追加）
    - `domain/model/User.kt` （`Transaction.metadata: MintMetadata?` 追加 + 新規
      `MintMetadata` 追加。**iOS 公開 ABI への追加のみで破壊なし**）
    - `data/repository/UserRepository.kt` （DTO → ドメインマッピング、`dummyTransactions()`
      の mint 数件に metadata 入りサンプル）
    - `format/MintMetadataFormatter.kt` （新規、`commonMain` 完結のフォーマッタ）
  - `shared/src/commonTest/`:
    - DTO デシリアライズテスト
    - Repository マッピングテスト
    - フォーマッタテスト
  - `composeApp/src/androidMain/.../features/transactions/TransactionDetailScreen.kt`:
    `EmotionMetadataCard()` を `EmotionMetadataCard(metadata: MintMetadata)` に書き換え
  - `iosApp/iosApp/Features/Transactions/TransactionDetailView.swift`:
    `EmotionMetadataCard` を `EmotionMetadataCard(metadata: Shared.MintMetadata)` に書き換え
- 破壊的変更:
  - **公開 ABI（iOS Framework）**: `Shared.Transaction` にプロパティ `metadata` 追加 +
    `Shared.MintMetadata` 新規型公開。Swift 側は読むだけなので **非破壊（追加のみ）**。
  - **UI**: 詳細画面のモック値「18 秒 / 0.94」が消え、mint 取引のみ実値表示。transfer 取引は
    カードが消える（現在はモック値で常時表示中）。
- 追加依存: なし（`kotlinx-datetime 0.8.0` が既に `libs.versions.toml:19,57` に存在。
  `LocalDate.parse()` をそのまま利用）。

## 設計判断

### 1. typed なドメインモデル `MintMetadata` を切る

`Transaction.metadata: JsonElement?` のような untyped 保持は避ける。理由:

- UI 側で 3 キーの存在を都度確認するコードが分散する
- テストでキー名タイポが検出できない
- iOS 側で `JsonElement` を扱うのは `Kotlinx_serialization_jsonJsonElement` 経由になり面倒

ドメインモデル:

```kotlin
// shared/src/commonMain/.../domain/model/User.kt に追記
data class MintMetadata(
    val nExposures: Int?,
    val targetDate: kotlinx.datetime.LocalDate?,
    val modelVersion: String?,
)
```

- **3 フィールドとも nullable**: contract 上は必ず set だが、防御的に nullable で受け、
  UI 側で「行ごとに描画判定」する。`requireNotNull` で例外を投げる方針は採らない（mining 側
  payload の将来形状変化に対する耐性を優先）。
- `target_date` は `LocalDate` で保持し、`String` のまま持ち回すのを避ける。`kotlinx-datetime`
  既存依存なので追加コストなし。パース失敗時は `null` に倒す（Repository マッピングで `runCatching`）。

### 2. DTO は文字列＋nullable で受ける

```kotlin
// shared/src/commonMain/.../data/remote/dto/TransactionDto.kt に追記
@Serializable
data class MintMetadataDto(
    @SerialName("n_exposures") val nExposures: Int? = null,
    @SerialName("target_date") val targetDate: String? = null,   // ISO date string のまま
    @SerialName("model_version") val modelVersion: String? = null,
)
```

- DTO は **payload 防御で nullable + default null** 固定。
- `target_date` は **DTO レベルでは String のまま**保持。`LocalDate` 変換は Repository 層で
  実施（パース失敗時に DTO 全体の deserialize が落ちないように）。

### 3. `TransactionDto.metadata: MintMetadataDto?`

```kotlin
@SerialName("metadata")
val metadata: MintMetadataDto? = null,
```

- `Json { ignoreUnknownKeys = true }` は継続利用（mining 側が将来 `tenant_id` 等を追加しても
  落ちないように）。
- `metadata == null` または `{}` 相当（全フィールド null）の場合は Repository 層で
  `Transaction.metadata = null` に正規化（後述）。

### 4. `Transaction.metadata: MintMetadata?`

- mint kind のみ非 null になる前提だが、**`kind` での絞り込みは UI 側で行う**。
  ドメインモデル上は kind と独立して nullable。
- transfer は DB metadata が `{}` なので Repository マッピング後は自動的に `null` になる
  （全フィールド null → `Transaction.metadata = null` に正規化）。

### 5. フォーマッタは `commonMain` 完結

`shared/src/commonMain/.../format/MintMetadataFormatter.kt` 新設:

```kotlin
object MintMetadataFormatter {
    fun formatExposures(value: Int): String = "$value 回"
    fun formatTargetDate(value: kotlinx.datetime.LocalDate): String =
        buildString {
            append(value.year)
            append('/')
            append(value.month.number.toString().padStart(2, '0'))
            append('/')
            append(value.day.toString().padStart(2, '0'))
        }
    fun formatModelVersion(value: String): String = value
}
```

- 日本式ゼロ埋め `YYYY/MM/DD` で固定（既存 `TransactionDateFormatter` は月日ゼロ埋め
  「しない」仕様だが、`target_date` は集計対象日でカレンダー表示の意味合いが強いため
  **ゼロ埋め採用** で見やすさを優先）。
- iOS は `MintMetadataFormatterKt.formatExposures(value:)` 等で呼ぶ
  （`Kt` suffix は CLAUDE.md「Swift から触られる Kotlin ファイル名は安易にリネームしない」遵守）。
- `formatModelVersion` は実質 identity だが、将来「dummy 版を非表示にする」等の
  プロダクト判断が入ったときに集約点として残すため関数化しておく。

### 6. Repository マッピング

`shared/src/commonMain/.../data/repository/UserRepository.kt:135-145` の `toDomain()` を拡張:

```kotlin
private fun TransactionDto.toDomain(): Transaction = Transaction(
    id = id,
    kind = kind,
    direction = resolveDirection(kind, direction),
    amount = amount,
    counterpartyUserId = counterpartyUserId,
    counterpartyPublicId = counterpartyPublicId,
    artifactId = artifactId,
    occurredAt = Instant.parse(occurredAt),
    memo = memo,
    metadata = metadata?.toDomain(),
)

private fun MintMetadataDto.toDomain(): MintMetadata? {
    val parsedDate = targetDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val domain = MintMetadata(
        nExposures = nExposures,
        targetDate = parsedDate,
        modelVersion = modelVersion?.takeIf { it.isNotBlank() },
    )
    // 全フィールド null（≒ {} or 不正 payload）なら Transaction.metadata = null へ正規化
    return if (domain.nExposures == null && domain.targetDate == null && domain.modelVersion == null) {
        null
    } else {
        domain
    }
}
```

- `runCatching { LocalDate.parse(it) }.getOrNull()`: 不正 ISO 文字列が来ても落ちず、行非表示で
  劣化する設計。
- `modelVersion.isNotBlank()`: 空文字防御。
- transfer は DB 上 `{}` なので `MintMetadataDto(null,null,null) → toDomain() == null` で
  `Transaction.metadata = null` に正規化される。

## 実装ステップ

### Phase 0: 旧 `dwell_seconds` 想定の痕跡確認・廃案部の特定

旧計画書（dwell_seconds 想定版）が指していた既存コードの痕跡を確認し、本リライトで上書き
されるべき箇所を網羅する。

- [ ] `composeApp/.../TransactionDetailScreen.kt:330-352` の `EmotionMetadataCard()` 内
  `EmotionMetadataRow(label = "滞留時間", value = "18 秒")` /
  `EmotionMetadataRow(label = "視線強度", value = "0.94")` を本タスクで全置換することを確認
- [ ] `iosApp/.../TransactionDetailView.swift:225-251` の `EmotionMetadataCard` 内
  同モック表示（"18 秒" / "0.94"）を本タスクで全置換することを確認
- [ ] `shared/.../dto/TransactionDto.kt:19-21` のコメント
  「server の追加フィールド (entry_id / metadata / created_at) は現状利用していない」
  のうち `metadata` を本タスクで利用フィールドへ昇格することを確認
- [ ] grep で `dwell_seconds` / `gaze_strength` の文字列が他に残っていないか確認
  （README / 過去タスク doc にあれば「過去資料として保持」扱いとし、本リポジトリ Kotlin/Swift
  コードからは完全除去する）

### Phase 1: shared 側 DTO / ドメイン / マッピング

1. `TransactionDto.kt`:
   - `MintMetadataDto` data class 追加（`n_exposures` / `target_date` / `model_version`、すべて nullable + default null）
   - `TransactionDto` に `@SerialName("metadata") val metadata: MintMetadataDto? = null` 追加
   - ファイル冒頭コメントの「`metadata` は無視」を「`metadata` は mint 用に利用」へ更新
2. `User.kt`:
   - `MintMetadata` data class 追加（`nExposures: Int?` / `targetDate: LocalDate?` / `modelVersion: String?`）
   - `Transaction` に `val metadata: MintMetadata? = null` 追加
3. `UserRepository.kt`:
   - `TransactionDto.toDomain()` に `metadata = metadata?.toDomain()` 行追加
   - `MintMetadataDto.toDomain(): MintMetadata?` private 拡張を追加（runCatching パース + 全 null → null 正規化）
   - `dummyTransactions()` の mint 系（`txn_dummy_004` / `txn_dummy_009` / `txn_dummy_014` /
     `txn_dummy_019` / `txn_dummy_024` の 5 件）すべてに `metadata = MintMetadata(...)` を
     付与:
     - 1 件は `n_exposures = 3, target_date = 2026-05-10, model_version = "dummy_test"`
       （mining 側の e2e fixture と一致させてデバッグしやすく）
     - 残りは `n_exposures` を変えて多様性を確保（例: 12 / 47 / 1 / 200）、`target_date` も
       それぞれの `occurred_at` の日付に揃える、`model_version` はうち 1 件だけ `v_2026-04-26`
       にして本番モデル想定の見え方を確認
     - 防御フォールバック確認用に 1 件だけ `targetDate = null` の片肺欠落も混ぜる（運用には
       発生しない想定だが UI 行非表示の動作確認用）

### Phase 2: shared / フォーマッタ追加

4. `shared/src/commonMain/.../format/MintMetadataFormatter.kt` 新規作成
   （`formatExposures` / `formatTargetDate` / `formatModelVersion`）

### Phase 3: shared / 単体テスト追加

5. `shared/src/commonTest/.../data/remote/dto/TransactionDtoTest.kt`:
   - mining 実 payload `{"n_exposures":3,"target_date":"2026-05-10","model_version":"dummy_test"}`
     をそのまま decode して全フィールド一致を assert
   - `"metadata": {}` で全フィールド null になることを assert
   - `metadata` キーが payload に存在しない場合のデフォルト null を assert
   - `"metadata": null` で `decoded.metadata == null` を assert
   - 1 キーのみ存在する片肺ペイロード（例: `{"n_exposures": 5}`）でデシリアライズが成功し、
     他フィールドが null になることを assert（防御）
   - mining 側未知フィールド（例: `tenant_id`）を含むペイロードでも decode が落ちないことを
     assert（`ignoreUnknownKeys` 検証）
6. `shared/src/commonTest/.../data/repository/UserRepositoryTest.kt`:
   - MockEngine が `metadata: {n_exposures, target_date, model_version}` を含む mint 取引を
     返したとき、`Transaction.metadata` に 3 フィールドが伝播することを assert
   - `metadata: {}` の mint 取引で `Transaction.metadata == null` に正規化されることを assert
   - transfer 取引（payload に metadata なし or `{}`）で `Transaction.metadata == null` を assert
   - `target_date: "not-a-date"` の不正 payload で `targetDate == null` に劣化し、他フィールドは
     伝播することを assert（runCatching 動作確認）
7. `shared/src/commonTest/.../format/MintMetadataFormatterTest.kt` 新規:
   - `formatExposures(0) == "0 回"` / `formatExposures(3) == "3 回"` / `formatExposures(120) == "120 回"`
   - `formatTargetDate(LocalDate(2026, 5, 10)) == "2026/05/10"`
   - `formatTargetDate(LocalDate(2026, 12, 31)) == "2026/12/31"`
   - `formatModelVersion("dummy_test") == "dummy_test"` /
     `formatModelVersion("v_2026-04-26") == "v_2026-04-26"`

### Phase 4: 詳細画面（Android）

8. `TransactionDetailScreen.kt`:
   - `LoadedContent` 内の `EmotionMetadataCard()` 呼び出しを
     `transaction.metadata?.let { EmotionMetadataCard(metadata = it) }` に変更（カードごと非表示）
   - `EmotionMetadataCard` のシグネチャを `EmotionMetadataCard(metadata: MintMetadata)` に変更
   - 内部で 3 行表示。各行は `metadata.<field>?.let { EmotionMetadataRow(label, format(...)) }`
     で欠落時に行ごと描画スキップ
   - タイトル「感情データ (metadata)」から括弧を削り「**感情データ**」に変更（UX 改善。括弧書きは
     開発者向け表記の名残）
   - ラベル: 「接触回数」「対象日」「モデルバージョン」（日本語ハードコード、既存方針踏襲）

### Phase 5: 詳細画面（iOS）

9. `TransactionDetailView.swift`:
   - 親 View で `transaction.metadata` を unwrap し、非 null のときだけ
     `EmotionMetadataCard(metadata: m)` を描画（Android と同じ「カードごと非表示」）
   - `EmotionMetadataCard` のシグネチャを `EmotionMetadataCard(metadata: Shared.MintMetadata)` に変更
   - フォーマット呼び出しは `MintMetadataFormatterKt.formatExposures(value:)` /
     `formatTargetDate(value:)` / `formatModelVersion(value:)`
   - `n_exposures` / `targetDate` / `modelVersion` のうち nil の行は描画しない

### Phase 6: 検証

10. Android: dummy モードで mint 取引（複数バリエーション）の詳細を開き、3 行表示を目視確認
11. Android: transfer 取引の詳細でカードが消えていることを目視確認
12. Android: 片肺欠落 dummy（`targetDate = null`）で対象日行のみ消えることを目視確認
13. iOS: 同じ 3 パターンをシミュレータで目視確認、Android と表示が一致することを確認
14. （DB データが入ったら）実 mining mint を流して実 payload の表示を目視確認 — DB 空なので
    本タスク完了条件には含めない（後日 mining 連携試験時に追加検証）

## 受け入れ条件

- [ ] **mint 取引詳細**: API レスポンスの `metadata.n_exposures` / `target_date` /
  `model_version` が日本語ラベル付きで 3 行表示される
- [ ] **transfer 取引詳細**: 感情データカードが描画されない（モック「18 秒 / 0.94」が消える）
- [ ] **mint で metadata が `{}`**: 感情データカードが描画されない
- [ ] **片肺欠落（防御）**: 個別キー欠落時にその行のみ描画されない（他の行は表示される）
- [ ] **取引一覧（履歴行）の表示は影響なし**: 一覧では metadata を表示しない
- [ ] **shared/commonTest pass**: DTO デシリアライズ / Repository マッピング / フォーマッタの
  3 系統テストが pass
- [ ] **Android / iOS 両方で目視確認済み**: dummy モードでの 3 パターン（mint metadata あり /
  transfer / 片肺欠落）の表示を両 OS で目視し、一致することを確認

## 検証コマンド

```bash
./gradlew build                                          # 全ターゲットビルド
./gradlew :shared:allTests                               # commonTest 全ケース pass
./gradlew :composeApp:assembleDebug                      # Android UI 変更ビルド
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64    # iOS Framework ビルド
```

iOS 実機/シミュレータでの UI 確認は Xcode 側で `iosApp` を起動して実施。

## スコープ外

- mining 側の本番モデル切替（`dummy_test` → `v_xxxx`）。mining 担当の責務。
- transfer の metadata 送出（client が `/ledger/transfer` で metadata を送る経路の新設）。
  別タスクで判断。
- `CreditEventDto.metadata` のドメインマッピング（ActionCable 経由のリアルタイム経路）。
  HUD/Artifact が MVP 外なので本タスクでは触らない。
- 多言語化（i18n リソース化）。既存方針通り日本語ハードコード継続。
- HUD raw signals（`dwell_seconds` / `gaze_strength`）の表示。bank には届かない。
- 取引履歴一覧行への metadata 反映。Figma も未確定で詳細画面のみ対象。
- metadata の編集 / 削除。仕様外。
- `model_version` の人間可読化（例: `dummy_test` を「デモ」表示）。当面そのまま生文字列表示。

## 技術的な補足

- **`Json { ignoreUnknownKeys = true }` の継続使用**: `MintMetadataDto` のフィールドも
  すべて `nullable + default null` でロバスト性を確保。mining 側が将来 `tenant_id` /
  `confidence` 等を追加しても deserialize が落ちない。
- **`expect`/`actual` は不要**: フォーマッタは pure Kotlin（`commonMain`）で書ける。
  Locale 依存の数値フォーマットは整数の `"N 回"` も日付の `YYYY/MM/DD` も不要なので
  プラットフォーム分岐なし。
- **Swift interop**: `Shared.MintMetadata` は Kotlin/Native → Obj-C 公開で
  `SharedMintMetadata` 相当に変換される。`MintMetadataFormatter` は object なので
  Swift からは `MintMetadataFormatterKt.formatExposures(value: 3)` 形式で呼ぶ点に注意
  （CLAUDE.md「Swift から触られる Kotlin ファイル名は安易にリネームしない」遵守）。
- **DI 変更なし**: Koin モジュールに新規型を登録する必要なし（DTO / ドメイン / フォーマッタは
  すべて純粋型）。
- **`kotlinx-datetime` の `LocalDate`**: 既存依存
  （`libs.versions.toml:19,57` の `kotlinxDatetime = "0.8.0"`）をそのまま利用。`LocalDate.parse()`
  は ISO `YYYY-MM-DD` を受け付ける標準動作。
- **CreditEvent 経路との非対称**: `CreditEventDto.metadata: JsonElement?` は domain mapping
  していないが、`UserChannel` broadcast でリアルタイム更新が来ても詳細画面は履歴 API 経由で
  fetch されるため、本タスクでは ActionCable 側の domain 化は不要。
- **`dummyTransactions()` の透明性**: e2e fixture と同じ `(3, 2026-05-10, dummy_test)` を 1 件
  含めておくと、ユーザーが mining → bank → client を手動で繋いだときの動作確認が高速になる。

## 変更履歴

### 2026-05-12（最新）: mining 側コード確認による契約確定 — 全面リライト

- 隣接リポジトリ `fuju-emotion-model` の実コード（`batch/mint_dispatch.py:232-241` /
  `services/bank_client.py:109` / `tests/e2e/test_full_pipeline.py:270-272`）を直接調査し、
  bank に送られる metadata は **`{n_exposures, target_date, model_version}` の 3 キー固定**
  であることを確定。
- 旧計画で前提にしていた `dwell_seconds` / `gaze_strength` は HUD → mining の上流 raw signals で
  bank には届かない（廃案）。bank backend の DB コメントと README fixture の `dwell_seconds /
  gaze_strength` 例は古い名残であることを確認。
- Resolution 3 / 4 を廃止（gaze_strength 表示 / dwell_seconds 型はそもそも対象フィールドが
  存在しない）。
- Resolution 1（null 時カード非表示）と Resolution 2（片肺欠落の行非表示フォールバック）と
  Resolution 5（単体テスト追加）は維持し、確定契約に合わせて記述を更新。
- Phase 0 を「backend スキーマ確認（旧版のブロッカー）」から「旧 dwell_seconds 想定の痕跡確認」に
  差し替え（mining 側 contract が確定したため backend スキーマ確認は不要）。

### 2026-05-11（旧）: ユーザー Resolution 確定回答反映

旧計画書（dwell_seconds 想定版）でユーザーから受領した Resolution 1〜5 の方針を反映した版。
本リライトで前提（dwell_seconds / gaze_strength）が廃案になったため、Resolution 3 / 4 は
無効化し、Resolution 1 / 2 / 5 のみ本版に引き継ぎ。
