---
description: KMP プロジェクトで PR を作成する
allowed-tools: [Bash, Read, Grep, Glob]
---

現在のブランチの変更内容から Pull Request を作成する。

## 手順

1. `git status` と `git log` で現在のブランチの状態と変更内容を確認する
2. 未コミットの変更があれば、コミットするか確認する
3. ベースブランチを特定する（`develop` → `main` の順で存在するものを使う。以降 `<base>`）
4. `git diff origin/<base>...HEAD` でベースブランチからの差分を確認する
5. 静的検査 / ビルド検証を実行する
   - `./gradlew build`（常に）
   - `./gradlew check`（lint / ktlint / detekt が設定されていれば）
   - 警告・エラーがあれば PR 作成を中断し修正する
6. 変更内容を分析し、PR のタイトルとサマリを作成する
   - 変更対象モジュール（composeApp / shared / iosApp）と影響プラットフォーム（Android / iOS）を明記
7. リモートに push されていなければ `git push -u origin <branch>` で push する
8. `gh pr create` で PR を作成する

## PR 作成フォーマット

`.github/pull_request_template.md` が存在する場合はそのテンプレートに従う。存在しない場合は以下のフォーマットを使用する。

```
gh pr create --base <base> --title "<タイトル>" --body "$(cat <<'EOF'
## 概要
<変更の目的と概要>

## 変更範囲
- モジュール: <composeApp / shared / iosApp>
- ソースセット: <commonMain / androidMain / iosMain / ...>
- 対象プラットフォーム: <Android / iOS / 両方>

## 細かい変更点
<具体的な変更点の箇条書き>

## 検証
- [ ] ./gradlew build
- [ ] ./gradlew :composeApp:assembleDebug (UI変更時)
- [ ] ./gradlew :shared:allTests (shared変更時)
- [ ] ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 (iOS影響時)

## 影響範囲・懸念点
<破壊的変更 / Shared framework ABI 変更 / Swift 側への影響。なければ「なし」>

## その他
<その他伝えておきたいこと。なければ「なし」>
EOF
)"
```

## ルール

- タイトルは 70 文字以内で簡潔にする
- ベースブランチは自動検出する（`develop` があれば `develop`、なければ `main`）
- PR の URL を最後に表示する
