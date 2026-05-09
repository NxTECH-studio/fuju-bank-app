# 取引履歴の本番運用対応（ホームの最近の取引 + サブタイトル文言）

## 概要

ホーム画面の「最近の取引履歴」セクションを完全モックから実 API 連携に切り替え、
取引一覧 / 取引詳細に残っている Figma 固定文「18秒みつめられた」を kind 別の
適切な文言（受け取り / 送金 / 発行）に置き換える。Android / iOS 両プラットフォーム同時対応。

> **前提タスク**: `transaction-listing-dto-alignment.md` の merge が必須。
> client / server の取引一覧 schema 乖離を先に解消しないと
> `UserRepository.transactions(userId)` が production で deserialize 失敗する。

## 背景・目的

- ホームの「最近の取引履歴」3 件は `MOCK_RECENT_TRANSACTIONS`（Android）/
  `mockRecentTransactions`（iOS）で「トマトのイラスト / +42 ふじゅ〜 / 2025/3/4 12:03:03」を
  ハードコード表示している。実ユーザーがログインしても見える内容が変わらず、
  本番運用では破綻する。
- 取引行 / 取引詳細のサブタイトル「18秒みつめられた」（`TransactionDisplay.kt` /
  `TransactionDisplay.swift` の `*subtitlePlaceholder`）は Figma 上の固定文。
  視線秒数（gazedSeconds）は現状 API レスポンス（`TransactionDto`）に含まれておらず、
  どの取引にも一律で同じ値が出てユーザーを誤認させるため、kind 別の文言に置き換える。
- 本タスクは **MVP の「お金を受け取れる」体験を本番品質に持ち上げる** ための
  ホーム画面ポリッシュ。視線データ統合・名前解決・debug ダミー整理は後続。

### バックエンド側の前提（fuju-bank-backend PR #80）

