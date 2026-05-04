# 銀行アプリクライアント：アカウント情報編集（表示名 / メール）ボトムシート Android 実装

## 概要

アカウントハブ画面（Figma [697:8394](https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=697-8394)）の「アカウント情報」セクションに **鉛筆アイコン** を追加し、タップで **ボトムシート** を開いて **表示名 / メールアドレス** を編集・保存できるようにする。現在「設定」セクションにある「アカウント情報」行（→ `AccountComingSoonScreen`）は廃止する。

`:shared` 側の `AccountProfileProvider` をインターフェースの拡張で **書き込み可能 + StateFlow 配信** に進化させ、`DummyAccountProfileProvider` を in-memory な可変実装にする。後続の iOS 実装（[`client-bank-11-account-info-edit-ios.md`](./client-bank-11-account-info-edit-ios.md)）が同じ shared API を再利用できる状態で凍結する。

## 背景・目的

### 経緯

- client-bank-4 / 5 でアカウントハブ（`697:8394`）を Android / iOS 双方に MVP 実装した時点では、「アカウント情報」は **読み取り専用の表示** であり、設定セクション内に「アカウント情報」行を置いて `AccountComingSoonScreen("アカウント情報")` への準備中遷移を仮配線していた
- 現在 Figma 上の確定デザインでは、「アカウント情報」セクションのカード見出し横（または各行右端に相当する位置）に **鉛筆アイコン** が追加されており、タップで **ボトムシートが立ち上がりその場で編集する** UI になっている
- 編集対象は「表示名」「メールアドレス」の 2 つで、設定セクション側の「アカウント情報」行は重複するため削除される
- 現状の `AccountProfileProvider` は `current(): AccountProfile` のみを公開する読み取り専用インターフェースで、`DummyAccountProfileProvider` も固定値を返すだけ。編集 UI を載せるために **値が変化することを UI に伝播できる** 構造への進化が必要
- 実 API（プロフィール更新エンドポイント）はまだ存在しない。本タスクでは Provider 側に in-memory state を保持し、`StateFlow` で更新を配信するダミー実装で完結させる

### 目的

- アカウントハブ画面の「アカウント情報」セクションに鉛筆アイコンを追加し、タップで `AccountInfoEditSheet`（ボトムシート）を開く
- ボトムシート上で「表示名」「メールアドレス」を編集し、保存ボタンで Provider に書き戻して画面が即時更新されることを確認できる
- 設定セクションから「アカウント情報」行および `RootDestination.AccountEdit` 配線を削除する
- `AccountProfileProvider` を `current(): AccountProfile` の単発取得から `profile: StateFlow<AccountProfile>` + `update(...)` を持つ書き込み可能インターフェースに進化させ、`DummyAccountProfileProvider` を in-memory 実装にする
- iOS 実装（[`client-bank-11`](./client-bank-11-account-info-edit-ios.md)）で再利用するための shared API を本タスクで凍結する

### Figma 確定デザインの主な仕様（697:8394）

参考スクリーンショット候補: `docs/figma-assets/bank-redesign-697-8394.png`（未取得の場合は本タスク着手時に Figma から書き出して同パスへ保存する。命名規則メモリ参照）。

- 「アカウント情報」セクション（白角丸カード、`rounded-[20]`、薄影）
  - セクション見出し `アカウント情報`（12sp Bold、カード外、左寄せ）の横、もしくはカード右上に **鉛筆アイコン**（既存の `R.drawable.ic_edit_pencil`、18dp、グレー）
  - カード内 2 行: `表示名`（ラベル 12sp Regular `#8E8E93` / 値 14sp Medium）、`メールアドレス`（同）。行間に hairline divider
