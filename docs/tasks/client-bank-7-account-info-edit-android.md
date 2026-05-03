# 銀行アプリクライアント：アカウント情報変更画面（仮実装）Android 実装

## 概要

client-bank-4 で導入した準備中画面（`AccountComingSoonScreen("アカウント情報")`）を本実装画面 `AccountEditScreen` に置換する。表示名 / メールアドレス / パスワード（現在 / 新規 / 確認）の入力フォームを Compose で構成し、保存ボタン押下時の値を `multiplatform-settings` にローカル保存する **仮実装レベル ii**（API 連携なし。本番想定でダミーデータをプレースホルダとして投入）。`:shared` 側に `AccountProfileLocalEditor` を追加し、編集後値で `AccountProfileProvider` の表示値を上書きする構造とする。iOS 版は client-bank-5 マージ後に shared API を再利用する形で別タスク化する。

## 背景・目的

### 経緯

- client-bank-4 でアカウントハブ（Figma `697:8394`）の「設定」セクション 3 行のうち、「アカウント情報」行のタップ先は `AccountComingSoonScreen("アカウント情報")` で準備中表示のまま
- 同タスクの「アウトオブスコープ」で「プライバシー設定 / アカウント情報変更の本実装」が次タスク以降と明記されていた
- バックエンド API はまだ確定していないため、本タスクは UI + ローカル保存までで完結させる「仮実装レベル ii」とし、後続の API 接続タスクで `AccountProfileProvider` の実装を差し替えるだけで済む構造を維持する

### 目的

- 準備中表示を廃止し、Figma 想定の編集フォームを表示する
- 保存ボタン押下で入力値を `multiplatform-settings` にローカル永続化し、`AccountProfileProvider.current()` が編集後値を返すようにする（再起動後もアプリ全体で編集後値が見える）
- 入力バリデーション（必須 / メール形式 / パスワード一致 / 最小文字数）を Compose 側で完結
- shared API（`AccountProfileLocalEditor` + `AccountProfileProvider` の合成挙動）を **本タスクで凍結** し、client-bank-5 マージ後の iOS 版タスクが同じ API を参照できる状態にする

## スコープ

- **`:shared` 側の追加 / 変更**
  - `AccountProfileLocalEditor`（commonMain、新規）: `multiplatform-settings` に編集値を書き込み、StateFlow で公開
  - `AccountProfileProvider` の合成挙動: 既存 `DummyAccountProfileProvider` をベース値として、`AccountProfileLocalEditor` の編集値があればそれを優先するように `current()` を見直す
    - 案: `LocalOverrideAccountProfileProvider`（仮称、commonMain）を新設して Koin 登録を差し替える。`DummyAccountProfileProvider` を内部で保持し、editor の上書き値とマージして `AccountProfile` を返す
  - Koin への登録更新（`accountModule`）
- **`AccountEditScreen`（Android）**: 既存 `AccountComingSoonScreen("アカウント情報")` を置換
  - ヘッダー: 戻る `<` + 「アカウント情報」タイトル
  - フォーム:
    - 表示名（TextField、必須、最大 32 文字）
    - メールアドレス（TextField、必須、メール形式バリデーション）
    - パスワード変更セクション（任意、3 つすべて入力時のみ送信に含める）
      - 現在のパスワード（PasswordField）
      - 新しいパスワード（PasswordField、最小 8 文字）
      - 新しいパスワード（確認）（PasswordField、新パスワードと一致）
  - 保存ボタン（フッター、入力に変更があり、かつバリデーション通過時のみ活性）
  - 保存成功時はトースト「保存しました」+ ハブ画面に戻る
- **`AccountEditViewModel`（Android）**: フォーム状態 / バリデーション / 保存処理を担う
- **`RootScaffold` 配線変更**
  - `RootDestination.AccountEdit` の遷移先を `AccountComingSoonScreen("アカウント情報")` から `AccountEditScreen` に置換
