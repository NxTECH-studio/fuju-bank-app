# client-bank-20: 認証系 shared テストの拡充と CI test job 新設

> **Notion ID**: `client-bank-20`
> **優先度**: 中（プロダクトの新機能ではないが、回帰検知の土台）
> **依存**: なし（`client-bank-17` / `client-bank-18` / `client-bank-19` の認証系実装が
> 既にマージ済み or 並行で進む前提。既存実装をテスト追加だけで覆う方針）
> **後続**: `client-bank-21`（残高 / 取引履歴のテスト拡充）、
> `client-bank-22`（iosApp Swift テストターゲット導入）を別タスクで切る。

## 概要

バックエンド側で当たり前になっている「ロジックを変えたら CI のユニットテストが落ちて
気付ける」状態を、KMP クライアントの `shared` モジュールにも整える。
具体的には認証系（トークンリフレッシュ／期限監視 → ログイン／セッション → MFA/OTP）の
回帰を検知できる commonTest / androidUnitTest / iosTest を整備し、
GitHub Actions に **`test` 専用 job** を新設して既存の `build-and-check` と並列で常時実行する。

## 背景・目的

- 現状:
  - `shared/src/commonTest/` に DTO / Repository / Formatter 系のテストは一定揃っているが、
    **認証フロー全体（refresh → expire watcher → login → MFA verify）を一連で見るテストが薄い**。
    特に `client-bank-17` / `client-bank-18` / `client-bank-19` で増えたトークン期限監視・
    セッション破棄経路の expect/actual パスは Android / iOS 双方で動作確認されているか曖昧。
  - CI (`.github/workflows/ci.yml`) は以下 3 job:
    - `build-and-check` (ubuntu): `:shared:allTests` + Android assemble + lint
    - `build-ios-framework` (macos): `linkDebugFrameworkIosSimulatorArm64` + `iosSimulatorArm64Test`
    - `build-ios-app` (macos): xcodebuild
  - `:shared:allTests` は ubuntu 側で commonTest + androidUnitTest だけ走り、
    `iosSimulatorArm64Test` は macOS runner 側でしか走らない。**「テストが走ること」自体は
    成立しているが、ロジック回帰検知に焦点を絞った job が分離されていない**ため、
    ビルド失敗とテスト失敗の責任分界がログ上分かりにくい。
- 問題:
  - 認証系のリファクタや backend エラーコード変更を入れたとき、
    回帰が CI で検知されず PR レビューに依存している。
  - Kotlin 側ロジックは expect/actual で Android/iOS 両方を持つが、
    iosTest が薄く（`IosSharedTest.kt` に最小スモークがあるのみ）、
    iOS 側でだけ壊れる回帰を検知できない。
- 目標:
  - 認証系の主要パス + 主要異常系を `commonTest` / `androidUnitTest` / `iosTest` に
    過不足なく配置し、PR で常時実行する。
  - CI に **テスト専用の job (`test`)** を新設して、ビルドチェックとテスト失敗を分離して読める状態にする。
  - カバレッジツール（Kover 等）は今回入れない（観測の前にまずテストの母数を増やす）。

## スコープ

### 含む

- `shared` の以下領域に対する commonTest / androidUnitTest / iosTest の追加・拡充:
  - **(c) トークンリフレッシュ / 期限監視** — `AuthTokenRefresher`, `TokenExpiryWatcher`,
    `TokenStorage` の expect/actual
  - **(a) ログイン / セッション** — `AuthRepository.login()` / `logout()`,
    `SessionStore` 状態遷移, `SessionResetCoordinator`
  - **(b) MFA / OTP** — `AuthRepository.verifyMfa()`, MFA 関連 DTO, OTP CTA バリデーション周辺
- GitHub Actions の `test` 専用 job 新設（既存 `build-and-check` と並列）。
  - JVM 側テスト（commonTest + androidUnitTest）は ubuntu runner
  - iosTest（commonTest の K/N コンパイル + iosSimulatorArm64Test）は macOS runner
- 既存の `build-and-check` / `build-ios-framework` の責務見直し（テストを `test` job に
  寄せて、ビルド job からはテスト実行ステップを削るか残すか実装時に判断）。

### 含まない（後続タスク）