- ボトムシート（編集 UI）
  - シート上端に短いハンドル（システム既定でも可）
  - タイトル `アカウント情報を編集`（17sp Bold）
  - 入力フィールド 2 つ（`表示名` / `メールアドレス`）。Material3 `OutlinedTextField` を採用
  - 下部に `保存` ボタン（既存ボタンスタイルに合わせる）と、必要なら `キャンセル`（システム戻し / シート外タップで閉じる方針なら省略可）
  - メールアドレスは `KeyboardType.Email`、表示名は `KeyboardType.Text`
  - 簡易バリデーション: 表示名 1 文字以上 / メール `@` を含む。NG の場合は保存ボタンを無効化（実 API 接続前なので緩めで OK）
- 「設定」セクションは「通知 / プライバシー設定」の **2 行構成に縮退**（既存の「アカウント情報」行を削除）

## スコープ

- **`:shared` 側の進化**（commonMain）
  - `AccountProfileProvider` を以下の API に進化:
    - `val profile: StateFlow<AccountProfile>`（現行 `current()` を置換 / もしくは `current()` を維持しつつ `profile` を追加）
    - `fun updateProfile(displayName: String, email: String)`
  - `DummyAccountProfileProvider` を in-memory state（`MutableStateFlow<AccountProfile>`）持ちに変更し、`updateProfile(...)` で書き戻し可能にする
  - 既存呼び出し元（`AccountHubViewModel` / iOS `ObservableAccountHubViewModel`）の置換: `provider.current()` → `provider.profile.value`（初期値取得用）。実際は VM 側で `StateFlow` を直接購読する形に書き換える
  - Koin 登録（`accountModule`）は変更不要（`single<AccountProfileProvider>` のまま）
- **Android 画面実装**
  - `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/account/components/AccountInfoSection.kt`: 鉛筆アイコン追加 + `onEditClick: () -> Unit` パラメータを追加
  - `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/account/components/AccountInfoEditSheet.kt`（新規）: `ModalBottomSheet` ベースの編集 UI。`initialDisplayName` / `initialEmail` / `onSave(displayName, email)` / `onDismiss()` を受け取る
  - `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/account/AccountHubScreen.kt`: シート開閉状態を `rememberSaveable` で持ち、鉛筆タップで開く / 保存・破棄で閉じる
  - `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/account/AccountHubViewModel.kt`: `profile: StateFlow<AccountProfile>` を Provider 直結に変更 + `updateProfile(displayName, email)` を追加
- **配線の削除**
  - `RootScaffold.kt` の `RootDestination.AccountEdit` 分岐 (`AccountComingSoonScreen("アカウント情報")`) を削除
  - `RootDestination.AccountEdit` を `RootDestination` から削除
  - `RootDestinationSaver` の `accountEdit` 分岐を削除
  - `AccountHubScreen` シグネチャから `onNavigateAccountEdit: () -> Unit` を削除
  - `SettingsCard` の rows 配列から「アカウント情報」エントリを削除（→ 通知 / プライバシー設定 の 2 行）

### アウトオブスコープ

- **iOS 実装**: 本タスクで凍結された `AccountProfileProvider` API を参照する形で [`client-bank-11-account-info-edit-ios.md`](./client-bank-11-account-info-edit-ios.md) にて別タスク化
- **「ユーザーID」（Twitter ハンドル相当）行の編集**: ユーザー合意で別タスク扱い
- **プロフィールカード上部の `ID: xxxx`（内部 ID）の編集**: 表示のみ・本タスクでは触らない
- **パスワード変更**: Figma に存在するが別タスク（仮: `client-bank-12-password-change-*`）
- **「情報」セクション（プライバシーポリシー / 利用規約）の本実装**: client-bank-8 / 9 で凍結された `PrivacyContent` を再利用する別タスク扱い
- **実 API 連携（プロフィール更新エンドポイント）**: 実装後に Remote 実装 Provider を追加して Koin の `BuildKonfig.USE_DUMMY_PROFILE` 分岐に載せる別タスク
- **アバター画像変更**: Figma 上にも編集 UI なし。対象外
- **`AccountComingSoonScreen` 自体の削除**: `RootDestination.PrivacySettings` 等で引き続き使われているため残置
- **バリデーションの厳密化（メールフォーマット RFC 準拠 / 文字数上限など）**: 実 API 接続時のサーバー側仕様確定後に検討。本タスクでは「表示名 1 文字以上」「メールに `@` を含む」程度に留める

