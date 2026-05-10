# client-bank-22: 送金機能の実装（受け取り専用 MVP からの方針転換）

## 概要

これまで MVP 範囲外として `ComingSoonScreen` プレースホルダで止めていた **送金機能** を実装する。フッターナビゲーションに「送金」タブを **「ホーム」と「アカウント」の間** に追加し、**送金先選択（表示名で絞り込み + 確認 bottom sheet モーダル）→ 金額入力（プレビュー + 確認 AlertDialog）→ 実行** という **2 ステップ画面 + 2 段の確認モーダル** 構成を Android（Compose） / iOS（SwiftUI）の両方に新設する。送金 API（`POST /ledger/transfer`） / DTO / `LedgerRepository` / `Idempotency-Key` 採番 はすでに `shared/` に揃っているため、本タスクは **UI とナビ動線の追加が主、shared 側は宛先ユーザー解決のための薄い表示名検索 API 追加のみ** となる。

合わせて、CLAUDE.md と memory の「MVP は受け取り専用」記述を更新し、送金が MVP スコープに入ったことを明示する。

## 背景・MVP 方針変更

- これまでの方針（2026-05-06 確定）: MVP は「お金を受け取れる」だけ。送金 / HUD / Artifact 投稿はすべて後回し。
  根拠は `CLAUDE.md` の「MVP は受け取り専用」セクションと、user memory `project_mvp_scope.md`。
- 本日（2026-05-10）方針変更: MVP に **送金** を含める。「Auth 側の表示名で送金先を決め、確認モーダルを挟んで実際に送る」というフローを実装する。
- そのため、本タスクの完了条件には CLAUDE.md / memory の更新も含める（後述「完了条件」参照）。

## 現状ギャップ

### shared 側（実装済み）

- `LedgerApi.transfer(fromUserId, toUserId, amount, memo, idempotencyKey)`（`shared/src/commonMain/.../data/remote/api/LedgerApi.kt`）が `POST /ledger/transfer` を叩いて `TransferResponse(transactionId, newBalance)` を返す。`Idempotency-Key` ヘッダと body 両方に同じ UUID を入れる規約も実装済み。
- `LedgerRepository.transfer(from, to, amount, memo, retryKey)`（`shared/src/commonMain/.../data/repository/LedgerRepository.kt`）が `TransferResult.Success / MfaRequired / Failure` の sealed class を返す。`MFA_REQUIRED` 時は `retryKey` をそのまま再利用できるよう保持する設計。
- DTO: `TransferRequest` / `TransferResponse`（`shared/src/commonMain/.../data/remote/dto/LedgerTransferDto.kt`）。
- DI: `ledgerModule` で `LedgerApi` / `LedgerRepository` を single 登録済み（`shared/src/commonMain/.../di/`）。
- エラーコード: `INSUFFICIENT_BALANCE` / `VALIDATION_FAILED` / `NOT_FOUND` / `MFA_REQUIRED` などはすべて `ApiErrorCode` enum に列挙済み。

### shared 側（未実装、本タスクで追加）

- **「表示名で宛先ユーザー候補を検索する API」が存在しない**。
  - `AuthCoreUserApi.getProfile()` は **自分自身** のプロフィールしか取れない（`/v1/user/profile`）。
  - bank `UserApi.get(userId)` は **bank 内部 ID（`Long`）を知っている前提** で叩く API のため、表示名 / public_id からの逆引きには使えない。
  - クライアントが `LedgerApi.transfer` に渡せるのは `to_user_id`（bank 内部の user 主キー）。よって「表示名 → bank user id 候補リスト」の解決手段がバックエンド側に必要。
  - バックエンド側計画書 `server-bank-22-recipient-resolution-api.md` で **表示名検索 API（`GET /users/search?q=...`）** として推奨方針を切り替え済み（後述 Open Questions 1）。

### composeApp（Android）側