- **(d) 残高表示まわりのテスト** — `LedgerRepository` の追加ケース、`BalanceFormatter` の境界値。
  → `client-bank-21` で対応。
- **(e) 取引履歴まわりのテスト** — `TransactionDto` 境界、`TransactionDateFormatter`、
  ホーム画面 `recentTransactions` 並列 fetch。→ `client-bank-21` で対応。
- **`composeApp/` の UI テスト** — Compose UI Test / Robolectric は今回触らない。
- **`iosApp/` の Swift テストターゲット導入** — Xcode の Test target を iosApp.xcodeproj に
  足すのは別タスク（`client-bank-22` 想定）。Swift 側のロジック（ViewModel/Reducer 等）の
  ユニットテストは KMP shared 側ではなく Xcode 側の責務。
- **Kover / Coverage Reporting** — 今回入れない。
- **Branch protection rule** での test job 必須化 — 別タスクで GitHub 設定を変更する。

## 影響範囲

- モジュール: `shared` のみ（`composeApp` / `iosApp` には触れない）
- ソースセット:
  - `shared/src/commonTest/kotlin/studio/nxtech/fujubank/...` — 共通ロジックのテスト追加
  - `shared/src/androidUnitTest/kotlin/studio/nxtech/fujubank/...` — Android actual 側の動作検証
  - `shared/src/iosTest/kotlin/studio/nxtech/fujubank/...` — iOS actual 側の動作検証
- インフラ: `.github/workflows/ci.yml`
- 破壊的変更: なし（テスト追加と CI 設定変更のみ。プロダクションコードには手を入れない）
- 追加依存: 原則なし。
  - 必要に応じて `kotlinx-coroutines-test`（既に入っている）、`ktor-client-mock`（既に入っている）、
    `multiplatform-settings-test`（既に入っている）の範囲で書く。
  - Turbine（Flow 検証）は現状未導入。`SessionStore.state` の遷移検証で必要になった場合のみ
    `libs.versions.toml` に追加（実装時判断）。

## 実装ステップ

優先順は **c → a → b**（依存の下から積む）。各ステップで commonTest を先に書き、
expect/actual の差分があるところだけ androidUnitTest / iosTest を足す方針。

### Phase 0: 既存テストの棚卸し（半日）

1. `shared/src/commonTest/` 配下の現状リストを README 化はしない（口頭整理で OK）。
   既存の以下テストを確認:
   - `data/repository/AuthRepositoryTest.kt` — login / logout / refresh / verifyMfa
     のハッピーパス + 異常系が一定揃っている
   - `di/AuthTokenRefresherTest.kt` — refresh の SessionStore 連携
   - `session/SessionStoreTest.kt` — 状態遷移
   - `data/remote/dto/AuthDtoTest.kt` — Auth DTO のシリアライズ
2. **不足している領域を以下に整理**（実装時にこのリストを叩き台にする）:
   - `TokenExpiryWatcher` のテスト（`client-bank-19` の実装が入った後）
   - `TokenStorage` の actual 動作（Android EncryptedSharedPreferences / iOS Keychain）
   - 連続 refresh / 同時並行 refresh の Mutex 動作（`client-bank-19` 着手時に新設想定）
   - MFA preToken 期限切れ → fresh login 再実行の遷移
   - logout 後の SessionResetCoordinator 経由 state クリア

### Phase 1: (c) トークンリフレッシュ / 期限監視（1〜2日）

3. **commonTest 追加**:
   - `data/repository/AuthRepositoryTest.kt` に以下ケースを追加:
     - refresh が `INVALID_REFRESH_TOKEN` を返したとき `tokenStorage.clear()` が走る
     - refresh 中に並行で 401 が起きた場合の Mutex 直列化（`client-bank-19` 仕様確定後）
     - `expiresAt` が saveAccess に正しく書き込まれる時刻計算（既存 1 ケースを境界値で増やす）
   - `di/AuthTokenRefresherTest.kt` に以下を追加:
     - refresh 成功で `SessionStore.state` が `Authenticated` を維持
     - refresh 連続失敗で `Unauthenticated` に遷移し、`tokenStorage.clear()` が呼ばれる
   - `session/TokenExpiryWatcherTest.kt` 新設（`client-bank-19` 実装に追従）:
     - `expiresAt - threshold` で `refresh()` が呼ばれる
     - 既に切れている expiresAt で `checkNow()` を呼ぶと即 refresh
     - `Unauthenticated` 中はタイマーが起動しない
     - `Job.cancel()` で監視が確実に停止する
