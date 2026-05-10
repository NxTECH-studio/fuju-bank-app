# CLAUDE.md

このファイルは Claude Code（およびその他の AI コーディング支援）向けの運用規約を集めたものです。プロジェクトの実装仕様・API 仕様・アーキテクチャ詳細は [`README.md`](./README.md) 側に書いてあるので、そちらと **重複させない**（API 表 / DTO / エラーコードは README 参照）。

## このリポジトリ

Kotlin Multiplatform（Kotlin 2.3.20）+ Compose Multiplatform で fuju-bank-backend のフロントを Android / iOS 両対応で実装するクライアント。3 層モジュール構成は `composeApp/`（UI）/ `shared/`（ドメイン・データ・抽象化）/ `iosApp/`（SwiftUI エントリ）。

## ソースセット境界（最重要）

- `commonMain` に Android / iOS SDK を **直接書かない**。プラットフォーム固有 API が必要なら `expect` 宣言を `commonMain` に置き、`androidMain` / `iosMain` で `actual` を実装する（既存例: `TokenStorageFactory`, `PersistentCookiesStorageFactory`, `createHttpClient`）。
- UI（Compose）は `composeApp/`、API クライアント・モデル・認証保管などのドメイン処理は `shared/` に寄せる。
- 新規 Kotlin ファイルの package は `studio.nxtech.fujubank` 配下。

## 依存追加ルール

- 新規依存は **`gradle/libs.versions.toml` に追記** し、`build.gradle.kts` から `libs.xxx` 経由で参照する。`build.gradle.kts` 内に直接 GAV 文字列を書かない。
- `settings.gradle.kts` で `TYPESAFE_PROJECT_ACCESSORS` が有効なので、プロジェクト参照は `projects.shared` 形式。

## 認証まわりの注意点

- `HttpClient` は **2 つの single** が共存する（bank API + AuthCore `/v1/user/profile` 用 / AuthCore `/v1/auth/*` 用）。AuthCore `/v1/auth/*` 用は `AUTHCORE_CLIENT_QUALIFIER` で取り出すこと。混同して bearer 入りクライアントで refresh を叩くと `refreshTokens` が自己再帰して deadlock する。
- `TokenStorage` は access_token のみ扱う。refresh_token は HttpOnly cookie のため `PersistentCookiesStorage` 側に流す。クライアントコードから refresh_token 文字列を読み書きしようとしてはいけない。
- 401 時の自動リフレッシュ（`refreshTokens`）と resume 時の事前リフレッシュ（`TokenExpiryWatcher.checkNow()`）は二系統あり、どちらも最終的に `AuthRepository.refresh()` を呼ぶ。`AuthRepository` 側で Mutex 直列化されているので、新たに refresh を呼ぶ経路を増やすときも追加 lock は不要。

## UX / プロダクトルール

- **MVP は受け取り + 送金まで**（2026-05-10 確定、当初の「受け取り専用」から方針転換）。HUD / Artifact 投稿は引き続き MVP 範囲外。これらの UI を「ついでに足す」提案はしない。
- **OTP 入力で自動 submit しない**。6 桁完了 / IME Done でのフォーム送信は禁止（誤入力リカバリを潰すため）。CTA タップで明示的に送信する。送金フロー中の MFA 入力でも同じ。
- 設定画面・アカウント情報編集・パスワード変更・通知許可・取引履歴・ホーム残高・送金フロー（送金先選択 → 金額入力 → 確認 → 実行）は実装済み。これらに変更を入れるときは Android / iOS 両方を必ず触る。
- 送金フロー中はフッター（ボトムナビ）を非表示にする。誤タップ防止と画面集中のため。

## 検証コマンド

実装後に必ず流すコマンド（変更範囲に応じて選ぶ）:

```bash
./gradlew build                                          # 全ターゲットのビルド確認（常に）
./gradlew :shared:allTests                               # shared に変更があるとき
./gradlew :composeApp:assembleDebug                      # composeApp に変更があるとき
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64    # iOS 影響あり（macOS のみ）
```

CI 側（`.github/workflows/ci.yml`）は `build-and-check` / `build-ios-framework` / `build-ios-app` / `test-jvm` / `test-ios` の 5 job 構成で、ビルド失敗とテスト失敗が分離して読める。Pull Request トリガー固定。

## ブランチ・コミット運用

- **main 直コミット禁止**。タスク開始時に `feature/<task-id>-<slug>` を main から切る（`/start-with-plan` の標準動作）。
- main only の単線運用（ソロ開発、`develop` ブランチは存在しない）。マージは PR 経由。
- **コミットメッセージは日本語**で書く。Conventional Commits の prefix（`feat:` / `fix:` / `docs:` / `refactor:` / `test:` / `ci:` 等）は英語のまま、件名・本文は日本語。
- **試行錯誤の履歴は残す**。失敗 → revert になっても **ブランチ破棄せず** PR/マージで履歴に残す（後で読み返したときの学習材料を優先）。

## KMP 両対応の徹底

- 機能追加は **iOS / Android 両方** 実装・検証して初めて完了。「Android で動いた」だけでクローズしない。
- iOS 側 `expect` を新規追加するときは Darwin cinterop 用の `@OptIn(ExperimentalForeignApi::class)` opt-in が要る場面が多い。既存の `KeychainTokenStorage` / `DarwinPersistentCookiesStorage` を参考にする。
- Kotlin/Native → Obj-C 公開時のクラス名は `<FileName>Kt` suffix が付く（Swift 側で `KoinIosKt.doInitKoin()` のように呼ぶ）。Swift から触られる Kotlin ファイル名は安易にリネームしない。

## 困ったときに見る場所

- 実装仕様 / API 表 / アーキテクチャ詳細 → [`README.md`](./README.md)
- KMP 固有のトラブルシュート（cocoapods / Xcode / ビルドキャッシュ等） → [`.claude/skills/SKILL.md`](./.claude/skills/SKILL.md)
- 過去のタスク計画 → [`docs/tasks/`](./docs/tasks/)
- Claude Code 用ワークフロー（`/create-task` → `/start-with-plan` → `/code-review` → `/pr-create`） → [`.claude/README.md`](./.claude/README.md)