- `composeApp/src/androidMain/.../features/shell/RootScaffold.kt` の `BottomNav` は **2 タブ均等配置**（ホーム / アカウント、weight=1, 内側 64dp の余白）。
- `RootDestination.Send`（`composeApp/src/androidMain/.../navigation/RootDestination.kt`）はすでに enum に存在し、`ComingSoonScreen("送る・もらう", onBack = ...)` を出すだけのプレースホルダになっている。
- `HomeScreen` の `onSendReceive` 引数は **未配線**（`@Suppress("UNUSED_PARAMETER")` でコメントアウト相当）。
- 送る系アイコン `composeApp/src/androidMain/res/drawable/ic_send.xml`（緑色 `#0CD80C`、Figma `89:12356`）は既に存在するが、フッターでは色違い（black / TextTertiary）で使う想定。

### iosApp（iOS）側

- `iosApp/iosApp/Features/Shell/RootTabView.swift` の `Destination` enum には **`Send` ケースが存在しない**（旧 fujupay の中央 FAB を撤去したコメントあり）。Android との非対称が現状すでにある。
- `BottomBar` は Android と同じく 2 タブ均等配置 + `safeAreaInset` 構成。
- `HomeView.swift` の `onSendReceive` も Android と同じく未配線で残っているだけ。

## 採用するフロー全体図（2026-05-10 確定）

```
[フッター] ホーム | 送金（新規） | アカウント
                       |
                       |  タブタップ
                       v
[Step 1] 送金先選択画面（SendRecipient）
         ・上部に検索フィールド「表示名で送金先を検索」
         ・入力（最低 2 文字）→ GET /users/search?q={表示名} で部分一致候補を取得
         ・候補リスト: 「丸アバター + 表示名 + public_id 末尾4桁」を縦並び
         ・自分自身は結果から除外
         ・候補タップ → 下から bottom sheet モーダル「この人で OK ですか？」
            ┌────────────────────────────┐
            │       ◯（大きい丸アバター）       │
            │         表示名               │
            │   public_id 末尾 4 桁（小）      │
            │                            │
            │ [ ピンクの「決定」CTA ]            │
            │ [   キャンセル（テキスト）  ]        │
            └────────────────────────────┘
            （Figma 709-8106 / 709-7561 のフレンド追加モーダルと同じパターン）
         |
         |  決定 CTA で Step 2 へ
         v
[Step 2] 金額入力 + プレビュー画面（SendAmount）
         （Figma node-437-22416 のチャージ画面テンプレートを踏襲）
         ・上部: 戻るボタン / タイトル「送金」/ 通知ベルアイコン
         ・宛先表示（小サイズの丸アバター + 表示名）を上部に配置
         ・大きな金額表示「金額  ◯◯◯,◯◯◯ふじゅ〜」（編集可、カンマ区切り）
         ・残高超過時は警告文 + CTA 非活性
         ・「送金後の残高: ◯◯ふじゅ〜」プレビューカード（角丸、薄ピンク背景 #FFEAF6 系）
         ・ピンクの大 CTA「送金する」（#FF20B0 系）
         ・下部: iOS は system 数字キーボード、Android はカスタム数字パッド
         ・**クイック金額チップ（+1,000 / +2,000 等）は採用しない**
         |
         |  「送金する」CTA タップ → 確認 AlertDialog
         v
[確認モーダル（AlertDialog）]
         「○○さんに 12,020ふじゅ〜 送りますか？」
         [送金する] [キャンセル]
         |
         |  「送金する」タップ → LedgerRepository.transfer(...)
         v
[実行成功]
         ・完了は別 destination（SendComplete: transactionId 末尾 + 新残高 + 「ホームへ戻る」）
           or Snackbar「送金しました」+ ホーム自動遷移、のどちらかを実装時の自然さで採用
         ・どちらの場合も HomeViewModel.refresh() を kick

[途中で出うる失敗パス]
  - INSUFFICIENT_BALANCE → Step 2 にとどまり、インライン error 文言
  - VALIDATION_FAILED / NOT_FOUND（送金直前に宛先が消えた等）→ Step 1 に戻し、エラー文言を表示
  - MFA_REQUIRED → 既存 MfaVerify 系の経路を流用、retryKey 維持で再 transfer
  - NetworkFailure（オフライン等） → Step 2 でインライン error + 再試行 CTA。Idempotency-Key 保持で安全に再送
```

## バックエンド前提

