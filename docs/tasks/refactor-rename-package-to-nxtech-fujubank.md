# パッケージ名 `com.example.fuju_bank_app` → `studio.nxtech.fujubank` へのリネーム

## 概要

プロジェクト全体のルートパッケージ / Android namespace / iOS bundle identifier を、暫定値 `com.example.fuju_bank_app`（および派生の `fuju_bank_app`）から、正式な組織配下の `studio.nxtech.fujubank` へ統一する大規模リファクタ。iOS の bundle identifier は xcconfig に集約して二重管理を解消する。

## 背景・目的

- `com.example.*` はテンプレート生成直後の暫定値であり、公開アプリで使ってはいけない。
- 組織（NxTECH studio）の正式ドメインは `nxtech.studio` で、パッケージ名はその逆順である `studio.nxtech.*` を採用する。
- iOS の bundle identifier は現状 `com.nxtech.fuju-bank-app.Fujubankapp`（`project.pbxproj` に 2 箇所直書き）と `com.example.fuju_bank_app.Fujubankapp`（`Config.xcconfig`）に分裂しており、正式値に寄せて一本化する。さらに「xcconfig に一元化し、`project.pbxproj` からは直書きを排除して `$(PRODUCT_BUNDLE_IDENTIFIER)` を参照する」方針で二重管理そのものを解消する。
- T0-2 で追加した `data.remote.*` 等のサブパッケージ階層はそのまま維持し、ルートだけを差し替える。
- 現時点ではファイル数が少ない（実装ファイル約 14 本、マーカー中心）ため、このタイミングで一括変換するのが最もコストが低い。後続の API 連携タスク（T1 以降）が大量にファイルを追加する前に終わらせる。

## 影響範囲

- モジュール: `composeApp` / `shared` / `iosApp`
- ソースセット: `commonMain` / `androidMain` / `iosMain` / `commonTest` / `androidUnitTest`
- 破壊的変更:
  - Android `applicationId` 変更 → 既存インストールとは別アプリ扱い（開発中なので影響軽微）。
  - iOS `PRODUCT_BUNDLE_IDENTIFIER` 変更 → 同上。
  - Kotlin ルートパッケージ変更 → Swift 側の `import Shared` 自体は変わらないが、Shared framework が露出するクラスの完全修飾名は変わる（iOS 側 `ContentView.swift` の `Greeting()` 参照は package import 不要のためそのまま動く）。
- 追加依存: なし（純粋なリネーム）。

## 移行後の命名規則

| 項目 | 変更前 | 変更後 |
| --- | --- | --- |
| Kotlin ルートパッケージ（shared / composeApp 共通） | `com.example.fuju_bank_app` | `studio.nxtech.fujubank` |
| `composeApp` android namespace | `com.example.fuju_bank_app` | `studio.nxtech.fujubank` |
| `composeApp` `applicationId` | `com.example.fuju_bank_app` | `studio.nxtech.fujubank` |
| `shared` android namespace | `com.example.fuju_bank_app.shared` | `studio.nxtech.fujubank.shared` |
| iOS `PRODUCT_BUNDLE_IDENTIFIER`（`Config.xcconfig` で単一定義） | 分裂状態（`com.nxtech.fuju-bank-app.Fujubankapp` / `com.example.fuju_bank_app.Fujubankapp`） | `studio.nxtech.fujubank.Fujubankapp$(TEAM_ID)`（xcconfig 一元管理。`project.pbxproj` からは直書きを削除し参照のみに） |
| Compose Resources パッケージ（自動生成） | `fujubankapp.composeapp.generated.resources` | 変更なし（`rootProject.name = "Fujubankapp"` 由来のためそのまま） |
| Shared framework の `baseName` | `Shared` | 変更なし |

サブパッケージは現状維持（例: `studio.nxtech.fujubank.data.remote.dto`, `studio.nxtech.fujubank.auth`, `studio.nxtech.fujubank.network`, `studio.nxtech.fujubank.di`, `studio.nxtech.fujubank.domain.model`, `studio.nxtech.fujubank.data.repository` など）。

## 確定方針: iOS bundle identifier の一元管理

エンジニア確認済み。以下の方針で確定:

- **値の単一ソースは `Config.xcconfig`**。`PRODUCT_BUNDLE_IDENTIFIER=studio.nxtech.fujubank.Fujubankapp$(TEAM_ID)` として定義する（先頭に `com.` は付けない。組織の正式ドメイン逆順 `studio.nxtech.*` に揃える）。
- **`project.pbxproj` 側の直書き 2 箇所（Debug/Release, 行 257 / 286 付近）は削除**。xcconfig からの値がそのまま採用されるようにする（行ごと削除すれば Xcode は xcconfig 由来の値を使う）。
- これにより bundle identifier の二重管理が解消され、今後は `Config.xcconfig` 1 ファイルだけを編集すればよくなる。

## 実装ステップ

### 1. 事前確認

1. 作業ブランチを切る（例: `feature/refactor-rename-package-to-nxtech-fujubank`）。main 直コミットは禁止。
2. `iosApp/iosApp.xcodeproj/project.pbxproj` の未コミット変更を確認（現在 `M` 状態）。今回の作業で上書きするので diff を把握しておく。

### 2. Kotlin パッケージ本体のリネーム（`git mv` で履歴追跡）

対象ファイル（現状 14 本）:

- `composeApp/src/androidMain/kotlin/com/example/fuju_bank_app/App.kt`
- `composeApp/src/androidMain/kotlin/com/example/fuju_bank_app/MainActivity.kt`
- `composeApp/src/androidUnitTest/kotlin/com/example/fuju_bank_app/ComposeAppAndroidUnitTest.kt`
- `shared/src/commonMain/kotlin/com/example/fuju_bank_app/Greeting.kt`
- `shared/src/commonMain/kotlin/com/example/fuju_bank_app/Platform.kt`
- `shared/src/commonMain/kotlin/com/example/fuju_bank_app/auth/AuthMarker.kt`
- `shared/src/commonMain/kotlin/com/example/fuju_bank_app/data/remote/RemoteMarker.kt`
- `shared/src/commonMain/kotlin/com/example/fuju_bank_app/data/remote/api/ApiMarker.kt`
- `shared/src/commonMain/kotlin/com/example/fuju_bank_app/data/remote/dto/DtoMarker.kt`
- `shared/src/commonMain/kotlin/com/example/fuju_bank_app/data/repository/RepositoryMarker.kt`
- `shared/src/commonMain/kotlin/com/example/fuju_bank_app/di/DiMarker.kt`
- `shared/src/commonMain/kotlin/com/example/fuju_bank_app/domain/model/DomainModelMarker.kt`
- `shared/src/commonMain/kotlin/com/example/fuju_bank_app/network/NetworkMarker.kt`
- `shared/src/androidMain/kotlin/com/example/fuju_bank_app/Platform.android.kt`
- `shared/src/iosMain/kotlin/com/example/fuju_bank_app/Platform.ios.kt`
- `shared/src/commonTest/kotlin/com/example/fuju_bank_app/SharedCommonTest.kt`

手順:

1. 各ソースセットで新ディレクトリを作成（`com/example/fuju_bank_app` → `studio/nxtech/fujubank`）:
   - `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/`
   - `composeApp/src/androidUnitTest/kotlin/studio/nxtech/fujubank/`
   - `shared/src/commonMain/kotlin/studio/nxtech/fujubank/`（配下のサブディレクトリ `auth/`, `network/`, `di/`, `domain/model/`, `data/remote/`, `data/remote/api/`, `data/remote/dto/`, `data/repository/` も併せて作成）
   - `shared/src/commonTest/kotlin/studio/nxtech/fujubank/`
   - `shared/src/androidMain/kotlin/studio/nxtech/fujubank/`
   - `shared/src/iosMain/kotlin/studio/nxtech/fujubank/`
2. 各ファイルを `git mv` で新ディレクトリへ移動（サブパッケージ階層は維持）。
3. 各ファイル先頭の `package com.example.fuju_bank_app...` を `package studio.nxtech.fujubank...` に置換。
4. ファイル内の `import com.example.fuju_bank_app...` も同様に置換（現状は内部 import 依存はほぼ無いが、追加された場合のため grep で最終確認）。
5. 旧ディレクトリ `com/example/fuju_bank_app` が空になっていることを確認し、親ディレクトリ（`com/example/`, `com/`）ごと削除。