- **アカウントハブの即時反映**
  - 保存後にハブへ戻った際に表示名 / メールが新値で表示されることを確認（`AccountHubViewModel` が StateFlow を購読しているか、もしくは画面復帰時に `provider.current()` を再取得する仕組みを整える）

### アウトオブスコープ

- **iOS 実装**: client-bank-5 (iOS) が `main` にマージされた後、本タスクで凍結された `:shared` API を参照する形で別タスク化（仮: `client-bank-10-account-info-edit-ios`）
- **バックエンド API 連携**: `RemoteAccountProfileProvider` 系の実装は別タスク
- **パスワード変更の実バックエンド呼び出し**: 本タスクではパスワード入力値の検証 + ローカル保存（パスワードは `multiplatform-settings` に保存しない、後述）
- **アバター画像変更 / SNS 連携の編集**
- **退会導線**

## 着手条件

**client-bank-4 (`client-bank-4-account-settings-android.md`) が `main` にマージ済みであること**。

具体的には:

- `RootScaffold` の `RootDestination.AccountEdit` 配線が `AccountComingSoonScreen` を表示している
- `:shared` の `AccountProfile` / `AccountProfileProvider` / `DummyAccountProfileProvider` が存在し、Koin に登録されている

## 影響範囲

- モジュール: `:shared` / `:composeApp`
  - `:shared/commonMain`:
    - `AccountProfileLocalEditor` 新規追加（`account/AccountProfileLocalEditor.kt`）
    - `LocalOverrideAccountProfileProvider`（仮称）新規追加 or `DummyAccountProfileProvider` の差し替え
    - `accountModule` 更新（editor 登録 + provider 差し替え）
  - `:composeApp/androidMain`:
    - `features/account/AccountEditScreen.kt` 新規
    - `features/account/AccountEditViewModel.kt` 新規
    - `features/shell/RootScaffold.kt` 配線変更
    - 必要に応じて `features/account/AccountHubViewModel.kt` を `provider.current()` 再取得 or StateFlow 購読に修正
- 破壊的変更:
  - `:shared` の `AccountProfileProvider` 経由で返ってくる `AccountProfile` が、ユーザー編集後はダミー値ではなく上書き値になる（公開 API 形状は維持、挙動拡張）
  - Koin の `single<AccountProfileProvider>` の実装が `DummyAccountProfileProvider` から合成 provider に変わる
  - `RootDestination.AccountEdit` の表示画面が置換される
- 追加依存:
  - なし（`multiplatform-settings` 既存）

## 技術アプローチ

### `:shared` 側設計

#### `AccountProfileLocalEditor`

`commonMain` に `NotificationSettingsPreferences` と同パターンで新規追加:

```kotlin
class AccountProfileLocalEditor(private val settings: Settings) {
    private val _displayNameOverride = MutableStateFlow(settings.getStringOrNull(KEY_DISPLAY_NAME))
    val displayNameOverride: StateFlow<String?> = _displayNameOverride.asStateFlow()

    private val _emailOverride = MutableStateFlow(settings.getStringOrNull(KEY_EMAIL))
    val emailOverride: StateFlow<String?> = _emailOverride.asStateFlow()

    fun saveDisplayName(value: String) {
        settings.putString(KEY_DISPLAY_NAME, value)
        _displayNameOverride.value = value
    }

    fun saveEmail(value: String) {
        settings.putString(KEY_EMAIL, value)
        _emailOverride.value = value
    }

    /** 全リセット用（テスト / 将来の「初期値に戻す」導線で使用）。 */
    fun clear() {
        settings.remove(KEY_DISPLAY_NAME)
        settings.remove(KEY_EMAIL)
        _displayNameOverride.value = null
        _emailOverride.value = null
    }

    private companion object {
        const val KEY_DISPLAY_NAME = "account.profile.displayName"
        const val KEY_EMAIL = "account.profile.email"
    }
}
```