`POST /ledger/transfer` 自体は実装済みで、サーバ側で送信元・宛先の残高を 1 トランザクションで更新する設計（`ledger_transactions` への INSERT + `users.balance_fuju` の UPDATE をトランザクション内で実行）になっている前提なので、本タスクの「相互書き換え」要件はバックエンド側で既に満たされている。

新規に必要なバックエンド変更は **「表示名で送金先候補を検索する API」のみ** で、これはバックエンド計画書 `server-bank-22-recipient-resolution-api.md` で個別タスク化済み。

- API: `GET /users/search?q={表示名}` → `[{ id, public_id, name, icon_url }, ...]`
- 認証必須（AuthCore JWT）
- レート制限強化（per-user 30/min、per-IP 60/min 程度）
- クエリ最低 2 文字
- 自分自身は API 側 or UI 側で除外（バックエンド計画書の Open Questions で議論中）
- email / balance / mfa_enabled 等の機密は返さない

なお、以下 2 点はバックエンド側で別途確認が残る:

1. **`POST /ledger/transfer` のレスポンスに「宛先の表示名」を含めるか**
   - 含めない場合は確認画面で取得した宛先情報をクライアント側で持ち回る（こちらで完結可能）。
2. **`POST /ledger/transfer` の `from_user_id` をクライアントが指定する現在の API 設計**
   - 現状の `TransferRequest.from_user_id` は呼び出し側が指定する形だが、サーバ側は access_token の sub から派生させて検証する方が安全。仕様変更があれば本タスクで吸収する。

## Figma 参照画面の対応付け（確認済み）

ユーザーから提示された Figma URL 4 枚 + 追加 1 枚を取得して内容を確認した結果、本タスクの 2 ステップ + モーダル設計に **モーダル UX として再評価** したものと、**Step 2 のレイアウトテンプレ** として採用するものがある。

スクリーンショットは `docs/figma-assets/money-transfer/` 配下に保存済み。

| URL の node-id | 実画面 | 本タスクでの扱い |
|---|---|---|
| `410-18923` | トーク一覧（送る・もらう）。検索バー + 友達リスト | **本タスク非採用**。将来「トーク UI 化」タスクで参照 |
| `709-8106` | フレンド検索結果 + 「友達追加する」モーダル（下からスライド） | **採用（モーダルパターン）**: Step 1 の「この人で OK?」bottom sheet モーダルのテンプレ |
| `709-7561` | フレンド追加確認モーダル（アバター + CTA 単体） | **採用（モーダルパターン）**: 同上、確認モーダル内のレイアウト（大アバター + 表示名 + ピンク CTA + キャンセル）テンプレ |
| `456-22599` | トークルーム。チャット吹き出しに「送る/受け取る ◯◯ふじゅ〜」が表示 | **本タスク非採用**。将来「トーク UI 化」タスクで参照 |
| `437-22416` | チャージ画面（金額入力 + プレビュー + ピンク CTA） | **採用（Step 2 レイアウトテンプレ）**: `https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=437-22416` 。スクリーンショット保存先 `docs/figma-assets/money-transfer/node-437-22416.png` |

**ユーザー判断（2026-05-10）**: トーク UI 設計は **採用せず**、本タスクは「2 ステップ画面 + bottom sheet 確認モーダル + AlertDialog 最終確認」のシンプルフォーム遷移で実装する。Figma 4 枚のうちフレンド追加モーダル 2 枚（709-8106 / 709-7561）はモーダルパターンとして再評価して採用、その他はトーク UI 化タスクで再参照する。

## 「実際のアプリに近い感じで」の UI ガイドライン

ユーザーは「実装時は実際のアプリに近い感じで」と指示。これは **Figma の旧 fujupay 系画面テーマを踏襲してほしい** という意味で、以下を統一ルールとする:

