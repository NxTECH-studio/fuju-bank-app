# .claude (KMP)

Kotlin Multiplatform / Compose Multiplatform プロジェクト向けの [Claude Code](https://docs.claude.com/en/docs/claude-code) 設定テンプレート集。

元ネタは [ruribou/.claude](https://github.com/ruribou/.claude) の汎用テンプレートで、本リポジトリは **KMP（Android / iOS を Compose Multiplatform で共通化する構成）** 向けにチューニングしている。

## 対象プロジェクト構成

```
root/
├── composeApp/         # Compose Multiplatform アプリ（Android エントリ含む）
│   └── src/
│       ├── commonMain/kotlin/   # 全ターゲット共通
│       ├── androidMain/kotlin/  # Android 固有
│       └── iosMain/kotlin/      # iOS 固有
├── shared/             # ロジック層（モデル / ドメイン / プラットフォーム抽象）
│   └── src/
│       ├── commonMain/kotlin/
│       ├── androidMain/kotlin/
│       └── iosMain/kotlin/
├── iosApp/             # iOS ネイティブエントリ（SwiftUI / Xcode プロジェクト）
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/libs.versions.toml
```

- ビルドツール: Gradle (Kotlin DSL) + Version Catalog (`gradle/libs.versions.toml`)
- UI: Compose Multiplatform (Material3)
- ターゲット: `androidTarget()`, `iosArm64()`, `iosSimulatorArm64()`
- 共通抽象: `expect` / `actual` でプラットフォーム依存を切り出す

## ディレクトリ構成

```
.claude/
├── README.md              このファイル
├── settings.json          共有してよい権限設定（git / gh / gradlew）
├── settings.local.json    ユーザーローカル設定（.gitignore 推奨）
├── review-patterns.md     KMP 特化のレビュー観点
├── agents/
│   ├── task-planner.md    対話ヒアリング → 実装計画ドキュメント作成
│   └── implementer.md     実装計画に沿ってステップ実行（KMP 検証つき）
├── commands/
│   ├── create-task.md     /create-task      タスク作成
│   ├── start-with-plan.md /start-with-plan  実装開始
│   ├── code-review.md     /code-review      並列レビュー
│   ├── pr-create.md       /pr-create        PR 作成
│   └── clean-branch.md    /clean-branch     マージ済みブランチ整理
└── skills/
    └── SKILL.md           KMP 固有のトラブルシュート置き場
```

## 想定ワークフロー

```
/create-task "やりたいこと"     # task-planner が対話でヒアリング → docs/tasks/*.md を生成
        ↓
/start-with-plan <file>         # implementer がステップごとに実装・Gradle 検証・コミット
        ↓
/code-review                    # 4観点（KMP 観点込み）で並列レビュー
        ↓
/pr-create                      # ベースブランチ自動検出で PR 作成
```

補助: `/clean-branch` — マージ済みローカルブランチを整理。

## KMP 特化ポイント

- **検証コマンド**: `./gradlew build` / `./gradlew :composeApp:assembleDebug` / `./gradlew :shared:allTests` を想定
- **ソースセット境界**: `commonMain` に依存特化コードを書かない（Android / iOS SDK 直呼び禁止）。必要なら `expect`/`actual` で切る
- **Compose Multiplatform**: UI ロジックは `commonMain` 側に寄せ、`androidMain` / `iosMain` は薄いアダプタに留める
- **iOS ビルド検証**: macOS 環境では `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が通るまでを最低ラインとする
- **ブランチ運用**: `develop` → `main` の2段を想定するが、`develop` が無ければ `main` をベースにする（ソロ開発 OK）

## カスタマイズ

- KMP 固有の罠（依存解決・cocoapods・Xcode 設定など）は `skills/SKILL.md` に蓄積する
- プロジェクト固有の規約は `CLAUDE.md` 側に書く