### 3. Gradle ビルドスクリプト更新

1. `composeApp/build.gradle.kts`:
   - `android.namespace = "com.example.fuju_bank_app"` → `"studio.nxtech.fujubank"`
   - `android.defaultConfig.applicationId = "com.example.fuju_bank_app"` → `"studio.nxtech.fujubank"`
2. `shared/build.gradle.kts`:
   - `android.namespace = "com.example.fuju_bank_app.shared"` → `"studio.nxtech.fujubank.shared"`
3. `settings.gradle.kts` の `rootProject.name = "Fujubankapp"` は変更しない（Compose Resources の生成パッケージ名に影響するため、今回は触らない）。

### 4. iOS 側の更新（bundle identifier を xcconfig に一元化）

1. `iosApp/Configuration/Config.xcconfig` を以下のように書き換える:
   - `PRODUCT_BUNDLE_IDENTIFIER=com.example.fuju_bank_app.Fujubankapp$(TEAM_ID)` → `PRODUCT_BUNDLE_IDENTIFIER=studio.nxtech.fujubank.Fujubankapp$(TEAM_ID)`
   - `PRODUCT_NAME=Fujubankapp` / `TEAM_ID=` / バージョン系はそのまま。
2. `iosApp/iosApp.xcodeproj/project.pbxproj` の直書きを削除:
   - 現在、Debug / Release の 2 つの `buildSettings` ブロックに `PRODUCT_BUNDLE_IDENTIFIER = "com.nxtech.fuju-bank-app.Fujubankapp";` が直書きされている（該当行: 257 行目付近 / 286 行目付近）。
   - **この 2 行を行ごと削除する**（`$(PRODUCT_BUNDLE_IDENTIFIER)` への書き換えではなく、行自体を消す。xcconfig に `PRODUCT_BUNDLE_IDENTIFIER` が定義されていれば、`project.pbxproj` に当該キーがない場合に xcconfig の値が採用される。これにより `project.pbxproj` 側からは bundle identifier の管理が消え、二重管理が根絶される）。
   - 削除後、`grep -n 'PRODUCT_BUNDLE_IDENTIFIER' iosApp/iosApp.xcodeproj/project.pbxproj` がヒット 0 件になることを確認する。
3. Xcode で `iosApp.xcodeproj` を開き、`iosApp` ターゲットの General / Signing & Capabilities タブで Bundle Identifier が `studio.nxtech.fujubank.Fujubankapp`（`TEAM_ID` が空の状態）と表示されることを確認（xcconfig 由来の値が正しく反映されているかの目視確認）。
4. `iosApp/iosApp/ContentView.swift` / `iOSApp.swift`:
   - `import Shared` は変更不要（framework baseName は `Shared` のまま）。
   - `Greeting()` 呼び出しも KMP 側でパッケージが変わっても Swift からは同一シンボル名で見えるため、差分なし（Kotlin の package は Swift の module 分離に影響しない）。

### 5. 参照残りの掃除（grep 全件確認）

以下のキーワードでリポジトリ全体を検索し、実装ファイル・ビルドスクリプトで残存がないことを確認する:

- `com.example.fuju_bank_app`
- `com.example.fuju-bank-app`
- `com.nxtech.fuju-bank-app`
- `fuju_bank_app`（パッケージ由来の snake_case）
- `PRODUCT_BUNDLE_IDENTIFIER`（`Config.xcconfig` 以外にヒットしないことを確認）

残って良いもの:

- `docs/tasks/api-*.md` などの既存タスクドキュメント内の記述（過去の事実なので書き換えない。今回の計画で「旧パッケージ名で書かれている」ことは前提）。
- `gradlew`（公式スクリプトの example URL など）。
- `.idea/Fujubankapp.iosApp.iml`（IDE 自動生成）。

### 6. クリーンビルド

1. `./gradlew clean`
2. `./gradlew build`
3. `./gradlew :composeApp:assembleDebug`
4. `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`

### 7. 実機 / シミュレータ起動確認

