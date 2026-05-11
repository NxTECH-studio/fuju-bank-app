# client-bank-23: 送金メモ（memo）対応

## 概要

送金フローに **任意入力のメモ（memo, 最大 80 文字）** を追加し、履歴一覧で memo ありの取引に
吹き出しアイコンを表示、取引詳細で memo 本文を表示する。サーバ側送金 API (`POST /ledger/transfer`)
は既に `memo` パラメータを受け付けており、`TransferRequest.memo` も `shared/` に存在する
（`shared/src/commonMain/.../data/remote/dto/LedgerTransferDto.kt:17`）。本タスクは
**未配線の memo を UI と取引データ取得経路に通す**ことが主目的。

クライアントの送金 Step 2（金額入力画面）に memo TextField + 80 文字 counter を追加し、
`SendFlowViewModel.submit()` から `LedgerRepository.transfer(memo = ...)` に流す。
取引一覧 / 詳細では `TransactionDto.memo` を新規フィールドとして deserialize し、
`Transaction` ドメインモデル経由で UI に届ける。

## 背景・目的

- 送金 API はサーバ側で memo を **既に受け取れる**（`server-bank-22` 送金実装時に対応済み、
  `TransferRequest.memo: String?` も client 側にある）。一方クライアント UI には memo 入力欄が
  存在せず、`SendFlowViewModel.submit()` も `memo` を渡していないため、サーバの memo 機能は
  事実上死蔵されている。
- 送金体験の自然な拡張として「何のための送金かをひと言添える」UX は MVP 範囲でも価値が大きい
  （現状: 取引履歴は `〇〇 に送りました` / `〇〇 からもらいました` のみで、用途が分からない）。
- 取引詳細画面はすでにレイアウト的に余白があり、memo セクションを差し込みやすい
  （`TransactionDetailScreen.kt:111-127` / `TransactionDetailView.swift:61-72`、AmountCard +
  DetailTransactionRow + EmotionMetadataCard の縦並び）。

## スコープ

### 含む（A〜D の 14 項目）

#### A. shared / DTO・ドメイン拡張

1. **A-1** `TransactionDto` に `memo: String? = null` フィールドを追加し `@SerialName("memo")`
   を付与する（`shared/src/commonMain/.../data/remote/dto/TransactionDto.kt`）。
   現状コメント「server の追加フィールド (`entry_id` / `memo` / `metadata` / `created_at`) は
   現状利用していないため、`Json { ignoreUnknownKeys = true }` 経由で無視」のうち memo を
   利用フィールドへ移す（コメントも更新）。
2. **A-2** ドメインモデル `Transaction`（`shared/src/commonMain/.../domain/model/User.kt:16`）に
   `val memo: String? = null` を追加。
3. **A-3** `UserRepository.transactions(...)` 内の `TransactionDto.toDomain()` で `memo` を
   マッピング（`shared/src/commonMain/.../data/repository/UserRepository.kt:134-142`）。
4. **A-4** `dummyTransactions()`（同ファイル 160-386）に memo 入りサンプルを数件混ぜ、Android/iOS
   ダミーモードで UI 確認できるようにする。

#### B. shared / 送金経路の memo 伝達

5. **B-1** `LedgerRepository.transfer(...)` の `memo` 引数（既存）が `SendFlowViewModel` から
   渡されるようにする（後段 C-3 と接続）。`shared` 自体の API シグネチャ変更は不要
   （`shared/src/commonMain/.../data/repository/LedgerRepository.kt:20` で既に `memo: String? = null`）。

#### C. composeApp（Android）/ 送金フロー

6. **C-1** `SendFlowState`（`composeApp/.../features/send/SendFlowState.kt`）に
   `val memo: String = ""` を追加。`@Immutable data class` のため `copy` の伝播に注意。
7. **C-2** `SendFlowViewModel`（同 `SendFlowViewModel.kt`）に `onMemoChange(value: String)` を
   追加。80 文字 (`MEMO_MAX_LENGTH = 80`) を超える入力は **超過分を切り捨て** て state に書き込み
   （クライアントガード、サーバ側の VALIDATION_FAILED 再現を避ける）。
   `submit()` で `ledgerRepository.transfer(..., memo = snapshot.memo.takeIf { it.isNotBlank() })`
   を渡す（空文字は `null` に正規化）。