4. **androidUnitTest 追加**:
   - `auth/TokenStorageAndroidTest.kt`（仮）— EncryptedSharedPreferences 経由の
     `saveAccess` / `loadAccess` / `clear` を Robolectric で検証。
     Robolectric を入れるかは要判断（コスト高ければ素の Android Instrumentation は今回見送り、
     `androidx-security-crypto` の差し替え可能な抽象化があるならそちら経由でテスト）。
5. **iosTest 追加**:
   - `auth/KeychainIosTest.kt`（仮）— iOS Keychain は実機/シミュレータの SecItem に依存するため、
     `iosSimulatorArm64Test` で動く範囲のスモーク（save → load → clear のラウンドトリップ）を最小限。
     SecItem の race / セキュリティ属性検証までは踏み込まない。

### Phase 2: (a) ログイン / セッション（1日）

6. **commonTest 追加**:
   - `data/repository/AuthRepositoryTest.kt` に以下を追加:
     - login 成功時の Set-Cookie パース（既存テストで一部カバー、Domain/Path の境界を 1 ケース追加）
     - login 失敗系の追加コード網羅（`USER_NOT_FOUND` / `ACCOUNT_LOCKED` 等の AuthCore 仕様）
     - logout 失敗時もローカル `tokenStorage.clear()` は走る（既に存在 → 再確認）
   - `session/SessionStoreTest.kt` を以下で増強:
     - `Loading → Authenticated → Unauthenticated` のループ可
     - `MfaPending` からの cancel で `Unauthenticated` に戻る
   - `session/SessionResetCoordinatorTest.kt` 新設（`client-bank-18` 経由のクリア統一が
     入っている前提。入っていなければ Phase 2 と同タイミングで先行実装）
7. **iosTest 追加**:
   - `session/SessionStoreIosTest.kt`（仮）— actual 側の `SessionStoreIos` がある場合の
     スレッド境界（main dispatcher vs Default）を Confirm。

### Phase 3: (b) MFA / OTP（1日）

8. **commonTest 追加**:
   - `data/remote/dto/MfaDtoTest.kt` 新設 — preToken / mfa_required / expires_in 境界、
     `mfa_required = false` のときに access_token が来るパターン。
   - `data/repository/AuthRepositoryTest.kt` に追加:
     - verifyMfa 成功 → access が保存される（既存）の時刻系強化
     - verifyMfa 失敗 (`MFA_CODE_INVALID` / `MFA_PRE_TOKEN_EXPIRED`) で access が保存されない
     - preToken 期限切れ後の再 login フロー（`AuthErrorMessages` の文言マッピング含む）
9. **OTP 入力検証ヘルパーのテスト**:
   - `feedback_otp_no_auto_submit` の方針に従い、6 桁完了で auto-submit しないことを
     ロジック層（あれば `OtpCodeState` 等）でテスト。UI レイヤは `composeApp` / `iosApp`
     なので **shared に該当ロジックがある場合のみ** ここでカバー。なければスコープ外として記載。

### Phase 4: CI test job 新設（半日）

10. **`.github/workflows/ci.yml` の改修**:
    - 新規 job `test`:
      - **JVM 側**（ubuntu）: `./gradlew :shared:testDebugUnitTest :shared:jvmTest 2>/dev/null || true`
        ではなく、明示的に `:shared:testDebugUnitTest`（androidUnitTest 相当）と
        `:shared:compileTestKotlinDesktop` 系を分けて呼ぶ。実用上は
        `./gradlew :shared:testDebugUnitTest` + commonTest はこれで巻き取られる。
      - **iOS 側**（macos）: `./gradlew :shared:iosSimulatorArm64Test` を専用 step で実行。
        Konan キャッシュ (`~/.konan`) を既存 `build-ios-framework` と同じキー戦略で共有。
    - 既存 `build-and-check` から `:shared:allTests` ステップを削除するか残すかを判断:
      - **推奨**: 残すと二重実行で時間とコストが増えるので、`build-and-check` 側からは
        テスト step を外し、`build-and-check` はビルド + lint に純化する。
      - 残す場合は「テスト失敗のシグナルを 2 経路から受ける」ことになるが、ログが冗長。
    - `build-ios-framework` 側の `:shared:iosSimulatorArm64Test` も同様に `test` job に寄せる。
      framework link 確認はビルド側に残す。