## 着手条件

- client-bank-4 (`client-bank-4-account-settings-android.md`) / client-bank-5 (`client-bank-5-account-settings-ios.md`) が `main` にマージ済み（→ ✅ 既にマージ済み）
- client-bank-8 / 9（プライバシー設定）の `:shared` 変更とは触る Koin モジュールが同じ (`accountModule`) だが、本タスクは `AccountProfileProvider` の **既存 single 登録のシグネチャを進化させる** だけで `single { ... }` の追加・削除はないため、衝突は限定的（`PrivacyPreferences` と独立）

## 影響範囲

- モジュール: `:shared` / `:composeApp`
  - `:shared/commonMain`:
    - `account/AccountProfileProvider.kt` 改修（インターフェース拡張 + Dummy 実装の in-memory 化）
  - `:composeApp/androidMain`:
    - `features/account/components/AccountInfoSection.kt` 改修（鉛筆アイコン追加）
    - `features/account/components/AccountInfoEditSheet.kt` 新規
    - `features/account/AccountHubScreen.kt` 改修（シート状態保持 + onNavigateAccountEdit 削除）
    - `features/account/AccountHubViewModel.kt` 改修（StateFlow 直結 + updateProfile 追加）
    - `features/shell/RootScaffold.kt` 改修（AccountEdit 分岐削除 / 引数変更）
    - `navigation/RootDestination.kt` 改修（`AccountEdit` 削除）
- 破壊的変更:
  - `AccountProfileProvider.current()` を廃止（または非推奨化）して `profile: StateFlow<AccountProfile>` に置換 → 呼び出し元（Android VM / iOS VM）に追従が必要
  - `RootDestination.AccountEdit` 削除 → 既存 `RootDestinationSaver` の `accountEdit` キー復元時の挙動変更（→ `Account` に降格させる）
  - `AccountHubScreen(...)` から `onNavigateAccountEdit` パラメータを削除
- 追加依存:
  - なし（`androidx.compose.material3.ModalBottomSheet` は既存依存内）

## 技術アプローチ

### `:shared` 側設計

#### `AccountProfileProvider` の進化

```kotlin
interface AccountProfileProvider {
    val profile: StateFlow<AccountProfile>
    fun updateProfile(displayName: String, email: String)
}

class DummyAccountProfileProvider : AccountProfileProvider {
    private val _profile = MutableStateFlow(
        AccountProfile(
            displayName = "山田 花子",
            email = "hanako@example.com",
            accountId = "1293031294904",
        ),
    )
    override val profile: StateFlow<AccountProfile> = _profile.asStateFlow()

    override fun updateProfile(displayName: String, email: String) {
        _profile.value = _profile.value.copy(
            displayName = displayName,
            email = email,
        )
    }
}
```

`current()` メソッドは削除する（呼び出し元は VM のみで影響範囲が限定されているため互換維持の必要性が薄い）。`accountId` は本タスクではユーザーが編集できないため、`updateProfile` のシグネチャには含めない（後で `userHandle` 編集タスクが来たときに `updateUserHandle(value: String)` 等を追加する想定）。

#### Koin 登録

`accountModule` の `single<AccountProfileProvider>` 登録は変更不要。`BuildKonfig.USE_DUMMY_PROFILE` 分岐もそのまま。

### Android 側設計

#### `AccountHubViewModel`

```kotlin
class AccountHubViewModel(
    private val profileProvider: AccountProfileProvider,
) : ViewModel() {
    val profile: StateFlow<AccountProfile> = profileProvider.profile

    fun updateProfile(displayName: String, email: String) {
        profileProvider.updateProfile(displayName, email)
    }
}
```