8. **C-3** `SendAmountScreen`（同 `SendAmountScreen.kt`）に memo `OutlinedTextField` を追加
   （`BalancePreviewCard` の下、CTA の上）。
   - プレースホルダ「メモ（任意・80文字まで）」
   - 右下に `${memo.length}/80` counter（残量が 10 以下になったら警告色）
   - `singleLine = false`、`maxLines = 3` 程度
   - **OTP 同様、自動 submit は禁止**（CTA タップ必須は既存挙動）

#### D. iosApp（iOS）/ 送金フロー

9. **D-1** `ObservableSendFlowViewModel`（`iosApp/iosApp/Features/Send/ObservableSendFlowViewModel.swift`）
   に `@Published var memo: String = ""` を追加し、didSet で 80 文字を超える代入を切り詰める。
   `submit()` 経路で `memo: memo.isEmpty ? nil : memo` を `LedgerRepository.transfer(...)` に渡す。
10. **D-2** `SendAmountView`（同 `SendAmountView.swift`）に SwiftUI `TextField` (axis: `.vertical`,
    `.lineLimit(1...3)`) を追加し、右下に counter `Text("\(viewModel.memo.count)/80")`。
    Android と同じ位置（プレビューカード下、CTA 上）。

#### E. composeApp（Android）/ 履歴 UI

11. **E-1** `TransactionRow`（`composeApp/.../features/transactions/TransactionRow.kt`）に
    `transaction.memo != null && memo.isNotBlank()` の時のみ表示する **吹き出しアイコン** を追加。
    タイトル横またはサブタイトル末尾に 14dp の `ic_chat_bubble`（後述 F-1 で追加）を配置。
12. **E-2** `TransactionDetailScreen`（同 `TransactionDetailScreen.kt`）に **memo セクション** を
    `EmotionMetadataCard` の前後に追加（推奨: AmountCard 直下の DetailTransactionRow と
    EmotionMetadataCard の間）。memo なし取引ではセクションごと非表示。
    - セクションタイトル「メモ」、本文は `transaction.memo`
    - 角丸 20dp、薄ピンク背景 (`FujuBankColors.LightPink`) で他カードと統一

#### F. iosApp（iOS）/ 履歴 UI

13. **F-1** `TransactionRow.swift`（`iosApp/iosApp/Features/Transactions/TransactionRow.swift`）に
    memo 吹き出しアイコンを E-1 と同じ位置に追加（SF Symbols `bubble.left` または同等の Asset）。
14. **F-2** `TransactionDetailView.swift` に memo セクションを追加（E-2 と同じ位置・スタイル）。
    memo なし取引ではセクションごと非表示。

### 含まない（スコープ外）

- **memo に対するリアルタイム broadcast の更新**（ActionCable `UserChannel` の `MintCredited` /
  `TransferCredited` payload に memo を含めるかは **サーバ側で別タスク化**。後述「スコープ外で別タスク化」参照）
- **送金履歴の専用画面 / フィルタ**（既存履歴を memo 付きで表示するだけ。memo で検索する UI は作らない）
- **memo の編集 / 削除**（送金時のみ確定、後から変更不可）
- **memo の i18n / 絵文字バリデーション**（80 文字上限のみ。Unicode コードポイント単位ではなく
  Kotlin `String.length` / Swift `String.count` でカウント。サロゲートペアの厳密処理は不要）
- **Push 通知本文への memo 反映**（既存通知 UX を変更しない）
- **HUD / Artifact 投稿との連動**（MVP 範囲外のまま）

## 影響範囲

- モジュール: `composeApp` / `iosApp` / `shared`
- ソースセット:
  - `shared/src/commonMain/` （DTO / 取引ドメイン / Repository mapping）
  - `shared/src/commonTest/` （DTO / Repository テスト）
  - `composeApp/src/androidMain/` （送金 ViewModel / Step 2 画面 / 履歴行 / 詳細画面）
  - `iosApp/iosApp/Features/Send/`（送金 ViewModel / Step 2 View）
  - `iosApp/iosApp/Features/Transactions/`（履歴行 / 詳細 View）