- **カラー**: ピンク（`#FF20B0` 系）を CTA とアクセントに使用。プレビューカード背景は薄ピンク（`#FFEAF6` 系）。
- **アバター**: 円形クロップ。Step 1 の検索結果リストでは小サイズ（40dp 程度）、bottom sheet モーダル内では大サイズ（80〜96dp 程度）。
- **「この人で OK?」確認**: **bottom sheet モーダル**（既存 Figma `709-8106` / `709-7561` のフレンド追加モーダルと同じパターン）。下からスライドアップ、アバター + 表示名 + ピンク「決定」CTA + テキスト「キャンセル」。
- **「○○さんに 〇〇ふじゅ〜 送りますか？」確認**: **AlertDialog** 系で OK（中央モーダル、シンプルな yes/no）。
- **金額表記**: 「12,020ふじゅ〜」（カンマ区切り + 「ふじゅ〜」サフィックス、半角カンマ + 全角チルダ）。Step 2 の大きな金額表示・確認モーダル・完了画面で統一。
- **タイポ**: 既存 `FujuBankColors` / `NotoSansJP` を踏襲。Step 2 の金額表示は既存ホームの残高表示と同じ size scale を使用。
- **iOS / Android 一致**: フォントとカラーは両 OS で同じトークンを使う（iOS 側は `Color(hex:)` 拡張で同等の hex を指定）。

## スコープ

### 含む

- **shared**:
  - 表示名検索 API のラッパ追加（`UserSearchApi` 新設、または `UserApi.searchByDisplayName(query)` 拡張）
  - 検索用 DTO（`UserSearchResultDto(id, publicId, name, iconUrl)` の配列レスポンス）
  - `UserRepository.searchByDisplayName(query): NetworkResult<List<UserSearchResult>>` 追加（自分自身を弾くロジックも Repository 側で吸収可能）
  - 必要なら `userModule` への DI 追記
- **composeApp（Android）**:
  - `RootDestination` に送金フローのサブ画面を追加。**画面は 2 つ**（`SendRecipient` / `SendAmount`）、**完了は別 destination（`SendComplete`）または Snackbar 経由でホーム遷移** のいずれか
  - Step 1 の「この人で OK?」確認は **`ModalBottomSheet`**、Step 2 の最終確認は **`AlertDialog`** で実装（独立した destination は作らない）
  - `RootScaffold.kt` の `BottomNav` を 3 タブ化（ホーム / 送金 / アカウント、weight 配分の見直し）
  - `features/send/` パッケージを新設し、2 画面の Composable + ViewModel を実装
  - 送金フロー中は MFA 要求時に `MfaVerifyScreen` 相当を表示する経路を組む（既存 `MfaVerifyViewModel` の再利用または送金専用に新規）
- **iosApp（iOS）**:
  - `RootTabView` の `Destination` に `.send` ケース追加 + 3 タブ化
  - `Features/Send/` 配下に SwiftUI の 2 画面 + Observable ViewModel
  - 「この人で OK?」確認は `.sheet` + `.presentationDetents([.medium])` で bottom sheet 表現、最終確認は `.alert`
  - MFA 要求時の経路は同上
- **アセット**:
  - フッター「送金」アイコン（既存 `ic_send.xml` を流用 or Figma から書き出し直し）。iOS 用 SVG / PNG も準備
  - Figma 画面ごとの screenshot を `docs/figma-assets/money-transfer/` に保存（`node-437-22416.png` は本タスクで新規追加）
- **ドキュメント更新**:
  - `CLAUDE.md` の「UX / プロダクトルール」内「MVP は受け取り専用」記述を更新（送金が MVP に入ったことを明記、OTP 自動 submit 禁止 / 既存実装画面リストはそのまま残す）
  - User memory `project_mvp_scope.md` を更新（送金が MVP スコープになった旨と日付）

### 含まない

- バックエンド側の送金 API 実装（`POST /ledger/transfer` 自体は既に存在する前提。**ユーザー検索 API の新設はバックエンドタスク** として `server-bank-22` で別管理）
- 送金履歴の専用画面（既存の取引履歴で `direction = Outgoing` として表示される。新規一覧画面は作らない）
- リアルタイム HUD / Artifact 投稿 / マイニング連携（MVP 範囲外のまま）
- iOS / Android のネイティブ通知での送金完了プッシュ（既存 ActionCable 経路のうえで動くなら自然と表示されるが、本タスクで追加実装はしない）
- Compose Multiplatform 化（現状の Android-only Compose / iOS-only SwiftUI の二重実装方針は踏襲）
- クイック金額チップ（+1,000 / +2,000 等）の追加 UI（不採用）