1. Android Studio から `composeApp` を Android エミュレータで起動し、既存の "Click me!" 画面が表示されることを確認。
2. Xcode で `iosApp` を iOS シミュレータで起動し、同じ画面が表示されることを確認。Shared framework の再ビルドが走るため、古い DerivedData のクリーンも推奨（`~/Library/Developer/Xcode/DerivedData` 該当プロジェクト分の削除 or Xcode の Clean Build Folder）。
3. 起動後、Xcode の Debug navigator などでアプリの bundle identifier が `studio.nxtech.fujubank.Fujubankapp` になっていることを確認。

## 検証

- [ ] `./gradlew clean` が成功
- [ ] `./gradlew build` が通る
- [ ] `./gradlew :composeApp:assembleDebug` が通る
- [ ] `./gradlew :shared:allTests` が通る
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が通る
- [ ] Android エミュレータでアプリが起動し、Greeting（"Hello, Android ..."）が表示される
- [ ] iOS シミュレータでアプリが起動し、Greeting（"Hello, iOS ..."）が表示される
- [ ] iOS アプリの bundle identifier が `studio.nxtech.fujubank.Fujubankapp` であること（Xcode General タブで確認）
- [ ] `grep -r "com.example.fuju_bank_app" .` で実装ファイル／ビルド設定に残存ヒットがない（`docs/tasks/api-*.md` と `.idea/` 除く）
- [ ] `grep -n 'PRODUCT_BUNDLE_IDENTIFIER' iosApp/iosApp.xcodeproj/project.pbxproj` のヒット件数が 0 である（xcconfig 一元化の確認）

## 技術的な補足

- **`git mv` の粒度**: ディレクトリごと一括 `git mv com/example/fuju_bank_app studio/nxtech/fujubank` が使えるケースでは使ってよい。ただし git は「ディレクトリ移動 + ファイル内容変更（`package` 行の書き換え）」を自動で rename 検出できないことがあるため、`git log --follow` での履歴追跡を担保したい場合は「①`git mv` のみで 1 コミット → ②`package` 行書き換えで 1 コミット」の 2 段階コミットを検討。今回は 1 PR にまとめる方針なので、PR 内で 2 コミットに分ける運用で対応する。
- **Compose Resources**: `composeApp/src/androidMain/kotlin/.../App.kt` で `import fujubankapp.composeapp.generated.resources.Res` を使っている。これは `rootProject.name = "Fujubankapp"` から Compose Multiplatform Gradle plugin が生成するパッケージで、Kotlin のソースパッケージとは独立。`rootProject.name` を維持する限り import 文は変更不要。
- **iOS framework 名**: `shared/build.gradle.kts` の `baseName = "Shared"` はそのまま。Swift 側の `import Shared` も変化なし。Kotlin の package 変更は Objective-C / Swift から見たクラス名（Shared module 内で単にクラス名で見える）に影響しないため、`ContentView.swift` の `Greeting()` 呼び出しはそのまま動く。
- **xcconfig 優先度の挙動**: Xcode のビルド設定は「`project.pbxproj` の `buildSettings` に明示されたキー」が「xcconfig で定義された同名キー」より優先される。今回は `project.pbxproj` から `PRODUCT_BUNDLE_IDENTIFIER` 行を削除することで xcconfig の値が唯一のソースになる。将来 Xcode GUI から bundle id を編集すると再び `project.pbxproj` に書き込まれてしまうので、PR レビュー時・以降のメンテ時には `project.pbxproj` に `PRODUCT_BUNDLE_IDENTIFIER` が復活していないことをチェックする運用にする。
- **applicationId 変更の副作用**: 既に Android エミュレータに旧 `com.example.fuju_bank_app` がインストールされていれば、新 `applicationId` では別アプリ扱いになる。古い方は手動アンインストール。
- **iOS Bundle ID 変更の副作用**: Keychain に旧 bundle id で保存されたものは引き継がれない。T1-2（Token Storage）より前にリネームを済ませておけば、実害なし。
- **Xcode 署名設定**: `TEAM_ID` が空のままなので、ローカルシミュレータ動作には影響しないが、実機確認は不要（本タスクの完了条件はシミュレータまで）。
- **IntelliJ / Android Studio のキャッシュ**: パッケージ一括リネーム後は `File > Invalidate Caches and Restart` を推奨。