- 破壊的変更:
  - **API 互換**: `Transaction`（domain）に `memo: String?` を追加。デフォルト値 `null` のため
    既存呼び出し側のコンパイルは保たれる。`TransactionDto` の `memo` も `String? = null` で nullable。
  - **公開 ABI（iOS Framework）**: `Shared.Transaction` のプロパティ追加。Swift 側は
    `transaction.memo` を読むだけなので非破壊。
  - **UI 互換**: 取引行に memo アイコンが追加されるが、memo なし取引では従来表示と同等。
- 追加依存: なし（既存 Compose Material3 `OutlinedTextField` / SwiftUI `TextField` で完結）

## 実装手順

### Phase 1: shared 側 DTO / ドメイン拡張

1. `TransactionDto` に `memo: String? = null` を追加（A-1）
2. `Transaction`（domain）に `memo: String? = null` を追加（A-2）
3. `UserRepository` の `TransactionDto.toDomain()` で `memo = memo` をマッピング（A-3）
4. `dummyTransactions()` の数件に memo を入れる（A-4、例: 「ランチ代」「コーヒー」「お疲れ」）

### Phase 2: shared / unit test 追加

5. `shared/src/commonTest/.../data/remote/dto/TransactionDtoTest.kt` に追加:
   - memo あり transfer payload の deserialize で `memo == "ランチ代"` を assert
   - memo null の payload（既存 mint テストに `null` 追加 assert）
   - 80 文字ぴったりの memo / 80 文字超 memo（サーバが返した想定）はクライアント側で**そのまま受け取る**
     （切り詰めしない＝表示側で truncate するのが責務）
6. （任意）`UserRepositoryTest.kt` で MockEngine 経由の `transactions()` 経路に memo が伝播することを 1 ケース追加

### Phase 3: 送金フロー（Android）

7. `SendFlowState` に `memo: String = ""` を追加（C-1）
8. `SendFlowViewModel` に `onMemoChange()` 追加 + `submit()` で `memo` を渡す（C-2）
9. `SendAmountScreen` に `OutlinedTextField` + counter 追加（C-3）

### Phase 4: 送金フロー（iOS）

10. `ObservableSendFlowViewModel` に `@Published var memo` 追加 + submit 経路で渡す（D-1）
11. `SendAmountView` に `TextField` + counter 追加（D-2）

### Phase 5: 履歴 UI（Android）

12. `TransactionRow` に吹き出しアイコン表示ロジック追加（E-1）
13. `TransactionDetailScreen` に memo セクション追加（E-2）

### Phase 6: 履歴 UI（iOS）

14. `TransactionRow.swift` に吹き出しアイコン追加（F-1）
15. `TransactionDetailView.swift` に memo セクション追加（F-2）

### Phase 7: 検証

16. Android で送金（memo あり / なし両方）→ 履歴・詳細での表示確認
17. iOS で同上
18. 80 文字ぴったり / 81 文字目入力時の切り詰め確認
19. memo なし取引の詳細画面で memo セクションが完全に消えていることを確認

## 受け入れ条件

1. **送金時に memo 付与可能**: 送金 Step 2 で memo を入力でき、空送信も成功する。80 文字以内に制限される。
2. **履歴一覧で memo アイコン表示**: memo ありの取引には吹き出しアイコンが行内に表示され、
   memo なし取引には表示されない。
3. **取引詳細で memo 本文表示**: memo ありの取引では詳細画面に memo 本文セクションが表示され、
   memo なし取引ではセクションごと非表示になる。
4. **80 文字上限 + counter 表示**: 送金 Step 2 の memo 欄に `n/80` counter が表示され、
   80 文字を超える入力は切り捨てられる。

## 検証手順

### Android

- [ ] `./gradlew build` が通る
- [ ] `./gradlew :shared:allTests` が通る（新規 memo テスト含む）
- [ ] `./gradlew :composeApp:assembleDebug` が通る
- [ ] 実機 / エミュレータで以下を手動確認:
  - [ ] 送金 Step 2 で memo を入力 → 送金実行 → 取引履歴と詳細に memo が反映される
  - [ ] memo なしで送金 → 履歴に吹き出しアイコンが出ない / 詳細で memo セクションが出ない
  - [ ] memo に 80 文字以上入力しようとすると 80 文字で止まる
  - [ ] counter `n/80` が即時更新される

### iOS

- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が通る
- [ ] Xcode で `iosApp` をビルドして起動できる
- [ ] iOS シミュレータで Android と同じ 4 つの手動確認を実施

## 既知の TODO・要確認事項

