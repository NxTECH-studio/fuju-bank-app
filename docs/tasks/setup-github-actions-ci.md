# GitHub Actions CI セットアップ

## 概要

PR（`main` 宛て）に対して Kotlin Multiplatform プロジェクトの commonMain / androidMain / iosMain ビルド、iOS アプリの Xcode ビルド、および静的解析を自動実行する GitHub Actions ワークフローを追加し、全ジョブ green を PR マージの目安にする。Ubuntu ジョブ 1 本 + macOS ジョブ 2 本の合計 3 ジョブを並列実行する。

## 背景・目的

- 現状 `.github/workflows/` が存在せず、PR 時の自動検証がない。
- ソロ開発でも、commonMain / androidMain / iosMain の各ソースセットで壊れていないことを毎回手動で確認するのはコストが高い。
- Android 側のビルド・テストおよびコード品質チェックを PR の段階で機械的に保証したい。
- `expect`/`actual` の整合性崩れや `iosMain` 側の型エラーは Ubuntu の Android ビルドだけでは検出できないため、iOS 側（shared framework + iosApp）も CI で検証したい。

## 影響範囲

- モジュール: リポジトリルート（`.github/workflows/` の新規追加のみ）
  - `composeApp/`, `shared/`, `iosApp/` のコードは変更しない
- ソースセット: 変更なし（`commonMain` / `androidMain` / `iosMain` を CI でビルドする）
- 破壊的変更: なし
- 追加依存: なし（`libs.versions.toml` の変更なし）
- 今回スコープ外:
  - GitHub のブランチ保護ルール設定（必須チェックの登録は手動で後日）
  - iOS 実機向けビルド / テスト（Simulator 向け Debug ビルドのみを対象）

## 前提（調査結果・運用）

- Gradle: `gradle/wrapper/gradle-wrapper.properties` → `gradle-8.14.3`
- Kotlin: `2.3.20`（`gradle/libs.versions.toml`）
- AGP: `8.11.2`
- JDK: `composeApp/build.gradle.kts` / `shared/build.gradle.kts` ともに `JvmTarget.JVM_11` / `JavaVersion.VERSION_11` → CI も **JDK 17** を使用（AGP 8.x の実行には JDK 17 が必要。ターゲットは 11 のままでよい）
- iOS ターゲット: `iosArm64`, `iosSimulatorArm64`（`shared/build.gradle.kts`）→ CI では `iosSimulatorArm64` 向け Debug framework をビルド
- Xcode プロジェクト: `iosApp/iosApp.xcodeproj`（scheme: `iosApp`、`PRODUCT_NAME=Fujubankapp`）
- 既存 `expect`/`actual`: `Platform.kt` / `Platform.android.kt` / `Platform.ios.kt`
- **リポジトリ公開範囲**: Public リポジトリ（`NxTECH-studio/fuju-bank-app`）。GitHub Actions は Public リポに対して ubuntu / macOS いずれの runner も**無料**で使えるため、macOS ジョブを追加しても課金コストは発生しない。
- **PR マージ運用**: マージコミット縛り（squash / rebase は使わない）。CI は PR HEAD に対して動けば良いため、この運用は CI 設定に直接は影響しない。

## 実装ステップ

1. `.github/workflows/ci.yml` を新規作成する。トリガーは以下:
   - `pull_request`（対象ブランチ: `main`）のみ
   - `main` への push トリガーは **追加しない**（PR 時の green を担保すればマージコミット運用下で十分）
2. 共通設定:
   - `concurrency` で同一 PR の古い実行をキャンセル（`group: ci-${{ github.ref }}` / `cancel-in-progress: true`）
   - すべてのジョブで `actions/checkout@v4`
   - `actions/setup-java@v4` で `temurin` JDK 17
   - `gradle/actions/setup-gradle@v4` で Gradle キャッシュを有効化
