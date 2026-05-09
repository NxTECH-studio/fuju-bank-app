# ドキュメント全面棚卸しと刷新（README.md / CLAUDE.md / docs）

## 概要

ルート `README.md` / ルート `CLAUDE.md`（新規）/ サブディレクトリの `CLAUDE.md`（必要に応じて）/ `docs/` 配下の運用資料を一気に棚卸しし、5/9 時点の実装実態に揃える。

## 背景・目的

直近で `TokenExpiryWatcher`（PR #89, 5/9）、`SessionResetCoordinator`（PR #87, 5/9）、CI の `test-jvm` / `test-ios` job 分離（PR #88, 5/9）が入ったが、ドキュメント側に反映されていない。`README.md` の最終更新は 5/7 の「本番 AuthCore / bank 接続前提に全面更新」でその後の3 PR が抜けている。また、ルート `CLAUDE.md` は **そもそも存在しない**（Claude Code がルート規約として参照する想定の場所が空）。`.claude/README.md` には `develop → main の2段を想定` というソロ開発実態（main only、`project_solo_dev.md`）と乖離した記述も残る。実装が前進する一方でドキュメントが追従できていないギャップを今回でゼロに戻す。

## 影響範囲

- モジュール: ドキュメントのみ（`composeApp/` / `shared/` / `iosApp/` のコードは触らない）
- ソースセット: 該当なし
- 破壊的変更: なし
- 追加依存: なし

### 対象ファイル一覧

| ファイル | 現状 | 今回の扱い |
|---|---|---|
| `/README.md` | 5/7 更新（531 行）。MFA / refresh は反映済みだが、TokenExpiryWatcher / SessionResetCoordinator / CI test job 分離が未反映。「現況（実装済み範囲）」表が 2 PR 分古い | 追記更新 |
| `/CLAUDE.md` | **存在しない** | 新規作成（Claude Code 用のルート規約。詳細仕様は README に委譲し、AI 向け運用ルールに集中） |
| `/composeApp/CLAUDE.md` | 存在しない | 今回は作らない（ルート CLAUDE.md で十分カバーできるか様子見し、必要が出てから追加。理由: 既存資料が薄い段階で粒度を分散させると保守コストが先行する） |
| `/shared/CLAUDE.md` | 存在しない | 同上（作らない） |
| `/iosApp/CLAUDE.md` | 存在しない | 同上（作らない） |
| `/.claude/README.md` | `develop → main の2段` 記述あり、ソロ開発実態（main only）と乖離。コマンド一覧は最新 | 「ブランチ運用」節を main only 前提に書き換え |
| `/docs/task-plans/TBD-bank-rebranding-foundation*.md` | リブランディング期の旧計画（6 ファイル） | 今回は **触らない**（試行錯誤の履歴として保持: `feedback_keep_trial_error_history.md`） |
| `/docs/tasks/*.md` | 71 ファイル。直近 PR の計画もここに格納されている | 今回は触らない（個別タスク計画はそのまま蓄積する運用） |
| `/docs/figma-assets/` | Figma 書き出し置き場 | 触らない |

## 実装ステップ

### 1. README.md の追記更新（実装実態への追従）

以下の記述を追加・修正する。

1. **「現況（実装済み範囲）」表に 3 行追加**
   - `access_token 期限の proactive 監視`: 実装済み: `TokenExpiryWatcher.checkNow()` を Android `Lifecycle.ON_RESUME` / iOS `ScenePhase=.active` から呼ぶ。`expiresAt - threshold` 以内なら `AuthTokenRefresher.refresh()` を起動。`refresh` が API エラーを返したら `SessionStore` を `Unauthenticated` に戻す（ネットワーク失敗ではセッションを保持）。
   - `logout / refresh 失敗時のローカル state 一括破棄`: 実装済み: `SessionResetCoordinator` がアプリ起動時に start し、`SessionStore` の `Unauthenticated` 遷移を観測して `TokenStorage.clear()` / `AccountProfileProvider.reset()` などの local state クリアを連動。
   - `CI`: テストを `test-jvm` / `test-ios` の独立 job に分離（PR #88）。テスト失敗とビルド失敗を切り分けやすい構成。
2. **「アーキテクチャ」節の DI モジュール表に追記**（必要なら）
   - `sessionModule` の供給物に `SessionResetCoordinator` / `AccountProfileProvider` を追加（実コードを読んで qualifier の有無を確認した上で）。
3. **「認証フロー」節の「処理の流れ」**
   - 6.（ログアウト）の後ろ、または別項として `TokenExpiryWatcher` の存在と発火タイミング（resume）を 2-3 行で追記。`refreshTokens` の自動リフレッシュは「401 を契機」なのに対し、`TokenExpiryWatcher` は「resume 契機の事前チェック」という違いを明記する。
4. **「現況（実装済み範囲）」の文言調整**
   - 既存「MFA / リフレッシュトークン」の行に `proactive expiry watcher` への参照を一行足す（重複説明はしない）。
5. **既存テキストの誤りチェック**
   - 「予定」「未実装」と書かれている項目で、実は実装済みになっているものがないか走査（特に「設定」周り）。

### 2. ルート `CLAUDE.md` の新規作成

