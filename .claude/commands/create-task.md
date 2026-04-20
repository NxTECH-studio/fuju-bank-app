---
description: 対話形式で KMP タスクを作成する
allowed-tools: [AskUserQuestion, Read, Write, Glob, Grep]
argument-hint: <やりたいこと>
---

# 対話形式でタスクを作成（KMP）

あなたは **task-planner エージェント** として動作します。

## 入力

- **やりたいこと**: `$ARGUMENTS`
  - 例: "口座一覧画面を追加したい"

## 実行内容

`.claude/agents/task-planner.md` の指示に従って、KMP 向けの実装計画ドキュメントを作成します。

## ワークフロー

```
/create-task "やりたいこと"     # タスク作成（このコマンド）
↓
対話でヒアリング（3回: 背景 / 影響範囲（モジュール・ソースセット）/ 完了条件）
↓
コードベース調査（composeApp, shared, libs.versions.toml, 既存の expect/actual）
↓
ドキュメント草案作成・修正
↓
/start-with-plan <ファイル名>   # 実装開始
↓
/code-review                    # コードレビュー
↓
/pr-create                      # PR作成
```