1. **サーバ API レスポンスでの memo 提供状況の確認**
   - 送金 API (`POST /ledger/transfer`) の **入力** が memo を受け付けるのは既に確認済み
     （`TransferRequest.memo: String?` が `shared/` 側に存在）。
   - 一方、**履歴 API** (`GET /users/:id/transactions`) と **取引詳細 API**
     （詳細専用 API がない場合は履歴 API のフィールドをそのまま使う）が `memo` フィールドを
     レスポンスに含めるかは **fuju-bank-backend 側の README / 仕様で要確認**。
   - 現状 `TransactionDto` のコメントには「server の追加フィールド (`entry_id` / `memo` /
     `metadata` / `created_at`) は現状利用していないため、`Json { ignoreUnknownKeys = true }`
     経由で無視」とあり、サーバが memo を返している前提のコメントだが、実 payload で確認が必要。
   - **対応**: 実装着手前に fuju-bank-backend の README / `transactions_controller` / serializer
     を確認し、memo がレスポンスに含まれていなければサーバ側タスクを別途起票する。
2. **吹き出しアイコンのアセット**
   - Android 用 `ic_chat_bubble.xml` / iOS 用 SF Symbols（`bubble.left`）または Figma 書き出しを
     用意するか、SF Symbols のみで済ませるかを実装時に判断。Figma に既存があれば
     `docs/figma-assets/transfer-memo/` 配下に保存する。
3. **memo の表示 truncation**
   - 履歴一覧の行は高さ固定のため、memo 本文は **アイコンのみ表示** にとどめる（本文は詳細でフル表示）。
4. **memo の改行 / 制御文字**
   - サーバ側で制御文字をどう扱うかは要確認。クライアントでは表示時に改行 `\n` のみ許容する想定。

## スコープ外で別タスク化が必要なもの

- **ActionCable broadcast（`UserChannel`）の payload に memo を含める**
  - 現状 `TransferCredited` / `MintCredited` 系のリアルタイム通知 payload に memo は含まれていない
    可能性が高い（fuju-bank-backend `UserChannel` の serializer を要確認）。
  - HUD / リアルタイム表示は本 MVP の範囲外だが、後続で取引履歴のリアルタイム反映を実装する際は
    broadcast payload に memo を追加する必要がある。**fuju-bank-backend 側のタスクとして別起票**
    （例: `server-bank-XX-broadcast-memo-payload`）。
  - 本タスクのクライアント側は **REST 経由の履歴 / 詳細取得時にのみ memo を扱う**。ActionCable
    経由のリアルタイム取引通知に memo が乗っていなくても、次回のフル再取得（pull-to-refresh /
    `HomeViewModel.refresh()` / 取引履歴画面再表示）で memo が表示される設計で割り切る。

## 技術的な補足

- **memo の文字数カウント**:
  - Kotlin: `String.length`（UTF-16 code unit count）。絵文字（サロゲートペア）は 2 としてカウントされるが、
    サーバ側のバイト長制限と差異がある可能性があるので、サーバ側の検証ロジックに合わせる必要があれば
    調整する（現状はクライアント側 80 / サーバ側 80 で同一仮定）。
  - Swift: `String.count`（Grapheme Cluster count）。Kotlin との非対称があるが、80 文字程度の
    短文かつどちらも目視で大きく違和感のない範囲のため、UX 上は許容する。
- **`memo.takeIf { it.isNotBlank() }`**:
  - 空文字 `""` を送ると一部のサーバ実装で空文字が persist される可能性があるため、空 → `null`
    に正規化する（受け入れ条件 1「空送信も OK」を素直に満たす実装）。
- **送金フロー中のフッター非表示**:
  - 既存の `showBottomBar` ロジック（`CLAUDE.md` の「送金フロー中はフッター非表示」）は本タスクで
    変更しない。memo TextField のフォーカスでキーボードが出ても、フッターは既に非表示なので
    干渉しない。
- **Compose の IME / Soft keyboard**:
  - memo 欄は `singleLine = false` のため、IME Done で送信されることはない（OTP 自動 submit 禁止
    ポリシーとも整合）。
- **dummyTransactions の Locale**:
  - サンプル memo はすべて日本語の短文（「ランチ代」「コーヒー」「お疲れ様」など）で統一し、
    `useDummyData = true` 経路でも UI 確認できるようにする。