Claude Code がルート規約として読む CLAUDE.md を新設する。**README.md と内容を重複させない**（README は人間 / ポートフォリオ向け、CLAUDE.md は AI 向け運用規約に絞る）。

含める要素:

- **このリポジトリの一行説明**（1-2 行）: KMP + Compose Multiplatform で fuju-bank-backend のフロントを実装。詳細は `README.md` 参照、と明記。
- **AI コーディング時の必須ルール**:
  - `commonMain` に Android / iOS SDK を直接書かない（`expect` / `actual` で切る）
  - 依存追加は `gradle/libs.versions.toml` 経由（`libs.xxx` 参照）
  - 新規ファイル作成時の package: `studio.nxtech.fujubank` 配下
  - iOS への新規 `expect` 追加時は Darwin cinterop（`ExperimentalForeignApi`）の opt-in を忘れない
  - Auth が絡む変更は **`AUTHCORE_CLIENT_QUALIFIER` のクライアントと bank 用クライアントを混同しない**（再帰 deadlock 防止）
- **検証コマンド**（README から該当箇所を再掲ではなく、AI が必ず流すべきコマンドのみ抜粋）:
  - `./gradlew build`（全ターゲット）
  - `./gradlew :shared:allTests`（共通ロジック変更時）
  - `./gradlew :composeApp:assembleDebug`（UI 変更時）
  - `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`（iOS 影響時、macOS のみ）
- **コーディング規約の最低ライン**:
  - コミットメッセージは日本語（`feedback_commit_message_language.md` 由来）
  - main 直コミット禁止、`feature/<task-id>-<slug>` を切る（`feedback_start_with_plan_branch.md` 由来）
  - 試行錯誤の履歴は revert で潰さず PR/マージで残す（`feedback_keep_trial_error_history.md` 由来）
- **MVP スコープ宣言**（1 行）: 「受け取り専用 MVP」。送金 / HUD / Artifact 投稿は範囲外（`project_mvp_scope.md` 由来）。
- **OTP UX ルール**（1 行）: 6 桁完了 / IME Done での自動 submit は禁止（`feedback_otp_no_auto_submit.md` 由来）。
- **困ったら見るドキュメント**: `README.md`（実装仕様）/ `.claude/skills/SKILL.md`（KMP 罠）/ `docs/tasks/*.md`（過去の計画）。

**含めないもの**: 詳細な API 表、DTO 一覧、エラーコード対応表など → これらは README.md 側に既に書かれているため二重管理を避ける。

### 3. `.claude/README.md` の修正

- 「KMP 特化ポイント」の `ブランチ運用: develop → main の2段を想定するが、develop が無ければ main をベースにする` を **`main only 運用（ソロ開発）。feature/<task-id>-<slug> から PR を作って main にマージ`** に書き換え。
- 他の節（`/create-task → /start-with-plan → /code-review → /pr-create` の流れ等）は実態と一致しているのでそのまま。

### 4. 整合性確認

- `MEMORY.md` で参照されているメモリ（`feedback_*`, `project_*`）と CLAUDE.md の記述が齟齬していないか目視確認。
- `README.md` 中の「未実装 / 予定」記述が新しい CLAUDE.md と矛盾していないか確認。

## 検証

- [ ] `./gradlew build` が通る（ドキュメントのみだが、念のため）
- [ ] README.md の「現況（実装済み範囲）」表に TokenExpiryWatcher / SessionResetCoordinator / CI test job 分離の 3 行が追加されている
- [ ] ルート `CLAUDE.md` が存在し、上記の必須ルールセクションを含む
- [ ] `.claude/README.md` のブランチ運用節が main only 前提に修正されている
- [ ] README.md と CLAUDE.md の間で内容の重複（API 表の二重掲載など）がない
- [ ] 既存の実装ファイル（`TokenExpiryWatcher.kt` / `SessionResetCoordinator.kt` / `.github/workflows/*.yml` 等）を読んだ上で、ドキュメント記述が実装と一致している

## 技術的な補足

- **README と CLAUDE.md の分担方針**: README は「人間が読むプロジェクト紹介 + 実装仕様」、CLAUDE.md は「AI が必ず守るべき短い運用規約」。重複したら CLAUDE.md 側を縮める。
- **サブディレクトリ CLAUDE.md を今回作らない理由**: ルート 1 枚で十分カバーできる現規模。粒度を増やすと整合性メンテ（重複・矛盾検知）のコストが先に立つ。`composeApp/` 固有 / `shared/` 固有のルールが複数蓄積された段階で初めて分割を検討する。
- **`docs/task-plans/TBD-bank-rebranding-foundation*` を残す理由**: 試行錯誤の履歴を残すポリシー（`feedback_keep_trial_error_history.md`）に従う。削除しない。
- **コミット粒度**: README.md 更新 / CLAUDE.md 新規 / `.claude/README.md` 修正は別コミットに分ける（レビュー時に diff を読みやすくするため）。コミットメッセージは日本語で `docs(readme):` / `docs(claude-md):` / `docs(.claude):` の形。
- **ブランチ名**: `feature/docs-readme-and-claude-md-refresh`（タスクファイル名と揃える）。