11. **timeout-minutes 調整**:
    - JVM test step は 15 分目安、iOS test step は Konan キャッシュ次第で 30〜45 分目安。
    - macOS runner の課金は ubuntu の 10 倍なので、iOS test step は **PR トリガーのみ** に絞る
      （現状 `on: pull_request` のみなので OK）。
12. **動作確認**:
    - 本タスクのブランチを push して、新 `test` job が緑になることを確認。
    - わざと commonTest を 1 件 fail させて、`test` job だけが赤くなり `build-and-check` は
      緑のまま、というシグナル分離が効くことを確認（確認後 revert）。

## DoD（受け入れ条件）

- [ ] `./gradlew :shared:allTests` がローカルで通る
- [ ] `./gradlew :shared:iosSimulatorArm64Test` がローカル（macOS）で通る
- [ ] `./gradlew :composeApp:assembleDebug` が通る（テスト追加でプロダクトコードに影響していないことの確認）
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が通る
- [ ] (c) トークンリフレッシュ / 期限監視 の主要パス + 主要異常系が commonTest にある
- [ ] (a) ログイン / セッション の主要パス + 主要異常系が commonTest にある
- [ ] (b) MFA / OTP の主要パス + 主要異常系が commonTest にある
- [ ] expect/actual で分岐しているクラス（`TokenStorage` / `SessionStore` / `Keychain` 等）の
      actual 側に対する最低限のスモークが androidUnitTest / iosTest に置かれている
- [ ] GitHub Actions に `test` job が追加され、PR で commonTest + androidUnitTest +
      iosSimulatorArm64Test が緑になる
- [ ] `test` job で **わざとテストを fail させたとき** に `build-and-check` は緑のまま
      `test` だけが赤くなることを 1 度確認（PR description にスクショで残す）

## CI 変更内容（詳細）

```yaml
# .github/workflows/ci.yml に追加する job のイメージ

  test-jvm:
    name: Tests (commonTest + androidUnitTest)
    runs-on: ubuntu-latest
    timeout-minutes: 20
    steps:
      - uses: actions/checkout@v6
      - uses: actions/setup-java@v5
        with: { distribution: temurin, java-version: '17' }
      - uses: gradle/actions/setup-gradle@v6
      - run: ./gradlew :shared:testDebugUnitTest

  test-ios:
    name: Tests (iosSimulatorArm64)
    runs-on: macos-latest
    timeout-minutes: 45
    steps:
      - uses: actions/checkout@v6
      - uses: actions/setup-java@v5
        with: { distribution: temurin, java-version: '17' }
      - uses: gradle/actions/setup-gradle@v6
      - uses: actions/cache@v5
        with:
          path: ~/.konan
          key: konan-${{ runner.os }}-${{ hashFiles('**/gradle/libs.versions.toml', 'gradle/wrapper/gradle-wrapper.properties') }}
          restore-keys: |
            konan-${{ runner.os }}-
      - run: ./gradlew :shared:iosSimulatorArm64Test
```

実装時の細かい判断事項:

- `:shared:testDebugUnitTest` で commonTest が同時に走るかは Kotlin Multiplatform の
  プラグイン挙動依存。ローカルで確認した上で、必要なら `:shared:allTests` を ubuntu 側で
  使う（ただし `:shared:allTests` は iosTest も含もうとして Linux で失敗する可能性があるので、
  `:shared:check` の中身をタスクグラフで確認してから決める）。
- 既存 `build-and-check` / `build-ios-framework` のテストステップは、新 test job が
  安定して緑になることを確認した後に削除する（同じ PR 内で削除して OK）。
- ステータスチェック必須化（branch protection）は別タスク。今回は job を新設するだけ。

## 後続タスク候補