## 影響範囲

- モジュール: `composeApp` / `iosApp` / `shared`
- ソースセット:
  - `shared/src/commonMain` （UserSearchApi 新設・UserRepository 拡張・search DTO・必要なら DI モジュール追記）
  - `composeApp/src/androidMain`（送金 2 画面 + bottom sheet + AlertDialog + ナビ + RootScaffold）
  - `iosApp/iosApp/Features/Send/`（送金 2 画面 + sheet + alert + RootTabView 拡張）
- 破壊的変更:
  - **API 互換**: `RootDestination.Send` を保持したまま新規 destination（`SendRecipient` / `SendAmount` / 必要なら `SendComplete`）を追加するため後方互換は保たれる
  - **UI 互換**: フッターが 2 タブ → 3 タブに変わるため、UI スナップショットや手動テストの基準は更新が必要
- 追加依存: なし（既存 Ktor / Compose Material3 の `ModalBottomSheet` / SwiftUI の `.sheet` で完結）

## 実装ステップ

### Phase 1: バックエンド前提と Figma の確認（着手前）

1. バックエンド `server-bank-22` の表示名検索 API 仕様（部分一致 / 上限 / 自分除外の層）を確定し、DTO スキーマをクライアント側 DTO と整合させる
2. Figma `node-437-22416`（チャージ画面）の screenshot を `docs/figma-assets/money-transfer/node-437-22416.png` に書き出す
3. CLAUDE.md / memory 更新の文言ドラフトを作成しユーザーレビューを受ける

### Phase 2: shared 拡張

1. `data/remote/dto/UserSearchDto.kt` を新設（`UserSearchResultDto(id, publicId, name, iconUrl)` の配列レスポンス。`UserSearchResponse(users: List<UserSearchResultDto>)` でラップするかは backend DTO に合わせる）
2. `data/remote/api/UserSearchApi.kt` を新設し、`searchByDisplayName(query): NetworkResult<List<UserSearchResultDto>>` を `runCatchingNetwork { client.get(...) }` で実装
3. `data/repository/UserRepository.kt` に `searchByDisplayName(query): NetworkResult<List<UserSearchResult>>` を追加。自分自身（`SessionStore.current.userId`）を結果から除外するロジックを Repository 側で吸収
4. クエリ最低 2 文字バリデーションは ViewModel 側で持つ（Repository は通過させる）
5. `commonTest` に `UserSearchApi` / `UserRepository.searchByDisplayName` のユニットテストを追加（`MockEngine` で 200（複数件）/ 200（0 件）/ 401 / 429 / 自分のみのケース）

### Phase 3: 送金 ViewModel 設計（Android / iOS 共通の状態モデル）

1. `composeApp/.../features/send/SendFlowState.kt`（または `shared` 側に置けるなら `shared`）に sealed class で各ステップの状態を定義
   - `RecipientSearch(query, loading, results: List<UserSearchResult>, error)`
   - `RecipientConfirming(candidate: UserSearchResult)` ← bottom sheet 表示中
   - `AmountEntry(recipient, balance, value, error)`
   - `AmountConfirming(recipient, amount)` ← AlertDialog 表示中
   - `Submitting(recipient, amount, retryKey)`
   - `Complete(transactionId, newBalance)`
   - `Failed(error, retryKey)`
2. ViewModel: `SendFlowViewModel`（Android） / `ObservableSendFlowViewModel`（iOS）。Repository 呼び出しは共通で `LedgerRepository.transfer(retryKey = state.retryKey)`
3. 表示名検索のデバウンス（300ms 程度）を ViewModel 側で実装

### Phase 4: Android UI

1. `RootDestination` に `SendRecipient` / `SendAmount`（必要なら `SendComplete`）を追加。`RootDestinationSaver` の save / restore に対応
2. `RootScaffold.kt`:
   - `BottomNav` を 3 タブ化（ホーム / 送金 / アカウント）。send family の selected 判定を追加
   - `when (destination)` に新規 destination を追加し、それぞれ `SendRecipientScreen` / `SendAmountScreen` を呼ぶ
   - `showBottomBar` ロジックを更新（送金フロー中はフッター継続表示。Step 2 の最終確認 AlertDialog 中・送金中はフッター継続で OK）