`MutableStateFlow` の自前保持は廃止（Provider 側の `StateFlow` を直接公開）。

#### `AccountInfoSection`

```kotlin
@Composable
fun AccountInfoSection(
    displayName: String,
    email: String,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 既存のカードレイアウト + 右上に鉛筆 IconButton（18dp、ic_edit_pencil、tint=TextTertiary）
}
```

鉛筆配置は **カード右上のオーバーレイ**（`Box` + `Modifier.align(Alignment.TopEnd)`) を採用。Figma の細かい位置はスクリーンショット確認時に微調整する。タップ領域は最低 36dp 確保する。

#### `AccountInfoEditSheet`

```kotlin
@Composable
fun AccountInfoEditSheet(
    initialDisplayName: String,
    initialEmail: String,
    onSave: (displayName: String, email: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var displayName by remember { mutableStateOf(initialDisplayName) }
    var email by remember { mutableStateOf(initialEmail) }
    val isValid = displayName.isNotBlank() && email.contains("@")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = FujuBankColors.Surface,
    ) {
        // タイトル / OutlinedTextField x2 / 保存ボタン
    }
}
```

`skipPartiallyExpanded = true` にして半開きを禁止（編集 UI は full sheet で集中させたい）。シート外タップ / バックジェスチャで `onDismiss` 起動。保存後はシートを `hide()` してから `onDismiss` を呼ぶ（state 遷移を綺麗にするため `LaunchedEffect` + `coroutineScope` 経由が必要）。

#### `AccountHubScreen`

シート開閉状態は `rememberSaveable { mutableStateOf(false) }` で保持。プロセス再生成時もシートは開きっぱなしにせず、復元後は閉じた状態にして問題ない（編集途中の値の保持はオーバーキル → `false` に降格）。

```kotlin
var showEditSheet by rememberSaveable { mutableStateOf(false) }
val profile by viewModel.profile.collectAsStateWithLifecycle()

AccountInfoSection(
    displayName = profile.displayName,
    email = profile.email,
    onEditClick = { showEditSheet = true },
)

if (showEditSheet) {
    AccountInfoEditSheet(
        initialDisplayName = profile.displayName,
        initialEmail = profile.email,
        onSave = { name, mail ->
            viewModel.updateProfile(name, mail)
            showEditSheet = false
        },
        onDismiss = { showEditSheet = false },
    )
}
```

#### `RootScaffold` / `RootDestination`

- `RootDestination.AccountEdit` を削除
- `RootDestinationSaver` の `accountEdit` キー復元時は `null` を返す（= 復元失敗時 `Account` に降格、`accountFamily` の判定からも除去）
- `AccountHubScreen(viewModel = ..., onNavigateNotifications = ..., onNavigatePrivacy = ...)` の 3 引数に縮退

## 実装手順

1. **`:shared` 改修**
   1. `shared/src/commonMain/kotlin/studio/nxtech/fujubank/account/AccountProfileProvider.kt` を上記設計に書き換え（インターフェース拡張 + Dummy 実装の in-memory 化）
   2. `./gradlew :shared:allTests` 通過
   3. `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` 通過
2. **Android VM 改修**
   1. `AccountHubViewModel.kt` を `StateFlow` 直結 + `updateProfile(...)` 追加に書き換え
3. **コンポーネント実装**
   1. `AccountInfoSection.kt` に鉛筆アイコン + `onEditClick` 追加
   2. `AccountInfoEditSheet.kt` 新規作成（`ModalBottomSheet` + 2 つの `OutlinedTextField` + 保存ボタン）
4. **画面配線**
   1. `AccountHubScreen.kt` にシート開閉状態を `rememberSaveable` で追加し、`AccountInfoSection` の鉛筆 → シート開、保存 → VM `updateProfile` → シート閉じ、を結線
   2. `AccountHubScreen` のシグネチャから `onNavigateAccountEdit` を削除
   3. `SettingsCard` の rows から「アカウント情報」行を削除