| Notion ID | 内容 | スコープ |
|---|---|---|
| `client-bank-21` | 残高 / 取引履歴のテスト拡充 | (d) `LedgerRepository` の境界値、(e) `TransactionDto` / `TransactionDateFormatter` / ホーム並列 fetch のロジックテスト |
| `client-bank-22` | iosApp Swift テストターゲット導入 | `iosApp/iosApp.xcodeproj` に Test target を追加、Swift 側 ViewModel / Reducer のユニットテストを書ける状態にする |
| (未採番) | branch protection で `test` job を required に | GitHub 設定変更のみ。test job が安定して緑になり続けることを 1〜2 週間観測してから |
| (未採番) | Kover / カバレッジ計測導入 | 認証系で 70% 以上を目安に。テスト母数が安定してから |
| (未採番) | composeApp の UI テスト | Compose UI Test / screenshot test。MVP 後 |

## 注意点・リスク

### macOS runner コスト

- GitHub Actions の macOS minutes は **ubuntu の 10 倍** で課金される。
  `test-ios` job を毎 PR で走らせると無視できないコスト増になる可能性。
  - 対策案: PR ラベル（例: `skip-ios-tests`）でスキップできる仕組みを後で入れる、
    あるいは draft PR では `test-ios` を skip。今回は **無条件で走らせる** ところからスタート。
  - Konan キャッシュは既存ジョブと共有することで wall time を抑える。

### iosTest の実行時間

- `iosSimulatorArm64Test` は K/N のコンパイルに時間がかかり、
  キャッシュなしで 10〜15 分、ありで 3〜5 分が目安。
- ローカルで開発者が走らせるのは時間コスト高なので、commonTest で十分カバーできるロジックは
  commonTest だけに置き、iosTest は actual 側のスモークに絞る。

### expect/actual テストの書き方

- `commonTest` に置いたテストは Android / iOS どちらでも走る（commonTest はアクチュアルが
  解決される側に展開される）。
  - そのため expect/actual で actual が完全に同等な動作をする場合は commonTest だけで OK。
  - 動作が違う / 副作用が違う（Keychain vs EncryptedSharedPreferences 等）ものだけ
    androidUnitTest / iosTest に actual 専用テストを置く。
- `multiplatform-settings-test` の `MapSettings` 等を使えば commonTest で擬似的に
  ストレージ依存をモックできるので、actual 専用テストは最小化できる。

### `client-bank-19` (TokenExpiryWatcher) の依存

- `TokenExpiryWatcher` 本体がまだマージされていない場合、Phase 1 のうち Watcher テストは
  保留。`client-bank-19` のマージを待ってから書く（または `client-bank-19` の DoD に
  Watcher テストを内包させる方が筋が良いかもしれない — 実装時に判断）。

### 既存テストとの命名衝突

- 既存の `AuthRepositoryTest` / `AuthTokenRefresherTest` / `SessionStoreTest` には
  追記する形にし、新規ファイル（`TokenExpiryWatcherTest` / `MfaDtoTest` /
  `SessionResetCoordinatorTest`）は新設する。1 ファイル 1 SUT を維持。

## 参考: 既存の関連ファイル

- `shared/src/commonTest/kotlin/studio/nxtech/fujubank/data/repository/AuthRepositoryTest.kt`
- `shared/src/commonTest/kotlin/studio/nxtech/fujubank/di/AuthTokenRefresherTest.kt`
- `shared/src/commonTest/kotlin/studio/nxtech/fujubank/session/SessionStoreTest.kt`
- `shared/src/commonTest/kotlin/studio/nxtech/fujubank/data/remote/dto/AuthDtoTest.kt`
- `shared/src/commonMain/kotlin/studio/nxtech/fujubank/data/repository/AuthRepository.kt`
- `shared/src/commonMain/kotlin/studio/nxtech/fujubank/session/SessionStore.kt`
- `shared/src/commonMain/kotlin/studio/nxtech/fujubank/auth/TokenStorage.kt`
- `shared/src/androidMain/kotlin/studio/nxtech/fujubank/auth/TokenStorageFactory.android.kt`
- `shared/src/iosMain/kotlin/studio/nxtech/fujubank/auth/Keychain.ios.kt`
- `.github/workflows/ci.yml`
- `docs/tasks/client-bank-19-proactive-token-expiry-watcher.md`