3. `features/send/SendRecipientScreen.kt`:
   - 上部に検索 `OutlinedTextField`（プレースホルダ「表示名で送金先を検索」）
   - 候補リストを `LazyColumn` で表示（`AsyncImage`（coil）で丸アバター + 表示名 + public_id 末尾4桁）
   - 候補タップで `ModalBottomSheet` を表示（`rememberModalBottomSheetState`）
   - bottom sheet 内: 大アバター + 表示名 + public_id 末尾4桁 + ピンク「決定」`Button` + テキスト「キャンセル」`TextButton`
   - 「決定」で SendAmount に navigate
4. `features/send/SendAmountScreen.kt`:
   - 上部 TopAppBar: 戻る / タイトル「送金」/ 通知ベル
   - 宛先表示 Row（小アバター + 表示名）
   - 金額表示 `Text`（巨大 size、カンマ区切り、「ふじゅ〜」suffix）
   - 残高超過時の警告 `Text` + CTA 非活性
   - 「送金後の残高」プレビュー `Card`（角丸、薄ピンク背景）
   - ピンク CTA `Button`「送金する」
   - 下部にカスタム数字パッド（`Row` × 4 で 0-9 + 削除）
   - CTA タップで `AlertDialog`「○○さんに ◯◯ふじゅ〜 送りますか？」を表示
   - AlertDialog の confirmButton で `LedgerRepository.transfer` を呼ぶ
5. 完了表現: `SendCompleteScreen` を作るか Snackbar + ホーム遷移にするかは実装時に判断（どちらでも OK、自然な方を採用）
6. MFA 要求時の経路: 送金フロー内に専用の MFA 入力画面を挟むか、既存 `MfaVerifyScreen` を再利用するかを決める（既存 `MfaVerifyScreen` はログイン用に強く結合しているため、本タスクでは送金フロー専用に簡易版を作るのが安全）

### Phase 5: iOS UI

1. `RootTabView.swift` の `Destination` enum に `.send`（および sub destination）を追加。`isSendFamily` 計算プロパティを追加
2. `bottomBar` を 3 タブ化（同上）
3. `Features/Send/SendRecipientView.swift`:
   - 検索 `TextField` + 候補 `List`
   - 候補タップで `.sheet(isPresented:)` + `.presentationDetents([.medium])` で bottom sheet 表現
   - sheet 内に大アバター + 表示名 + ピンク「決定」`Button` + テキスト「キャンセル」`Button`
4. `Features/Send/SendAmountView.swift`:
   - `NavigationStack` 内に大金額表示 + プレビューカード + ピンク CTA
   - 数字入力は `keyboardType(.numberPad)` を使う（iOS は system キーボードで OK）
   - CTA タップで `.alert(...)` を表示し、確認で `LedgerRepository.transfer` を呼ぶ
5. MFA 要求時の経路は Android と揃える

### Phase 6: ドキュメント / メモリ更新

1. `CLAUDE.md` の「MVP は受け取り専用」記述を「MVP は受け取り + 送金まで」と書き換え。OTP 自動 submit 禁止 / 既存画面リストは保持
2. user memory `project_mvp_scope.md` を更新（送金が MVP に入った日付・経緯）
3. `README.md` の「現況（実装済み範囲）」の `送金フォーム / HUD / Artifact 投稿（MVP 範囲外）` 行を更新

### Phase 7: 検証 / 実機確認

1. Android: 自分以外のテストアカウント宛に少額送金 → 残高反映を確認
2. iOS: 同上
3. 残高不足 / 自分宛（検索結果から除外されること）/ 候補 0 件 / 1 文字検索（CTA 非活性）/ オフライン それぞれのエラー UI を確認
4. MFA 必須テストアカウントで MFA 経路を確認
5. 確認 bottom sheet の「キャンセル」で Step 1 に戻る動作を確認
6. AlertDialog の「キャンセル」で Step 2 にとどまる動作を確認

## バリデーション / エラー方針