**パスワードは保存しない**: 仮実装段階ではローカル平文保存のリスクが大きいため、フォームでは入力 / バリデーションのみ実施し、保存ボタン押下時にトーストで「（仮実装）パスワード変更は保存されません」相当を出すか、UI 上で「※API 接続後に有効化されます」注記を入れて握り潰す。実 API 接続タスクで本実装する。

#### `AccountProfileProvider` の合成

```kotlin
class LocalOverrideAccountProfileProvider(
    private val base: AccountProfileProvider,   // 既存 DummyAccountProfileProvider
    private val editor: AccountProfileLocalEditor,
) : AccountProfileProvider {
    override fun current(): AccountProfile {
        val baseProfile = base.current()
        return baseProfile.copy(
            displayName = editor.displayNameOverride.value ?: baseProfile.displayName,
            email = editor.emailOverride.value ?: baseProfile.email,
        )
    }
}
```

`accountModule` を以下に更新:

```kotlin
val accountModule = module {
    single { NotificationSettingsPreferences(get<Settings>()) }
    single { AccountProfileLocalEditor(get<Settings>()) }
    single<AccountProfileProvider> {
        val base: AccountProfileProvider = if (BuildKonfig.USE_DUMMY_PROFILE) {
            DummyAccountProfileProvider()
        } else {
            error("RemoteAccountProfileProvider is not implemented yet")
        }
        LocalOverrideAccountProfileProvider(base = base, editor = get())
    }
}
```

これにより既存の `AccountHubViewModel` / `RootScaffold` が `AccountProfileProvider` 経由で取得する値は自動的に「ベース ＋ ローカル上書き」の合成値になる。

#### iOS 用に凍結する shared API

後続 iOS タスクで参照する API（本タスクのマージ以降、シグネチャ変更時は iOS タスクを更新する必要あり）:

- `AccountProfileLocalEditor` クラスとそのメソッド: `saveDisplayName(value: String)` / `saveEmail(value: String)` / `clear()`
- `AccountProfileLocalEditor.displayNameOverride: StateFlow<String?>` / `emailOverride: StateFlow<String?>`
- `AccountProfileProvider.current()` の合成挙動（ベース値 + ローカル上書き）
- Koin での `AccountProfileLocalEditor` 取得経路（`SharedDI.resolve()`）

### Android 側設計

#### `AccountEditViewModel`

```kotlin
class AccountEditViewModel(
    private val provider: AccountProfileProvider,
    private val editor: AccountProfileLocalEditor,
) : ViewModel() {
    data class FormState(
        val displayName: String,
        val email: String,
        val currentPassword: String,
        val newPassword: String,
        val confirmPassword: String,
    )

    data class FieldErrors(
        val displayName: String? = null,
        val email: String? = null,
        val newPassword: String? = null,
        val confirmPassword: String? = null,
    )

    private val initialProfile = provider.current()
    private val _form = MutableStateFlow(FormState(
        displayName = initialProfile.displayName,
        email = initialProfile.email,
        currentPassword = "",
        newPassword = "",
        confirmPassword = "",
    ))
    val form: StateFlow<FormState> = _form.asStateFlow()

    val errors: StateFlow<FieldErrors> = _form.map { validate(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, FieldErrors())

    val canSave: StateFlow<Boolean> = combine(form, errors) { f, e ->
        hasChanges(f) && !e.hasAny()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun onDisplayNameChange(v: String) { _form.update { it.copy(displayName = v) } }
    // ... 他フィールドも同様

    fun save(onComplete: () -> Unit) {
        val f = _form.value
        editor.saveDisplayName(f.displayName)
        editor.saveEmail(f.email)
        // パスワードは仮実装のため保存しない
        onComplete()
    }
}
```

#### `AccountEditScreen`

`AccountComingSoonScreen` のヘッダーを流用しつつ、本文を `LazyColumn` または `Column(verticalScroll)` で構成。`OutlinedTextField` を使うか、既存の入力 UI パターン（signup 系）に合わせるかは実装時に確認（既存パターン優先）。

