# Fuju Bank App

「感情を担保とする中央銀行」fuju-bank の **クライアントアプリ**。
Kotlin Multiplatform + Compose Multiplatform で Android / iOS を単一コードベースから提供する、
銀行層バックエンド [fuju-bank-backend](https://github.com/NxTECH-studio/fuju-bank-backend) の
フロントエンド層に相当します。

鑑賞者がアート作品の前で滞留し、視線を向けた時間を、作品に「魂を削った」作家（User）へ
ふじゅ〜として還元する、というコンセプトを支える **作家向け HUD / 一般鑑賞者向け体験クライアント**
として設計されます。

## 3 層アーキテクチャでの位置づけ

本リポジトリは fuju-bank プロダクトの **3 層目（クライアント / 作家 HUD）** に属します。
銀行（1 層目）とマイニング（2 層目）から供給される残高・取引・リアルタイム着金通知を
受け取って表示するのが主責務です。

| 層 | リポジトリ | 責務 |
|---|---|---|
| 1 層目 **銀行** | [fuju-bank-backend](https://github.com/NxTECH-studio/fuju-bank-backend) | 発行・記帳・決済・配信の中央台帳（Rails 8.1 API） |
| 2 層目 **マイニング** | （別リポジトリ） | ブラウザ内 MediaPipe で視線・滞留をエッジ解析、重み付け計算 |
| 3 層目 **デモ SNS / 作家 HUD** | **本リポジトリ** | タイムライン滞留でマイニング、作家 HUD へ push 通知を受信 |

クライアント側は小数の計算には関与せず、銀行 API が返す **整数（`bigint`）** の残高・取引量を
そのまま表示します。

## 主な画面・機能（予定）

| 画面 / 機能 | 説明 |
|---|---|
| ログイン / サインアップ | AuthCore が発行する JWT を取得し、以降の API 呼び出しで `Authorization: Bearer <jwt>` として付与 |
| 残高ダッシュボード | `GET /users/:id` の `balance_fuju` を表示 |
| 取引履歴 | `GET /users/:id/transactions` の mint / transfer 統合ビューを表示 |
| リアルタイム着金通知 | ActionCable `UserChannel` を購読し、`credit` イベント（mint / transfer）を push 通知 / HUD で表示 |
| 送金（将来） | `POST /ledger/transfer`（Introspection + 将来的に `MfaRequired` 対象） |

## 連携する API（fuju-bank-backend）

| Method | Path | 用途 | 認証 |
|---|---|---|---|
| `POST` | `/users` | User 作成 | ローカル JWT |
| `GET` | `/users/:id` | User 情報 + 残高取得 | ローカル JWT |
| `GET` | `/users/:id/transactions` | 取引履歴（mint / transfer 統合） | ローカル JWT |
| `GET` | `/artifacts/:id` | Artifact 情報 | ローカル JWT |
| `POST` | `/ledger/transfer` | 送金（User → User） | ローカル JWT + introspection |

- **べき等性**: `POST /ledger/transfer` を呼ぶ際は `Idempotency-Key` ヘッダ（または body の `idempotency_key`）必須。
  リトライ時も同一キーを再利用すること。
- **統一エラーレスポンス**: `{"error": {"code": "...", "message": "..."}}` 形式。`code` で i18n メッセージを切り替える。
  代表コード: `VALIDATION_FAILED` / `NOT_FOUND` / `INSUFFICIENT_BALANCE` / `UNAUTHENTICATED` / `TOKEN_INACTIVE` / `AUTHCORE_UNAVAILABLE` / `MFA_REQUIRED`。
- **ActionCable**: `UserChannel` に `user_id` を subscribe params で渡して接続。
  `credit` イベントのペイロードは `{ type, amount, transaction_id, transaction_kind, artifact_id, from_user_id, metadata, occurred_at }`。

## 認証（AuthCore 連携）

認証基盤は別リポジトリの **AuthCore**（JWT RS256 + introspection 併用）。
クライアント側は以下を責務とします。

- AuthCore に対するログイン / トークン取得（アクセストークン / リフレッシュトークン）
- アクセストークンを安全に保管する
  - Android: EncryptedSharedPreferences / Keystore
  - iOS: Keychain
- トークン期限切れ時のリフレッシュ / 再ログイン導線
- 銀行 API への呼び出し時に `Authorization: Bearer <jwt>` を付与
- 403 + `MFA_REQUIRED` が返ってきた場合は MFA 画面へ誘導

銀行側 `User` は AuthCore の `sub`（ULID, 26 文字）で同定されるため、クライアント側は
ユーザー ID の管理に AuthCore の `sub` を使用します。

## 技術スタック

| カテゴリ | 技術 |
|---|---|
| 言語 | Kotlin (Multiplatform) |
| UI | Compose Multiplatform (Material3) |
| ビルド | Gradle (Kotlin DSL) + Version Catalog (`gradle/libs.versions.toml`) |
| ターゲット | Android (`androidTarget()`), iOS (`iosArm64()`, `iosSimulatorArm64()`) |
| ライフサイクル | `androidx.lifecycle.viewmodel-compose` / `runtime-compose` |
| テスト | `kotlin.test`（`commonTest`） |

## プロジェクト構成

```
root/
├── composeApp/                 # Compose Multiplatform アプリ（Android エントリ含む）
│   └── src/
│       ├── commonMain/kotlin/  # 全ターゲット共通の UI / ViewModel
│       ├── androidMain/kotlin/ # Android 固有（Activity / Context 依存等）
│       └── iosMain/kotlin/     # iOS 固有
├── shared/                     # ドメイン / プラットフォーム抽象層
│   └── src/
│       ├── commonMain/kotlin/
│       ├── androidMain/kotlin/
│       └── iosMain/kotlin/
├── iosApp/                     # iOS ネイティブエントリ（SwiftUI / Xcode）
├── gradle/libs.versions.toml   # Version Catalog
├── build.gradle.kts
└── settings.gradle.kts
```

- **モジュール境界**: UI は `composeApp`、API クライアント / モデル / 認証保管などドメイン処理は `shared` に寄せる。
- **プラットフォーム固有**: 必要な箇所のみ `commonMain` に `expect` を置き、`androidMain` / `iosMain` で `actual` 実装。
- **依存追加**: `gradle/libs.versions.toml` に追記し、`build.gradle.kts` からは `libs.xxx` 経由で参照。

## セットアップ

### 前提

- JDK 17 以上（推奨: JDK 17）
- Android Studio（Koala 以降推奨）
- Xcode（iOS 側をビルドする場合）
- macOS（iOS フレームワーク生成を行う場合）

### Android

開発版ビルドは IDE の Run から、またはコマンドラインから実行できます。

```bash
./gradlew :composeApp:assembleDebug
```

### iOS

`iosApp/` を Xcode で開き、実機 / シミュレータで実行します。
Kotlin 側フレームワークのみを先にリンクしたい場合:

```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

## 検証コマンド早見表

| 目的 | コマンド |
|---|---|
| 全ターゲットのビルド確認 | `./gradlew build` |
| ユニットテスト（共通） | `./gradlew :shared:allTests` |
| Android デバッグ APK | `./gradlew :composeApp:assembleDebug` |
| iOS シミュレータ向けフレームワーク | `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` |
| Gradle デーモン再起動 | `./gradlew --stop` |

## Claude Code 設定

本リポジトリには Claude Code 向けの設定（`.claude/`）を同梱しています。
詳細は [.claude/README.md](./.claude/README.md) を参照してください。

主なワークフロー:

```
/create-task "やりたいこと"   # 対話でヒアリング → docs/tasks/*.md を生成
/start-with-plan <file>       # 計画に沿って実装（Gradle 検証つき）
/code-review                  # KMP 観点込みの並列コードレビュー
/pr-create                    # PR 作成
```

## 関連リポジトリ

- [NxTECH-studio/fuju-bank-backend](https://github.com/NxTECH-studio/fuju-bank-backend) — 銀行層バックエンド（Rails 8.1 API）

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html).