| ケース | 検出層 | UI 挙動 |
|---|---|---|
| 検索クエリが空 | クライアント | 候補リスト非表示、検索リクエストを発行しない |
| 検索クエリが 1 文字 | クライアント | 「2 文字以上で検索してください」のヒント表示、検索リクエストを発行しない |
| 検索結果 0 件 | サーバ → 200 with `[]` | 「該当ユーザーが見つかりません」の文言を candidate list 領域に表示 |
| 表示名重複（複数ヒット） | サーバ | 候補リストに **全件並べる**。各行に「アバター + 表示名 + public_id 末尾4桁」で識別 |
| 候補タップ後の bottom sheet で「キャンセル」 | クライアント | bottom sheet を閉じて Step 1 にとどまる、検索結果は維持 |
| 宛先が自分自身 | クライアント / サーバ | 検索結果から除外（一次は API 側、二次は Repository 側でガード） |
| 金額が 0 / 負 / 非整数 | クライアント | CTA 非活性 |
| 金額 > 残高 | クライアント側 + サーバ二重チェック（`INSUFFICIENT_BALANCE`） | Step 2 で警告文 + CTA 非活性 |
| 確認 AlertDialog で「キャンセル」 | クライアント | Dialog を閉じて Step 2 にとどまる |
| MFA 必須 | サーバ → `MFA_REQUIRED` | 送金フロー内 MFA 画面を挟み、`retryKey` を維持したまま再 transfer |
| ネットワーク失敗 | クライアント | Step 2 でインライン error + 再試行 CTA。`retryKey` 保持で安全に再送 |
| サーバ 5xx / `AUTHCORE_UNAVAILABLE` | サーバ | リトライ誘導の文言 |
| 検索 API のレート制限超過（429） | サーバ | 「検索が混み合っています。しばらく待ってください」の文言、検索を一時停止 |

## 検証コマンド

CLAUDE.md の「検証コマンド」セクション準拠:

- [ ] `./gradlew build` が通る
- [ ] `./gradlew :shared:allTests` が通る（search API / Repository のテスト追加分含む）
- [ ] `./gradlew :composeApp:assembleDebug` が通る
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が通る
- [ ] Android 実機 / シミュレータで送金フロー全体（成功 / 残高不足 / 自分除外 / 候補 0 件 / 重複 / MFA / オフライン）を手動確認
- [ ] iOS シミュレータで同上を手動確認
- [ ] Xcode で `iosApp` をビルドして起動できる

## 完了条件

1. Android / iOS 両方でフッターに「送金」タブが表示され、ホーム ↔ 送金 ↔ アカウントの順で切り替えられる
2. 送金先選択（表示名検索 + 確認 bottom sheet モーダル）→ 金額入力（プレビュー）→ 確認 AlertDialog → 実行 → 完了 が両プラットフォームで動作し、実 API（`POST /ledger/transfer`）が叩かれて残高が反映される
3. 残高不足 / 自分除外 / 候補 0 件 / 重複 / MFA / オフライン の各エラーパスで、想定どおりの UI を表示する
4. `CLAUDE.md` と `project_mvp_scope.md` の「MVP は受け取り専用」記述が、送金を含む形に更新されている
5. 送金完了後にホームへ戻ると、残高表示と取引履歴に新しい Outgoing 取引が反映される（Pull-to-refresh 不要、自動 refresh で OK）
6. CI（`build-and-check` / `test-jvm` / `test-ios` / `build-ios-framework` / `build-ios-app`）がすべて green

## 技術的な補足