各フィールドは `errors` の StateFlow を購読してエラー表示。保存ボタンは `canSave` が true のときのみ活性。

#### ハブ画面への反映

`AccountHubViewModel` は client-bank-4 で `MutableStateFlow(profileProvider.current())` で **初期値固定** になっている。本タスクで `provider.current()` の戻り値が動的に変わる構造にしたため、ハブ画面に戻ったときに最新値が見えるよう以下のいずれかを実施:

- **案 A（推奨・最小変更）**: `AccountHubViewModel` を `editor.displayNameOverride` / `editor.emailOverride` の StateFlow を `combine` して購読し、変更時に `_profile` を更新
- **案 B**: `AccountHubScreen` 側で `LifecycleEventEffect(Lifecycle.Event.ON_RESUME)` を使って `provider.current()` を再取得（VM のシグネチャ変更が発生するため案 A 推奨）

案 A は editor の StateFlow を直接購読するためテスト可能性も高い。

## 実装手順

1. **`:shared` 拡張**
   1. `AccountProfileLocalEditor` を `shared/src/commonMain/kotlin/studio/nxtech/fujubank/account/` に新規作成
   2. `LocalOverrideAccountProfileProvider` を同ディレクトリに新規作成
   3. `accountModule` を更新（editor の `single` 登録 + provider 差し替え）
   4. `./gradlew :shared:allTests` 通過
   5. `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` 通過（iOS リンク維持）
2. **`AccountHubViewModel` 修正（案 A）**
   1. `AccountProfileProvider` に加え `AccountProfileLocalEditor` を注入
   2. editor の StateFlow を `combine` で購読し、`_profile` を更新
   3. `RootScaffold` 側の `AccountHubViewModel` 生成箇所も修正
3. **`AccountEditViewModel` 実装**
   1. `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/account/AccountEditViewModel.kt` 新規
   2. フォーム状態 / バリデーション / 保存処理を実装
4. **`AccountEditScreen` 実装**
   1. `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/account/AccountEditScreen.kt` 新規
   2. ヘッダー（`AccountComingSoonScreen` と同じスタイル）
   3. 表示名 / メール / パスワード変更セクションのフォーム
   4. エラー表示 / 保存ボタンの活性制御
   5. パスワード変更セクションの仮実装注記
5. **`RootScaffold` 配線変更**
   1. `RootDestination.AccountEdit` の表示を `AccountComingSoonScreen("アカウント情報")` から `AccountEditScreen(viewModel = ...)` に置換
   2. VM 生成は他画面と同じ `viewModelFactory { initializer { ... } }` パターン
   3. 保存完了後の `onComplete` で `destination = RootDestination.Account` に戻す
6. **動作確認**
   1. `./gradlew :composeApp:assembleDebug` 通過
   2. アカウントハブから「アカウント情報」タップ → `AccountEditScreen` 表示、初期値が現プロフィールで埋まる
   3. 表示名 / メール変更 → 保存 → ハブに戻り表示が更新される
   4. アプリ再起動後も編集値が維持される
   5. メール形式不正 / パスワード不一致 / 必須未入力でエラー表示 + 保存ボタン非活性
   6. パスワード 3 フィールド未入力時はエラーなし（任意扱い）、3 つ揃った時のみバリデーション実施
7. **PR 作成**: `feature/client-bank-7-account-info-edit-android` → `main`

## 完了条件

- [ ] `./gradlew build` が通る
- [ ] `./gradlew :shared:allTests` が通る
- [ ] `./gradlew :composeApp:assembleDebug` が通る
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が通る
- [ ] アカウントハブから「アカウント情報」タップで `AccountEditScreen` が表示される
- [ ] 表示名 / メールの初期値が現プロフィールで埋まっている
- [ ] バリデーション（必須 / メール形式 / パスワード一致 / 8 文字以上）が機能する
- [ ] 保存後、アカウントハブの表示名 / メールが更新される
- [ ] アプリ再起動後も編集値が保持される
- [ ] パスワード入力値はローカルに保存されない（仮実装段階の安全策）
- [ ] `:shared` の `AccountProfileLocalEditor` API が iOS から `SharedDI.resolve()` で取得できる状態（コンパイル可能性まで担保）