- 2026-05-03 に [`fuju-bank-backend#80`](https://github.com/NxTECH-studio/fuju-bank-backend/pull/80)
  (`feat(ledger): service token 代理 mint + ULID 受取人 ID 統一`) が merge 済み。
  これにより **fuju-emotion-model から service token 経由の代理 mint** が本番運用可能になり、
  クリエイター payout として `ledger_transactions` に Mint レコードが積まれるようになった。
- MVP「お金を受け取れる」体験の **主経路** はまさにこの fuju 代理 mint 経由の Mint。
  本タスクの最重要価値は「ユーザーが fuju 投稿で得た payout が、ログイン直後のホームに
  最新 3 件として正しく出る」状態を作ること。
- PR #80 が暗黙に持ち込んだ表示要件:
  - **代理 mint は `artifact_id = NULL`** で記帳される（fuju 投稿は Bank 側に Artifact として
    ミラーしない契約）。Bank 内発生の Mint と fuju 由来の Mint を**サブタイトル文言で区別する余地**がある。
  - **mint 受取人 ID は AuthCore `sub` (ULID = `external_user_id`)** に統一。ただし
    `/users/:id/transactions`（path / response）は PR #80 では変更されておらず、Bank 内部
    PK ベースのまま。`SessionStore.userId` も `getMe().id` 経由で内部 PK の文字列化なので、
    現状の経路は内部 PK で一気通貫で揃っており、本タスクで ULID 化する必要はない
    （調査結果: `users_controller.rb:36-43` / `user_transactions_controller.rb:8` /
    `SessionStore.kt:92` / `UserDto.kt:13-22`）。
- 取引一覧 API (`/users/:id/transactions`) 自体は PR #80 では変更されていないが、
  **client / server の payload schema が PR #80 以前から乖離していた**ことが調査で判明し、
  前提タスク `transaction-listing-dto-alignment.md` で先に整合させる。本タスクは
  整合済みの `UserRepository.transactions(userId)` を再利用する形に変わる。

## 影響範囲

- モジュール: `composeApp` / `iosApp` / `shared`
- ソースセット:
  - `shared/src/commonMain/`（変更なし。既存の `UserRepository.transactions(userId)` を再利用）
  - `shared/src/iosMain/`（`TransactionsFlowIos.kt` に「最近 N 件」用の関数を追加）
  - `composeApp/src/androidMain/`（HomeViewModel / HomeScreen / TransactionRow / TransactionDetailScreen / TransactionDisplay / RootScaffold）
  - `iosApp/iosApp/`（HomeViewModel / HomeView / TransactionRow / TransactionDetailView / TransactionDisplay / RecentTransactionsSection）
- 破壊的変更:
  - `HomeViewModel`（Android）コンストラクタに `userRepository: UserRepository` /
    `sessionStore: SessionStore` を追加（DI 拡張、外部 API への影響は `RootScaffold` のみ）。
  - `HomeUiState.Loaded` に `recentTransactions: RecentTransactionsState` フィールドを追加（内部 sealed）。
  - iOS の `HomeUiState`（enum）の associated value も同様に拡張。
  - `RecentTransactionItem` は表示モデルを保ったまま（後続で `subtitle` フィールドを足す余地は残す）。
  - 公開 ABI（shared framework）への追加は `TransactionsFlowIos.fetchRecentTransactions` 1 関数のみ。
- 追加依存: なし（`gradle/libs.versions.toml` 更新不要）

## 実装ステップ

### A. shared 側の最小拡張

1. **`shared/src/iosMain/kotlin/studio/nxtech/fujubank/transactions/TransactionsFlowIos.kt`**
   - 既存 `fetchMyTransactions(...)` の隣に、Swift から呼ぶ「最近 N 件」用ファサードを追加する:
     ```
     fun fetchRecentTransactions(
         userRepository: UserRepository,
         sessionStore: SessionStore,
         limit: Int,
         onResult: (TransactionsLoadOutcome) -> Unit,
     ): Job
     ```
   - 中身は既存 `fetchMyTransactions` と同じ流れで、`Success` 時に
     `result.value.sortedByDescending { it.occurredAt }.take(limit)` する。
   - `TransactionsLoadOutcome` は既存型をそのまま再利用（`Loaded` / `Failure` /
     `NetworkFailure` / `Unauthenticated`）。
   - Koin / 既存 `transactionsScope` をそのまま使う。

2. `shared/src/commonMain/.../UserRepository.kt` の `dummyTransactions()` には触らない
   （後続タスクで整理）。

### B. Android 実装

3. **`composeApp/src/androidMain/.../features/home/HomeUiState.kt`**
   - `Loaded` に `recentTransactions: RecentTransactionsState` を追加。
   - 同ファイル内に `sealed interface RecentTransactionsState { Loading; data class Ready(items: List<RecentTransactionItem>); data class Error(message: String) }` を新設。

4. **`composeApp/src/androidMain/.../features/home/HomeViewModel.kt`**
   - コンストラクタに `userRepository: UserRepository` / `sessionStore: SessionStore` を追加。
   - `load(initial)` 内を `coroutineScope { val p = async { profileRepository.getMyProfile() }; val t = async { fetchRecentTransactions() }; ... }` で並列化。
   - profile 失敗時は従来通り `HomeUiState.Error` に遷移（既存挙動維持）。
   - profile 成功 + transactions 失敗時は `HomeUiState.Loaded(profile, recentTransactions = RecentTransactionsState.Error(...))` とし、ホーム本体は表示する。
   - `fetchRecentTransactions()` は private suspend 関数として、`SessionStore` から `userId` を取り、
     `UserRepository.transactions(userId)` を叩いて `sortedByDescending { it.occurredAt }.take(3)` する。
     未認証 / userId 空文字 / NetworkResult.Failure / NetworkResult.NetworkFailure はすべて
     `RecentTransactionsState.Error("最近の取引を取得できませんでした")` に集約（ホーム自体は落とさない）。
   - 取引のみ再試行用の `refreshRecent()` を追加（Recent セクションだけリロード）。

5. **`composeApp/src/androidMain/.../features/home/HomeScreen.kt`**
   - `MOCK_RECENT_TRANSACTIONS` を削除。
   - `LoadedContent` で `state.recentTransactions` を分岐:
     - `Loading` → セクション内に小さい CircularProgressIndicator
     - `Ready(items)` → `RecentTransactionsSection(items = items, ...)`。`items.isEmpty()` の場合は「取引履歴はまだありません」プレースホルダ。
     - `Error(message)` → `RecentTransactionsSection` のスロットに「読み込めませんでした」テキスト + 再試行ボタン（`viewModel::refreshRecent`）を表示。
   - 取引 → `RecentTransactionItem` の変換は `HomeViewModel` 内で実施し、
     既存 `TransactionRowVariant.from(transaction)` と同じロジック（mint=「アーティファクト xxxxxx」/
     incoming=「xxxxxx からもらいました」/ outgoing=「xxxxxx に送りました」）でタイトルを組み立て、
     sign / amount / `formatTransactionDateTimeSlash(occurredAt)` を入れる。
   - `RecentTransactionItem` の `data class` は既存のまま（subtitle なし）を維持。

6. **`composeApp/src/androidMain/.../features/home/components/RecentTransactionsSection.kt`**
   - 必要に応じて `RecentTransactionCard` の sign の色を direction に合わせて切替えるオプションを追加
     （Outgoing は黒、Mint/Incoming はピンク）。実装は `RecentTransactionItem` に
     `amountColorIsPink: Boolean` のような表示用フラグを 1 個足すか、既存
     `TransactionRowVariant` を `RecentTransactionItem` から導出可能な形にする。
   - 共通化を優先するなら `RecentTransactionItem` に `direction: TransactionDirection` を持たせ、
     `RecentTransactionCard` 内で sign / 色を派生させる方針が望ましい（Android / iOS 両方の表示モデルを揃えられる）。

7. **`composeApp/src/androidMain/.../features/transactions/TransactionDisplay.kt`**
   - `TRANSACTION_ROW_SUBTITLE_PLACEHOLDER` 定数を削除。
   - 代替として、kind 別文言を返す関数を追加。Mint は PR #80 を踏まえて
     `artifactId` の有無で代理 mint (fuju クリエイター payout) と Bank 内 Mint を分ける:
     ```
     internal fun transactionRowSubtitle(transaction: Transaction): String = when (transaction.direction) {
         TransactionDirection.Mint ->
             if (transaction.artifactId == null) "fuju からの受け取り" else "発行されたアーティファクト"
         TransactionDirection.Incoming -> "受け取り"
         TransactionDirection.Outgoing -> "送金"
     }
     ```
   - 文言の最終決定が必要なら計画レビュー時に確定する（暫定値として上記を提案、未決事項参照）。

8. **`composeApp/src/androidMain/.../features/transactions/TransactionRow.kt`**
   - `TRANSACTION_ROW_SUBTITLE_PLACEHOLDER` を `transactionRowSubtitle(transaction)` に置換。

9. **`composeApp/src/androidMain/.../features/transactions/TransactionDetailScreen.kt`**
   - 同上 (`DetailTransactionRow` 内の `TRANSACTION_ROW_SUBTITLE_PLACEHOLDER`) を関数呼び出しに置換。
   - 既存の Mint title フォールバック (`artifactId?.let { "アーティファクト …" } ?: "発行"`)
     はそのまま流用（PR #80 で `artifact_id = NULL` のケースは元々想定済）。

10. **`composeApp/src/androidMain/.../features/shell/RootScaffold.kt`**
    - `HomeViewModel` の生成箇所（行 125-133 付近）に `userRepository` / `sessionStore` を Koin から注入するよう修正:
      ```
      HomeViewModel(
          profileRepository = KoinPlatform.getKoin().get<ProfileRepository>(),
          userRepository = KoinPlatform.getKoin().get<UserRepository>(),
          sessionStore = KoinPlatform.getKoin().get<SessionStore>(),
      )
      ```

### C. iOS 実装

11. **`iosApp/iosApp/Features/Home/HomeViewModel.swift`**
    - `HomeUiState` enum の `loaded` ケースに `recentTransactions: RecentTransactionsState` を追加。
      Swift 側に `enum RecentTransactionsState { case loading; case ready([RecentTransactionItem]); case error(String) }` を新設。
    - `private let userRepository: UserRepository` / `private let sessionStore: SessionStore` をプロパティに追加し、`init` で `KoinIosKt.userRepository()` / `KoinIosKt.sessionStore()` を取得。
    - `inFlight: Job?` を `inFlightProfile` / `inFlightRecent` の 2 本に分離。
    - `load()` 内で `ProfileFlowIosKt.fetchMyProfile` と `TransactionsFlowIosKt.fetchRecentTransactions(limit: 3)` を **両方同時に kick** し、それぞれ独立にコールバックで state を更新する（Android の async と等価な並列実行）。
    - profile の `.error` 遷移は従来通り全画面エラー。
    - transactions の失敗 / Unauthenticated は `recentTransactions = .error(...)` のみセットし、他は弄らない。
    - `refreshRecent()` を追加。
    - 取引 → `RecentTransactionItem` の組み立ては iOS 側 `TransactionRow.swift` の `TransactionRowVariant` と同じロジックで行う（共通化のため、軽いヘルパー関数 `RecentTransactionItem.fromShared(_:)` を `RecentTransactionItem.swift` 付近に追加してもよい）。

12. **`iosApp/iosApp/Features/Home/HomeView.swift`**
    - `HomeView.mockRecentTransactions` を削除。
    - `loadedContent(profile:)` を `loadedContent(profile:recent:)` に拡張し、`recent` の状態に応じて
      `RecentTransactionsSection`（ready）/ `ProgressView`（loading）/ エラープレースホルダ + 再試行ボタン（error）を出し分ける。
    - エラー時のプレースホルダは Figma の RecentTransactions セクションのカード枠を踏襲し、テキスト + `Button("再試行") { viewModel.refreshRecent() }` を中央寄せで表示。

13. **`iosApp/iosApp/Features/Home/Components/RecentTransactionsSection.swift`**
    - Android 側 (#6) と同様、`RecentTransactionItem` に
      `amountColorIsPink: Bool`（あるいは `direction: TransactionDirection`）を追加し、
      `RecentTransactionCard` の金額色を Outgoing 時に `textPrimary` に切替える。

14. **`iosApp/iosApp/Features/Transactions/TransactionDisplay.swift`**
    - `subtitlePlaceholder` を削除。
    - 代替として、Android (#7) と同じく `artifactId` の有無で Mint を分岐させる:
      ```swift
      static func rowSubtitle(transaction: Transaction) -> String {
          switch transaction.direction {
          case TransactionDirection.mint:
              return transaction.artifactId == nil ? "fuju からの受け取り" : "発行されたアーティファクト"
          case TransactionDirection.incoming:
              return "受け取り"
          default:
              return "送金"
          }
      }
      ```
    - 文言は Android (#7) と完全一致させる。

15. **`iosApp/iosApp/Features/Transactions/TransactionRow.swift`**
    - `TransactionDisplay.subtitlePlaceholder` を `TransactionDisplay.rowSubtitle(transaction: transaction)` に置換。

16. **`iosApp/iosApp/Features/Transactions/TransactionDetailView.swift`**
    - `DetailTransactionRow` 内の `TransactionDisplay.subtitlePlaceholder` 参照を同様に置換。

## 検証

- [ ] `./gradlew build` が通る
- [ ] `./gradlew :composeApp:assembleDebug` が通る
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が通る
- [ ] `./gradlew :shared:allTests` が通る
- [ ] Android 実機 / エミュレータでログイン後にホームを開き、最近の取引 3 件が API から取得・表示されること（debug ビルドではダミー 25 件のうち最新 3 件、release ビルドでは本番 API のレスポンス最新 3 件）。サブタイトルが「受け取り / 送金 / 発行されたアーティファクト / fuju からの受け取り」のいずれかになっていること。一覧 / 詳細のサブタイトルも同様に kind 別文言になっていること。
- [ ] iOS シミュレータでログイン後にホームを開き、上記と同じ振る舞いを確認。一覧 / 詳細でも文言を確認。
- [ ] 取引取得失敗時（機内モード等で再現）にホームの残高は表示されたまま、Recent セクションだけ「読み込めませんでした」+ 再試行ボタンが出ること（Android / iOS 双方）。
- [ ] profile 取得失敗時は従来通り全画面エラー（既存挙動の回帰がないこと）。
- [ ] **PR #80 経路の E2E**: fuju-emotion-model から service token 経由で代理 mint を実行し
  (`mint:creator_payouts` scope + `user_id = 受取人 ULID`)、対象ユーザーで Bank クライアントに
  ログインしてホームを開いたとき、その mint が「最近の取引」最新 3 件に含まれ、
  サブタイトルが「fuju からの受け取り」になっていること。`artifact_id = NULL` のため
  タイトルが「発行」（artifact 短縮 ID なし）で表示されることを併せて確認。
- [ ] 前提タスク `transaction-listing-dto-alignment.md` が merge 済みで、
  release ビルドの取引履歴一覧画面が実 API から正しく取引を取得・表示できていること
  （本タスクはその基盤の上にホーム配線を載せるだけ）。

## 技術的な補足

### 並列 fetch の設計

- Android: `viewModelScope.launch { coroutineScope { val p = async { ... }; val t = async { ... }; p.await() / t.await() } }` を使うのが KMP/Compose の慣用。`coroutineScope` を使うと片方が例外を投げてももう片方を待ってから集約できるが、ここでは `runCatching` で個別に握り潰し、Recent 側の失敗を `RecentTransactionsState.Error` に格納する設計とする。
- iOS: `inFlightProfile` / `inFlightRecent` の 2 本の `Job` を独立に保持し、`refresh()` / `refreshRecent()` でそれぞれをキャンセル + 再起動する。`@MainActor` 上でのみ state を書き換える点は既存と同じ。

### `RecentTransactionItem` の取り回し

- 現状 `RecentTransactionItem` には `direction` / `subtitle` がない。今回の変更では sign / 金額色を direction で出し分けたいため、`direction: TransactionDirection`（kotlinx 列挙）を 1 個増やす方針が最小差分で素直。
- iOS 側の `RecentTransactionItem`（Swift struct）は `Identifiable` を維持したまま `direction: TransactionDirection` を保持できるよう、`Shared.TransactionDirection` を import する。

### Out of Scope（後続タスクで対応）

1. **視線秒数（gazedSeconds）の API 拡張** — `TransactionDto` に滞留秒数 / 視線強度を載せ、サブタイトル「○秒みつめられた」と取引詳細の感情データ (`18 秒` / `0.94`) を実値に差し替える。
2. **名前解決 API** — `userId.takeLast(6)` の短縮 ID 表示を、相手ユーザー名 / アーティファクト名表示に置き換える。
3. **`UserRepository.dummyTransactions()` の整理** — debug ビルドで返している 25 件ハードコードを `BuildKonfig.USE_DUMMY_PROFILE` の用途縮小と合わせて整理する。
4. **Android からも `shared.fetchRecentTransactions` を再利用する形に統合** — 現状 Android `HomeViewModel.fetchRecentTransactions()` と `shared/iosMain/.../TransactionsFlowIos.fetchRecentTransactions` で session→userId 解決 / sortedByDescending().take() / Failure メッセージがほぼ二重実装になっている。`commonMain` に `suspend fun fetchRecentTransactions(limit): TransactionsLoadOutcome` を抽出し、Android VM / iOS facade の両方からそれを呼ぶ形に DRY 化する。Recent タイトル組み立てロジック (`Transaction.toRecentItem` / `TransactionRowVariant.from`) の共通化も同時に検討。

## 未決事項

- サブタイトル文言の最終決定。提案中:
  - Mint (artifact_id 有 = Bank 内発行) → "発行されたアーティファクト"
  - Mint (artifact_id = NULL = fuju 代理 mint / クリエイター payout) → "fuju からの受け取り"
  - Incoming → "受け取り"
  - Outgoing → "送金"
  別案として「もらいました / 送りました」のような動詞調 / 「受け取り済み」のような状態調も可。
  fuju 代理 mint は「クリエイター報酬」「fuju 投稿の対価」も検討余地あり。レビュー時に確定する。
- `RecentTransactionItem` に `direction` を持たせるか、`amountColorIsPink: Bool` のような表示用フラグだけ持たせるか。前者が拡張性◯、後者が UI 層責務分離◯。実装時にコードを書きながら最終決定する。
