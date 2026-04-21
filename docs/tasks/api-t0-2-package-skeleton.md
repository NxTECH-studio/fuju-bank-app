# T0-2: パッケージ骨格の作成

## 概要

shared モジュールに API 連携層のパッケージディレクトリを新設し、以降の並行タスクがファイル・ディレクトリ競合なく追加できる下地を作る。

## 背景・目的

Phase 1 以降の複数タスクが `shared/commonMain` 下に新規ファイルを追加する。最初にディレクトリとマーカーファイルを用意しておくことで、並行タスクの `git add` が競合しないようにする。

## 影響範囲

- モジュール: shared
- ソースセット: commonMain
- 破壊的変更: なし
- 追加依存: なし

## 実装ステップ

1. 以下のディレクトリを新設:
   - `shared/src/commonMain/kotlin/studio/nxtech/fujubank/network/`
   - `shared/src/commonMain/kotlin/studio/nxtech/fujubank/auth/`
   - `shared/src/commonMain/kotlin/studio/nxtech/fujubank/data/remote/`
   - `shared/src/commonMain/kotlin/studio/nxtech/fujubank/data/remote/dto/`
   - `shared/src/commonMain/kotlin/studio/nxtech/fujubank/data/remote/api/`
   - `shared/src/commonMain/kotlin/studio/nxtech/fujubank/data/repository/`
   - `shared/src/commonMain/kotlin/studio/nxtech/fujubank/domain/model/`
   - `shared/src/commonMain/kotlin/studio/nxtech/fujubank/di/`
2. 各ディレクトリに最小のプレースホルダを配置（後続タスクで置換される想定）:
   - 例: `network/NetworkMarker.kt` に `internal object NetworkMarker`
   - 他ディレクトリも同様の marker を置く

## 検証

- [ ] `./gradlew :shared:build`
- [ ] `./gradlew :shared:allTests`

## 依存

- T0-1

## 技術的な補足

- Kotlin では空ディレクトリを git 管理できないため、marker ファイルは必須。後続タスクで最初の実ファイルが追加された時点で marker は削除して OK（各タスクの「実装ステップ」に明記すること）。
- `iosMain` / `androidMain` のディレクトリは、expect を最初に入れる T1-1 / T1-2 で初めて作成するため本タスクでは作らない。