## 想定される懸念・リスク

- **パスワード平文保存リスク**: パスワードは `multiplatform-settings` に保存しない方針で固定する。仮実装段階で誤ってデフォルト挙動として保存しないよう、`AccountProfileLocalEditor` にパスワード関連の API を一切追加しない（後続 API 接続タスクで `AuthCore` 経由のパスワード変更 API を呼ぶ形にする）
- **`AccountHubViewModel` の StateFlow 購読への変更**: client-bank-4 では `MutableStateFlow(provider.current())` で初期値固定だったため、editor 購読への変更で挙動が変わる。purely additive な変更だが、iOS の `ObservableAccountHubViewModel`（client-bank-5）も同様に追従する必要が出てくる → 後続 iOS タスクで対応
- **`provider.current()` の合成計算コスト**: 毎回 base + override をマージするだけなので無視できるが、editor の StateFlow を combine する `AccountHubViewModel` の購読が ViewModel ライフサイクル全体で生きることに留意
- **iOS API 凍結リスク**: 本タスクでマージした `AccountProfileLocalEditor` のシグネチャを後で変更すると iOS タスクが追従コストを払うことになる。シグネチャ確定を本タスク内で慎重に行う（特に StateFlow 公開型 / nullable の取り扱い）
- **Figma が未確定**: アカウント情報変更画面の Figma がまだ無い状態のため、レイアウトは「signup 等の既存入力フォームスタイルを踏襲した暫定」とする。Figma 確定後にレイアウト調整タスクを別途切る前提
- **編集値リセット導線**: 「初期値に戻す」UI は本タスクでは実装しない。`editor.clear()` だけ用意しておき、開発中は ADB 経由で `multiplatform-settings` クリア or アプリデータ削除で対応
- **既存 `BuildKonfig.USE_DUMMY_PROFILE` 連動**: 現状 `USE_DUMMY_PROFILE = true` の前提で組む。`false` の場合は `error("...")` のままで良い（実 API 接続タスクで本対応）

## 参考リンク

- 前提タスク 4 (Android アカウント設定): [`client-bank-4-account-settings-android.md`](./client-bank-4-account-settings-android.md)
- 前提タスク 5 (iOS アカウント設定): [`client-bank-5-account-settings-ios.md`](./client-bank-5-account-settings-ios.md)
- 既存 `AccountProfile` / `AccountProfileProvider`: `shared/src/commonMain/kotlin/studio/nxtech/fujubank/account/`
- 既存 Koin モジュール: `shared/src/commonMain/kotlin/studio/nxtech/fujubank/di/accountModule.kt`
- 差し替え対象 Android 画面: `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/account/AccountComingSoonScreen.kt`（呼び出し元: `RootScaffold.kt` 内 `RootDestination.AccountEdit`）
- 既存 `NotificationSettingsPreferences`（パターン参考）: `shared/src/commonMain/kotlin/studio/nxtech/fujubank/account/NotificationSettingsPreferences.kt`

---

## Notion タスク登録用サマリ

- **タイトル**: 銀行アプリクライアント：アカウント情報変更画面（仮実装）Android 実装
- **プレフィックス**: client-bank-7
- **ブランチ命名**: `feature/client-bank-7-account-info-edit-android`
- **メモ欄に貼る計画書パス**: `docs/tasks/client-bank-7-account-info-edit-android.md`
- **依存タスク**: client-bank-4 (Android アカウント設定) 完了
- **後続タスク**: iOS 版アカウント情報変更（client-bank-5 iOS 完了後に別タスク化、shared `AccountProfileLocalEditor` を再利用）/ 実 API 連携タスク
- **PR 構成**: 1 本（Android + 共通 `:shared` 基盤拡張）