5. **`RootScaffold` / `RootDestination` 縮退**
   1. `RootDestination.AccountEdit` 削除
   2. `RootScaffold` の `RootDestination.AccountEdit -> AccountComingSoonScreen(...)` 分岐を削除
   3. `BottomNav` の `accountFamily` 判定から `AccountEdit` を除去
   4. `RootDestinationSaver` の save / restore から `accountEdit` を除去
   5. `AccountHubScreen` 呼び出し箇所から `onNavigateAccountEdit = ...` 引数を削除
6. **Figma スクリーンショット取得**
   1. `697:8394` を `docs/figma-assets/bank-redesign-697-8394.png` として書き出し（既に MVP 時に取得済みなら再利用）。MCP `figma:get_screenshot` を活用
7. **動作確認**
   1. `./gradlew :composeApp:assembleDebug` 通過
   2. アカウントハブの「アカウント情報」セクションに鉛筆アイコンが表示される
   3. 鉛筆タップで編集ボトムシートが立ち上がる
   4. 表示名 / メールを変更して「保存」 → シートが閉じてカードの値が更新される
   5. 編集後にシートを閉じる→再度開くと、保存済みの最新値が初期値として入っている（in-memory 永続）
   6. 設定セクションが「通知 / プライバシー設定」の 2 行に縮退している
   7. システム回転 / プロセス再生成（developer options の Don't keep activities）で開いていたシートが閉じた状態で復元される（クラッシュしない）
8. **PR 作成**: `feature/client-bank-10-account-info-edit-android` → `main`（メモリ: feature ブランチ + PR 必須）

## 完了条件

- [ ] `./gradlew build` が通る
- [ ] `./gradlew :shared:allTests` が通る
- [ ] `./gradlew :composeApp:assembleDebug` が通る
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が通る
- [ ] アカウントハブの「アカウント情報」セクションに鉛筆アイコンが表示される
- [ ] 鉛筆タップでボトムシートが立ち上がり、表示名 / メールアドレスを編集できる
- [ ] 「保存」ボタンで `AccountProfileProvider.updateProfile(...)` が呼ばれ、カード上の表示が即時更新される
- [ ] 設定セクションから「アカウント情報」行が削除され、`RootDestination.AccountEdit` が削除されている
- [ ] iOS ビルド (`./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`) で `AccountProfileProvider` の API 変更が壊さない（後続 client-bank-11 タスクで iOS 側 VM を追従するための土台が用意できている）

## 想定される懸念・リスク

- **`AccountProfileProvider.current()` 廃止に伴う iOS 側のビルド失敗**: `iosApp/iosApp/Features/Account/ObservableAccountHubViewModel.swift` が `KoinIosKt.accountProfileProvider().current()` を呼んでいるため、本タスクで `:shared` を変更すると iOS 側がビルド不可になる。回避策として **`current(): AccountProfile` を `default` メソッドとして残し、`profile.value` を返す互換層を一時的に提供する** ことで、本 PR 単体で iOS ビルドも壊さない構成にする。client-bank-11 のタスクで `current()` を完全削除する。
- **`ModalBottomSheet` のキーボード重なり**: 入力フィールドにフォーカスした際にソフトキーボードがシートと重なる場合がある。`Modifier.imePadding()` をシート内 root に付与して回避する。
- **シート内のフォーム値の保持**: `rememberSaveable` で `displayName` / `email` を保持すれば回転時も値が消えない。バリデーション結果は派生値なので保持不要。
- **保存ボタンの非活性表現**: バリデーション NG 時の見た目が他画面と揃うように、既存ボタンスタイル（`disabled` パラメータ）を尊重。タップ領域は維持してアクセシビリティを下げない。
- **アクセシビリティ**: 鉛筆アイコンは `contentDescription = "アカウント情報を編集"` を必ず付与する（`ProfileCard` の鉛筆と区別するため、文言を専用にする）。
- **`AccountHubScreen` のテスト**: 既に Compose UI test がほぼ無いプロジェクト方針のため、本タスクでも UI test 追加は見送る（手動検証で完了）。将来テスト基盤導入時に追加する。
- **shared API 凍結リスク**: `AccountProfileProvider.profile: StateFlow<AccountProfile>` / `updateProfile(displayName, email)` のシグネチャを後で変更すると iOS タスク（client-bank-11）が追従コストを払う。本タスク内で凍結し、ユーザーID 編集が来たときは **既存メソッドを変えず** に `updateUserHandle(...)` 等を追加する方針。

