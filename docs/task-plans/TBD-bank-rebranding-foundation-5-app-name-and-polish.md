# `<TBD>` fuju 銀行リブランディング foundation (5/5) — アプリ表示名 + 仕上げ

> Notion タスク ID: `<TBD>` （Notion 起票時に追記する。ブランチ名は `feature/<TBD>-bank-rebranding-foundation-5-app-name-and-polish`）
>
> **全体像** は `docs/task-plans/TBD-bank-rebranding-foundation.md`（インデックス）を参照。
> 本 PR は foundation を 5 分割した **5/5 番目（最終）**。foundation 全体の仕上げ。

## 概要

アプリ表示名（Android `app_name` / iOS `CFBundleDisplayName`）を必要に応じて「fuju 銀行」系に統一し、foundation 全体の最終ビルド検証 + 動作確認を行う。foundation のクロージング PR。

## 背景・目的

- 表示名はユーザーから見える最も目立つ箇所。リブランディング後の名称（Figma 上での指定）に合わせて統一する必要がある。
- foundation を 5 PR に分割した結果、各 PR では局所的な検証しかできていない。最終 PR で **全体ビルド + Android/iOS 両方での目視確認** を行い、foundation のリブランディング作業を締める。
- 後続の画面別タスク（home / transactions / account / notification-settings）が安心してこの状態をベースに着手できるようにする。

## スコープ（この PR でやること）

| # | 項目 | 内容 |
|---|------|------|
| 追加 | Android `app_name` 更新 | Figma の表示名指定があれば `strings.xml` 更新。なければスキップ |
| 追加 | iOS `CFBundleDisplayName` 更新 | Figma の表示名指定があれば `Info.plist` 更新。なければスキップ |
| 仕上げ | 全体ビルド検証 | `./gradlew build` + `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` |
| 仕上げ | Android 動作確認 | エミュレータで起動 → スプラッシュ → ログイン画面までの一連 |
| 仕上げ | iOS 動作確認 | iOS Simulator で起動 → スプラッシュ → SwiftUI 画面までの一連 |
| 仕上げ | foundation 完了レポート | PR Description で 5 PR の集約・後続タスクの起票準備状況を整理 |

## スコープ外（後続 **タスク** で対応、foundation の範囲外）

- ホーム / 取引履歴 / アカウント / 通知設定の画面実装（別タスク `<TBD>-bank-rebranding-home` 等）
- `composeApp` の commonMain 化（`<TBD>-kmp-common-migration`）

## 前提となる先行 PR

- `1-rename`
- `2-currency-formatter`
- `3-theme-tokens`
- `4-splash`

## 後続 PR

- なし（foundation 最終）。以後は **後続タスク**（home / transactions / account / notification-settings / kmp-common-migration）が独立タスクとして起票される。

## 影響範囲

### モジュール / ソースセット

- `composeApp/src/androidMain/res/values/strings.xml` — `app_name`（必要時のみ）。
- `composeApp/src/androidMain/AndroidManifest.xml` — `@string/app_name` を参照しているのみなので変更不要（strings.xml の更新で反映される）。
- `iosApp/iosApp/Info.plist` — `CFBundleDisplayName`（必要時のみ）。

### 破壊的変更

- **`applicationId`** (`studio.nxtech.fujubank`): 互換性のため **据え置き**。表示名のみ変更する。

### 追加依存 (`gradle/libs.versions.toml`)

- なし。

## 影響ファイル一覧

確認済み：

- `composeApp/src/androidMain/res/values/strings.xml` — `app_name` を「ふじゅ〜」のまま継続している現状。
- `composeApp/src/androidMain/AndroidManifest.xml` — `@string/app_name` 参照のみ。
- `iosApp/iosApp/Info.plist` — `CFBundleDisplayName`。

## 実装ステップ

1. **Figma で表示名指定を確認**:
   - Figma 銀行版デザインで「fuju 銀行」「FujuBank」など、ホームスクリーンに表示される名称が指定されているかを確認。
   - 指定がなければ既存の「ふじゅ〜」を継続し、本 PR のステップ 2-3 をスキップ。