- **Idempotency-Key**: 確認 AlertDialog で「送金する」を押した時点で Repository が UUID を採番し、`MFA_REQUIRED` / `NetworkFailure` で再試行する場合は **同じ key を `retryKey =` に渡し直す**。これは既存実装で吸収済み（`LedgerRepository` の `retryKey` 引数）。
- **`from_user_id` の解決**: `SessionStore.current` が `Authenticated` のときに `userId`（bank 内部 ID）が入っているのでそれを使う。ダミーモード時の動作はとりあえず「送金 CTA を非活性」で OK（実 API を叩けないため）。
- **MFA 要求時の retry**: `LedgerRepository.transfer` が `TransferResult.MfaRequired(retryKey)` を返したら、ViewModel 側で MFA 画面を挟む → MFA 検証成功後に `LedgerRepository.transfer(..., retryKey = previousKey)` を再呼出。MFA 検証は AuthCore `/v1/auth/mfa/verify` ではなく、送金時の MFA は **bank API 側の introspection が `MFA_REQUIRED` を出す** ケース。フローは `server-bank-23-transfer-mfa-verify-flow.md` を参照。
- **検索のデバウンス**: `kotlinx.coroutines.flow.debounce(300)` を ViewModel 内 `MutableStateFlow<String>` に挟み、過剰リクエストとレート制限抵触を抑止。
- **ModalBottomSheet（Android）**: Material3 の `ModalBottomSheet` は `sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)` で full / dismiss のみの挙動にする。
- **`.sheet` + `.presentationDetents([.medium])`（iOS）**: iOS 16+ で動く。プロジェクトの最低 iOS バージョンを確認。
- **Compose Multiplatform 化を今回もしない理由**: 既存 UI が Android-only Compose / iOS-only SwiftUI の二重実装になっており、本タスクだけ commonMain 化すると整合が崩れる。共通化は別タスクで一括変換するのが安全。
- **`HomeViewModel.refresh()` の発火**: 送金完了画面（または Snackbar 表示後）から「ホームへ戻る」を押した瞬間に `refresh()` を呼ぶ経路を作るのが望ましい。Android では `RootScaffold` で destination 切替時にフックを挟むか、`HomeViewModel` を Koin singleton 化して送金完了 ViewModel から `refresh()` を呼ぶ。iOS では `HomeViewModel.onAppear()` で再取得しているので自然と更新される（要確認）。

## Open Questions（実装着手前にユーザーに確認）

1. **宛先ユーザー解決 API**: ✅ 方針確定（2026-05-10）。**候補 3（表示名部分一致検索）** を採用。
   → バックエンド側計画書: `docs/tasks/server-bank-22-recipient-resolution-api.md`（fuju-bank-backend リポジトリ）
   - **注記**: 以前は候補 1（`public_id` 完全一致）を推奨していたが、ユーザー方針確定により表示名検索（候補 3）へ転換。詳細・DTO・認証要件・レート制限・プライバシー設計・RSpec 方針は当該ドキュメント参照。
2. **送金フローでフッターを表示し続けるか**: 送金フロー全体で **常時表示** とする（誤タップでホーム / アカウントに飛ぶ可能性はあるが、戻ってきた時に状態を保持できるよう ViewModel を destination 跨ぎで保持する設計にするか、別 navigation graph に切り出すかは実装時判断）
3. **Figma URL の対応付け**: ✅ 解消済み。トーク UI 系 2 枚は非採用、フレンド追加モーダル 2 枚（709-8106 / 709-7561）はモーダルパターンとして再評価して採用、チャージ画面（437-22416）は Step 2 のレイアウトテンプレとして採用（上記「Figma 参照画面の対応付け（確認済み）」参照）
4. **送金時の MFA 経路**: `POST /ledger/transfer` が `MFA_REQUIRED` を返した場合、どの API で MFA を verify するのか
   → バックエンド側計画書を起票済み: `docs/tasks/server-bank-23-transfer-mfa-verify-flow.md`
   （推奨方針 = 候補 A: AuthCore に `POST /v1/auth/mfa/step-up` を新設し、検証済み access_token で transfer を再送。詳細は当該ドキュメント参照）
5. **表示名重複時の UX**: ✅ 解消済み。検索 API は重複していても全件返す仕様で、UX 側は **候補リスト + 確認 bottom sheet モーダル + public_id 末尾4桁表示** の組み合わせで識別を担保する（前述「採用するフロー全体図」「バリデーション」参照）。
6. **「送金」タブのアイコン色とラベル文字**: 既存緑 `#0CD80C` を使うか、ホーム / アカウントと同じ TextTertiary 系で統一するか（推奨は後者だがユーザー判断）
7. **完了表現**: 専用の完了画面（`SendCompleteScreen`）にするか、Snackbar + ホーム自動遷移にするかは実装時に自然な方を採用