3. ジョブ構成（3 ジョブを並列実行）:
   - **build-and-check** (`ubuntu-latest`)
     - `./gradlew :shared:allTests` … commonTest + androidUnitTest を実行
     - `./gradlew :composeApp:assembleDebug` … Android Debug ビルド
     - `./gradlew :composeApp:lintDebug` … Android Lint
     - ktlint チェック（該当タスクが設定されていれば `./gradlew ktlintCheck`）
     - detekt チェック（該当タスクが設定されていれば `./gradlew detekt`）
   - **build-ios-framework** (`macos-latest`)
     - `actions/cache@v4` で `~/.konan` をキャッシュ（key は `konan-${{ runner.os }}-${{ hashFiles('**/gradle/libs.versions.toml', 'gradle/wrapper/gradle-wrapper.properties') }}`、restore-keys に `konan-${{ runner.os }}-`）
     - `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`
   - **build-ios-app** (`macos-latest`)
     - `actions/cache@v4` で `~/.konan` をキャッシュ（build-ios-framework と同じ key 体系）
     - `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`（xcodebuild 前に framework を生成）
     - `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build CODE_SIGNING_ALLOWED=NO`
   - 3 ジョブに相互依存は張らず並列実行する（framework ビルドは各 macOS ジョブ内で自己完結させる。Konan キャッシュが両ジョブで共有されれば 2 回目以降のビルドは短縮される）。
   - ktlint / detekt のタスク名・導入有無は既存の `build.gradle.kts` / `libs.versions.toml` を再確認し、未導入なら本タスクのスコープ外（別タスクで導入）とするか、本タスク内で最小導入するかを実装時に決定する。
4. 失敗時の扱い:
   - 上記 3 ジョブすべて（テスト / Android ビルド / lint / ktlint / detekt / iOS framework / iOS app）を **必須 green** の目安とする
   - lint の `abortOnError` は既定のまま（警告ではなくエラーで fail）
5. 自己検証:
   - 本ワークフロー追加 PR 自体で 3 ジョブすべてが green になることを確認

## 検証

- [ ] `.github/workflows/ci.yml` が YAML として valid（`actionlint` をローカルで回す or GitHub 側でパースエラーなし）
- [ ] PR 作成時に 3 ジョブ（`build-and-check` / `build-ios-framework` / `build-ios-app`）が並列起動する（`main` への push では起動しないこと）
- [ ] `./gradlew :shared:allTests` がローカルでも通る（CI と同等のコマンド）
- [ ] `./gradlew :composeApp:assembleDebug` がローカルで通る
- [ ] `./gradlew :composeApp:lintDebug` がローカルで通る
- [ ] ktlint / detekt を採用する場合、対応タスクがローカルで通る
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` がローカル（macOS）で通る
- [ ] `xcodebuild ... -scheme iosApp -sdk iphonesimulator ... CODE_SIGNING_ALLOWED=NO build` がローカル（macOS）で通る
- [ ] 2 回目以降の実行で Gradle キャッシュおよび Konan キャッシュがヒットし、初回より短縮されている

## 技術的な補足

- **JDK の選定**: ソースの `JvmTarget` は 11 のままとし、CI 実行 JDK は 17 を使う。AGP 8.x は JDK 17 以降での実行が推奨されているため。
- **Gradle キャッシュ**: `gradle/actions/setup-gradle@v4` がビルトインで対応。ソロ開発のため read-write の既定設定でよい。
- **Konan キャッシュ**: macOS ジョブでのみ有効化。`~/.konan` 配下に Kotlin/Native コンパイラと依存がダウンロードされるため、キャッシュしないと毎回数分の初期化コストが発生する。`libs.versions.toml` と `gradle-wrapper.properties` の hash を key に含めておくと、Kotlin バージョンアップ時に自動的にキャッシュが切り替わる。
- **iOS Xcode ビルドの署名**: CI では署名不要のため `CODE_SIGNING_ALLOWED=NO`（加えて必要なら `CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY=""`）を付与して Simulator 向け Debug ビルドを通す。`Config.xcconfig` の `TEAM_ID` が空のままでも問題ない。
- **ランナー課金**: Public リポジトリのため GitHub Actions の無料枠とは独立に ubuntu / macOS いずれも無料で使える（有料課金の対象は Private リポジトリの macOS runner）。
- **並列実行**: 3 ジョブに依存関係（`needs`）を張らず並列化することで、iOS 側が失敗しても Android 側の結果が得られ、逆も同様。フィードバックが早くなる。
- **PR マージ運用（マージコミット縛り）**: squash / rebase と異なり、PR ブランチの各コミットがそのまま残る。CI は PR HEAD（merge commit ではなく PR ブランチの最新コミット）に対して走るため、現行設定で問題なし。
- **concurrency**: `group: ci-${{ github.ref }}` / `cancel-in-progress: true` で同一 PR の古い run をキャンセル。
- **ブランチ保護ルール**: 今回スコープ外だが、後日 GitHub の Settings → Branches で 3 ジョブを必須チェックに登録することで、マージコミット運用下でも green 強制が可能になる。