## 参考リンク

- Figma 確定デザイン（アカウントハブ刷新）: [697:8394](https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=697-8394)
- 後続 iOS タスク: [`client-bank-11-account-info-edit-ios.md`](./client-bank-11-account-info-edit-ios.md)
- 既存 `AccountProfileProvider`: `shared/src/commonMain/kotlin/studio/nxtech/fujubank/account/AccountProfileProvider.kt`
- 既存 `AccountProfile`: `shared/src/commonMain/kotlin/studio/nxtech/fujubank/account/AccountProfile.kt`
- 既存 Koin モジュール: `shared/src/commonMain/kotlin/studio/nxtech/fujubank/di/AccountModule.kt`
- 改修対象 Android 画面: `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/account/AccountHubScreen.kt`
- 改修対象 Android VM: `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/account/AccountHubViewModel.kt`
- 改修対象セクション UI: `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/account/components/AccountInfoSection.kt`
- 改修対象シェル配線: `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/features/shell/RootScaffold.kt`
- 改修対象 Destination: `composeApp/src/androidMain/kotlin/studio/nxtech/fujubank/navigation/RootDestination.kt`
- 既存 ic_edit_pencil ドロワブル: `composeApp/src/androidMain/res/drawable/ic_edit_pencil.xml`（`ProfileCard` で使用中）
- 前提タスク（Android アカウント設定 MVP）: [`client-bank-4-account-settings-android.md`](./client-bank-4-account-settings-android.md)
- 前提タスク（iOS アカウント設定 MVP）: [`client-bank-5-account-settings-ios.md`](./client-bank-5-account-settings-ios.md)
- 関連タスク（プライバシー設定 Android）: [`client-bank-8-privacy-settings-android.md`](./client-bank-8-privacy-settings-android.md)
- 関連タスク（プライバシー設定 iOS）: [`client-bank-9-privacy-settings-ios.md`](./client-bank-9-privacy-settings-ios.md)

---

## Notion タスク登録用サマリ

- **タイトル**: 銀行アプリクライアント：アカウント情報編集（表示名 / メール）ボトムシート Android 実装
- **プレフィックス**: client-bank-10
- **ブランチ命名**: `feature/client-bank-10-account-info-edit-android`
- **メモ欄に貼る計画書パス**: `docs/tasks/client-bank-10-account-info-edit-android.md`
- **依存タスク**: client-bank-4 / 5 完了済み。`:shared/accountModule` を触る他タスク（client-bank-8 / 9 等）とは独立した部分（`AccountProfileProvider` のシグネチャ拡張）を変更するため衝突リスク小。可能なら他の shared 変更タスクと PR を時系列で重ねない
- **後続タスク**: [`client-bank-11-account-info-edit-ios`](./client-bank-11-account-info-edit-ios.md)（shared `AccountProfileProvider` 拡張を再利用 + iOS で同等の編集シート実装）/ ユーザー ID（ハンドル）編集 / パスワード変更 / 実 API 連携（プロフィール更新エンドポイント）
- **PR 構成**: 1 本（Android + 共通 `:shared` の `AccountProfileProvider` 拡張）。`current()` は互換のため一時残置し、client-bank-11 で完全削除
- **参考 Figma**: [アカウントハブ 697:8394](https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=697-8394)
