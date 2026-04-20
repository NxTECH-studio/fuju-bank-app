---
description: 実装計画ドキュメントに基づいて KMP の実装を開始する
allowed-tools:
  [Agent, Bash, Read, Write, Edit, Grep, Glob, TaskCreate, TaskUpdate]
args: path
---

指定された実装計画ドキュメントに基づき、implementer エージェントのワークフローで KMP プロジェクトの実装を進める。

## 引数

- `$ARGUMENTS`: 実装計画ドキュメントのパス（例: `docs/tasks/add-account-list.md`）
  - `docs/tasks/` を省略した場合は自動的に補完する

## ワークフロー

1. 実装計画ドキュメント `$ARGUMENTS` を読み込む
   - パスに `docs/tasks/` が含まれていなければ `docs/tasks/$ARGUMENTS` として読む
2. ドキュメントの内容を把握し、実装ステップを TaskCreate で TODO リストとして作成する
3. 各ステップを順番に実装する
   - 既存コード（特に該当モジュールの `commonMain` / `androidMain` / `iosMain`）を確認してから変更する
   - `expect`/`actual` / Compose Multiplatform / Version Catalog の既存パターンに倣う
4. 各ステップ完了時に TaskUpdate でステータスを更新する
5. 全ステップ完了後、KMP 検証コマンドを実行する
   - `./gradlew build`（常に）
   - `./gradlew :shared:allTests`（shared 変更時）
   - `./gradlew :composeApp:assembleDebug`（composeApp 変更時）
   - `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`（iOS 影響時 / macOS 環境のみ）
   - `./gradlew check`（lint / ktlint / detekt が設定されていれば）
   - 警告・エラーがあれば修正する
6. 適切な粒度でコミットする
7. 完了報告を出力する

## 全体フロー

```
/create-task → /start-with-plan → /code-review → /pr-create
```

実装完了後、`/code-review` でレビューし、`/pr-create` で PR を作成する。