2. **Android `app_name` 更新（必要時のみ）**:
   - `composeApp/src/androidMain/res/values/strings.xml` の `app_name` を更新。
3. **iOS `CFBundleDisplayName` 更新（必要時のみ）**:
   - `iosApp/iosApp/Info.plist` の `CFBundleDisplayName` を更新（Xcode の Target → General → Display Name でも可）。
4. **全体ビルド検証**:
   - `./gradlew build` 全体実行。
   - `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` でフレームワークビルド確認。
5. **Android 動作確認**:
   - `./gradlew :composeApp:assembleDebug` 後、エミュレータで起動。
   - 確認項目:
     - [ ] スプラッシュが新ロゴで表示される
     - [ ] ホームスクリーンのアプリ名が新表示名になっている（変更した場合のみ）
     - [ ] 既存ログイン画面の背景 / 文字色が新カラートークンになっている
     - [ ] 金額表示画面（debug ビルドで到達できれば）で「ふじゅ〜」表記になっている
6. **iOS 動作確認**:
   - `open iosApp/iosApp.xcodeproj` → Xcode で Run（iPhone 15 Simulator）。
   - 確認項目:
     - [ ] スプラッシュが新ロゴで表示される
     - [ ] ホームスクリーンのアプリ名が新表示名になっている（変更した場合のみ）
     - [ ] SwiftUI 画面で金額表記が `CurrencyFormatter` 経由の「ふじゅ〜」になっている
7. **PR Description の整理**:
   - foundation の 5 PR 一覧と各完了状況を整理。
   - 後続タスク（home / transactions / account / notification-settings / kmp-common-migration）の Notion 起票準備が整ったことを明記。
   - PR 3 の「暫定パッチ箇所」のうち未解消なものがあれば、後続タスクで解消されることをチェックリスト化。

## 完了条件

- [ ] Figma 表示名指定の有無を確認済み。指定があれば Android / iOS とも反映済み。
- [ ] `./gradlew build` が通る。
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が成功する。
- [ ] Android エミュレータで起動から既存到達画面まで動作確認済み（スクリーンショット添付）。
- [ ] iOS Simulator で起動から既存到達画面まで動作確認済み（スクリーンショット添付）。
- [ ] `grep -R "Fujupay" .`（コード側）の結果が 0 件。
- [ ] `grep -R '"円"' composeApp/ iosApp/` の結果が 0 件。
- [ ] PR Description に foundation 5 PR の集約と後続タスクの起票準備状況が記載されている。

## リスク / 注意点

- **`applicationId` は変更しない**: ストア互換性 / インストール済みアプリの上書きを担保するため。表示名 (`app_name` / `CFBundleDisplayName`) のみ変更する。
- **Figma 表示名指定がない場合の判断**: ヒアリング結果が「アプリ表示名は据え置き OK」なら本 PR のステップ 2-3 はスキップして、仕上げ検証のみ行う。
- **foundation のクロージング PR の意義**: 単独で見るとボリュームが小さい PR になりがちだが、**「foundation 全体が動くことを保証する最終チェックポイント」** としての役割が大きい。検証ログ / スクリーンショットを PR Description に残すことを最優先する。
- **後続タスクへの引き継ぎ**: PR 3 で残った「暫定パッチ箇所」は後続の画面別タスクで作り直すため、本 PR で必ずリスト化して引き継ぐ。

## ブランチ / PR 運用（メモリ準拠）

- ブランチ: `feature/<TBD>-bank-rebranding-foundation-5-app-name-and-polish`
- コミット例:
  - `chore: app_name を銀行版に更新`（必要時のみ）
  - `chore: iOS の CFBundleDisplayName を銀行版に更新`（必要時のみ）
  - `chore: foundation 全体ビルド + 動作確認`
- レビュー強制は不要（ソロ開発）。セルフレビュー後にマージ。
