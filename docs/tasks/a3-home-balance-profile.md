# A3: ホーム画面（残高 / プロフィール / ボトムナビ）

## メタ情報

- **Phase**: 2
- **並行起動**: ✅ A4 / A6 と並列可能（同じ Home コンポーネントに後で合流）
- **依存**: A2b（SessionStore.Authenticated）
- **同期点**: なし
- **Figma**:
  - ホーム v2 採用: https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=89-12356&m=dev
  - ホーム v1 (参考): https://www.figma.com/design/bzm13wVWQmgaFFmlEbJZ3k/NxTECH?node-id=99-19378&m=dev

## 概要

ログイン後の最初に表示するホーム画面。fujupay ブランドのバーコード／QR カードと残高、4 アクション（取引履歴 / 送る・もらう / スキャン / チャージ）、3 タブのボトムナビ（ホーム / 支払い / アカウント）を提供する。**A3 のスコープはホーム画面 + ボトムナビ（シェル）まで**。アカウント画面・支払い画面は別タスクで扱う。

## スコープ

| 含む | 含まない |
| :--- | :--- |
| ホーム画面（ヘッダー / バーコード＋QR＋残高カード / 4 アクション） | アカウント画面（Figma `100:19982`）→ 別タスク |
| ボトムナビ（ホーム / 中央 FAB / アカウント）の **シェル** | 支払い完了画面（Figma `129:23777`）→ A5/A7 側 |
| 残高マスク／表示トグル | スキャン本体（A5 にぶら下がる想定） |
| 取引履歴・送る/もらうへの遷移エントリ | チャージ機能 |

## プロフィールデータの所在

AuthCore と bank で持つ user データは別物：

| データ | 所在 | エンドポイント |
| :--- | :--- | :--- |
| `id`(ULID) / `email` / `public_id` / `icon_url` / `mfa_enabled` | **AuthCore** | `GET /v1/user/profile` (Bearer access) |
| `external_user_id`(=AuthCore sub) / `name` / `public_key` / `balance_fuju` | **bank** | `GET /users/me` (Bearer access) |

shared 側は既に実装済み（前回作業で commit 前の状態）：

- `shared/.../data/remote/api/AuthCoreUserApi.kt`
- `shared/.../data/remote/dto/AuthCoreUserDto.kt`
- `shared/.../data/repository/ProfileRepository.kt`（並列 fetch + merge）
- `shared/.../domain/model/UserProfile.kt`

A3 では **これらを Koin モジュールに登録し、UI から呼ぶ** ところからスタートする。

## UI 仕様（v2 / `89:12356`）

### 画面構成（上から）

1. **iOS ステータスバー**（SwiftUI 標準）
2. **ヘッダー** （横並び・背景 `#F6F7F9`）
   - 左: 48×48 の空スロット（Figma も空。何も置かない）
   - 中央: `fujupay` ロゴ（fuju キャラクター + テキスト）。assets として SVG/PNG を `composeApp/composeResources` と `iosApp/Assets.xcassets` に配置
   - 右: 48×48 の通知ベルアイコン（赤ドット付き）。**A3 ではタップでトーストのみ**
3. **バーコード/QR/残高カード** （白背景 / `rounded-[32px]` / `drop-shadow`）
   - 上段: バーコード（高さ 約 63px / 幅 312px）。**`public_id` を Code128 等でレンダリング**
   - 下段: 左に QR（66px 角）+ 右にラベル「現在の残高」「`xxx,xxx,xxx` 円」「表示」ボタン
   - QR は `public_id` を内容として動的生成
4. **見出し**: 「取引メニュー」（12px Bold）
5. **4 アクションボタン**（横並び・各々白背景丸ボタン）
   - 取引履歴（紫）→ A4 ルートへ遷移
   - 送る・もらう（緑）→ A5 ルートへ遷移
   - スキャン（マゼンタ）→ A3 ではトースト「実装中」
   - チャージ（青）→ A3 ではトースト「実装中」
6. **ボトムナビ** （Figma `43:258`）
   - 左タブ「ホーム」（A3 で実装）
   - 中央 FAB「支払い」（マゼンタ円形 / 上にせり出す）→ A3 ではトーストのみ
   - 右タブ「アカウント」（**A3 ではタブの枠だけ。中身は Coming Soon プレースホルダー**）

### 残高マスクトグル

- 初期状態: マスク（`--,---,---,---`）
- 「表示」ボタンタップ: 実際の残高を表示
- 再タップ: マスクに戻る
- 値は ViewModel が `revealed: Boolean` を保持。formatter は数値 → カンマ区切りに整形（commonMain で `formatBalance(Long): String` を用意）
- 値の取得自体は起動時に 1 回 fetch 済み（マスクは UI 表示の問題）

### カラートークン（Figma 抽出）

| 用途 | 値 |
| :--- | :--- |
| 背景 | `#F6F7F9` |
| カード／タブ背景 | `#FFFFFF` |
| 主要テキスト | `#111111` |
| 補助テキスト | `#4B4C50` / `#B0B0B0` |
| アクセント（マゼンタ／支払い・スキャン・通知ドット） | `#FF1E9E` |
| 取引履歴 | `#9E1EFF` |
| 送る・もらう | `#0CD80C` |
| チャージ | `#1E83FF` |

`composeApp` 側は Material3 の ColorScheme を使わず、**専用の `FujupayColors` オブジェクト**で持つ（Material のセマンティクスと衝突するため）。

## 影響範囲

### 新規依存（`gradle/libs.versions.toml`）

QR コード描画用ライブラリを追加：

- **Android (Compose)**: `io.github.alexzhirkevich:qrose:1.0.1`（commonMain 対応・SVG/Painter 出力）
- **iOS (SwiftUI)**: `CoreImage` の `CIFilter.qrCodeGenerator()` を使用（追加依存なし）

> 同じ public_id を両 OS で同一見た目にするため、`L` レベル / margin: 0 で揃える。
> バーコード（Code128）も同様に：Android は `qrose` 系または別軽量ライブラリ、iOS は `CICode128BarcodeGenerator`。Android 側に Code128 描画ライブラリが無ければ **MVP ではバーコード部分はダミー画像 + TODO** とする（レビューで判断）。

### shared

- 既存（追加実装不要、Koin 登録のみ）:
  - `data/remote/api/AuthCoreUserApi.kt`
  - `data/remote/dto/AuthCoreUserDto.kt`
  - `data/repository/ProfileRepository.kt`
  - `domain/model/UserProfile.kt`
- 追加:
  - `shared/.../util/BalanceFormatter.kt`（`fun formatBalanceFuju(value: Long): String` / `formatMasked(): String`）
  - Koin モジュールに `AuthCoreUserApi` / `ProfileRepository` を追加（DI 経由で `authCoreBaseUrl` を渡す）

### iOS 新規

- `iosApp/iosApp/Features/Home/HomeView.swift`
- `iosApp/iosApp/Features/Home/HomeViewModel.swift`（`@MainActor`、`UserProfile` を保持）
- `iosApp/iosApp/Features/Home/Components/`
  - `BalanceCardView.swift`（バーコード / QR / 残高 / 表示トグル）
  - `ActionTilesView.swift`（4 アクション）
  - `FujupayHeaderView.swift`（ロゴ + 通知ベル）
  - `QRCodeImage.swift` / `Code128BarcodeImage.swift`（CIFilter ラッパ）
- `iosApp/iosApp/Features/Shell/RootTabView.swift`
  - `TabView` (selection bind) + 中央 FAB を `ZStack` でオーバーレイ
  - タブ: Home / (中央 spacer) / Account
  - 中央 FAB は `Button` + 影付き、タップで `Toast`（既存トーストヘルパが無ければ作る）
- `iosApp/iosApp/Features/Account/AccountPlaceholderView.swift`（"準備中"）
- 既存 `iosApp/iosApp/AuthFlow.swift`（A2b）から、`Authenticated` 時の遷移先を `HomeView` → `RootTabView` に差し替え

### Android 新規

- `composeApp/.../features/home/HomeRoute.kt`（NavHost 用 entry）
- `composeApp/.../features/home/HomeScreen.kt`
- `composeApp/.../features/home/HomeViewModel.kt`（`viewModelScope` で `ProfileRepository.getMyProfile()`）
- `composeApp/.../features/home/components/`
  - `BalanceCard.kt`
  - `ActionTiles.kt`
  - `FujupayHeader.kt`
  - `QrCodeImage.kt`（qrose）
  - `BarcodeImage.kt`（dummy or 軽量ライブラリ）
- `composeApp/.../features/shell/RootScaffold.kt`
  - `Scaffold` の `bottomBar` に `NavigationBar`（Home / spacer / Account）
  - `Box` で `NavigationBar` の上に中央 FAB をオーバーレイ
  - `NavHost` で `home` / `account` ルートを切り替え
- `composeApp/.../features/account/AccountPlaceholderScreen.kt`
- `composeApp/.../theme/FujupayColors.kt`
- `composeApp/.../navigation/RootDestination.kt`（`Home` / `Account` / `TransactionHistory` / `Send` ルート）

`MainActivity` から既存の `App()` Composable を `RootScaffold` 経由で立ち上げるように差し替え（A2b の `SessionState` 監視は維持）。

### Koin / DI

- `shared/.../di/DataModule.kt` に
  - `AuthCoreUserApi(httpClient, BuildKonfig.AUTHCORE_BASE_URL)`
  - `ProfileRepository(authCoreUserApi, userMeApi)`
- BuildKonfig の `AUTHCORE_BASE_URL` が未設定なら追加（A2b で既設の可能性あり、要確認）。

## 実装ステップ

1. **準備**: `gradle/libs.versions.toml` に `qrose`（commonMain）を追加し、`shared/build.gradle.kts` の commonMain dependencies に登録。BuildKonfig に `AUTHCORE_BASE_URL` が無ければ追加。
2. **DI 登録**: `AuthCoreUserApi` / `ProfileRepository` を Koin モジュールに登録。`HomeViewModel` も Koin で解決できるようにする。
3. **shared util**: `BalanceFormatter.kt` を `commonMain` に追加（`formatBalanceFuju(Long)` / マスク文字列定数）。
4. **アセット戦略**（再現性とコストのバランス重視）:
   - **Figma MCP から直接ダウンロード**（独自意匠で標準アイコンに無いもの）:
     - `fujupay` ロゴ（中央ヘッダー）→ `composeApp/composeResources/drawable/ic_fujupay_logo.xml`（SVG → VectorDrawable に変換） / `iosApp/Assets.xcassets/FujupayLogo.imageset/`（SVG をそのまま、または PDF に変換）
     - 中央 FAB の QR 風アイコン → 同様に `ic_fujupay_pay.xml` / `FujupayPay.imageset`
     - バーコードのプレースホルダー画像 → MVP 段階では Figma の SVG をそのまま静的アセットとして配置（後でライブラリに差し替え）
   - **Material Symbols / SF Symbols で代用**（標準で十分再現できるもの）:
     - 通知ベル: Material `Icons.Outlined.Notifications` / SF `bell`
     - ホームタブ: Material `Icons.Filled.Home` / SF `house.fill`
     - アカウントタブ: Material `Icons.Outlined.AccountCircle` / SF `person.circle`
     - 取引履歴: Material `Icons.Outlined.History` / SF `clock.arrow.circlepath`
     - 送る・もらう: Material `Icons.Outlined.Send` / SF `paperplane`
     - スキャン: Material `Icons.Outlined.QrCodeScanner` / SF `qrcode.viewfinder`
     - チャージ: Material `Icons.Outlined.AddCircleOutline` / SF `plus.circle`
   - **色トークン**: Figma から抽出した値で `FujupayColors`（commonMain or 各プラットフォーム）を定義。標準アイコンには `tint`（Compose）/ `foregroundStyle`（SwiftUI）でカテゴリ色を当てる。
   - 取得手順: `curl http://localhost:3845/assets/<hash>.svg -o <path>`（Figma デスクトップ起動中に実行）。SVG → Android VectorDrawable は Android Studio の "New > Vector Asset" もしくは `svg2vector` で変換。iOS は SVG / PDF をそのまま `Assets.xcassets` に追加（Xcode 14+）。
5. **iOS UI**:
   - `RootTabView` のシェル（タブ + 中央 FAB）
   - `HomeView` + 子コンポーネント（QR は `CIFilter`）
   - `HomeViewModel` で `ProfileRepository.getMyProfile()` を呼び `UserProfile` を保持。`revealed` トグル。`refresh()` 関数。
   - 4 アクション・支払い FAB・通知ベル・スキャン/チャージのタップ動作（遷移 / トースト）
   - `Authenticated` ルートを `HomeView` 単体から `RootTabView` に差し替え
6. **Android UI**:
   - `RootScaffold` シェル（NavigationBar + 中央 FAB）
   - `HomeScreen` + 子 Composable（QR は `qrose`）
   - `HomeViewModel`（`StateFlow<HomeUiState>`）。`refresh()` で再取得。`PullToRefreshBox`。
   - Navigation Compose で `home` / `account` / `transactionHistory` / `send` を定義（後者 2 つは A4/A5 で実装）
7. **A6 リアルタイム HUD との合流ポイント（コメントだけ残す）**:
   - HomeViewModel に `realtimeRepository.events` を collect する slot を確保しておくが、A3 では未配線。

## 検証チェックリスト

- [ ] `./gradlew :shared:build` が通る
- [ ] `./gradlew :composeApp:assembleDebug` が通る
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` が通る
- [ ] iOS シミュレータ（iPhone 15）で起動 → ログイン → ホーム画面が Figma `89:12356` に概ね一致
- [ ] Android エミュレータで起動 → ログイン → ホーム画面が Figma に概ね一致（OS 標準のシステム UI 差は許容）
- [ ] 残高マスクの初期表示と「表示」トグルが両 OS で動く
- [ ] 取引履歴ボタン → A4 ルートに `pushNavigation`（A4 未実装ならプレースホルダー画面でも可）
- [ ] 送る・もらうボタン → A5 ルートに `pushNavigation`
- [ ] スキャン / チャージ / 通知ベル / 中央「支払い」FAB をタップしてトーストが出る
- [ ] ボトムナビ「アカウント」タブをタップして Coming Soon プレースホルダーが出る
- [ ] 401 時に `SessionStore.clear()` → ログイン画面へ（既存 A2b の挙動を踏襲）
- [ ] ネットワーク失敗時にエラーステートが描画され、再試行できる
- [ ] QR コードに含まれる文字列が `public_id` と一致（QR リーダで読んで確認）

## 技術的な補足

- **QR / バーコードの抽象化**: ホーム画面のレベルでは expect/actual を導入せず、両 OS でそれぞれ native 実装する（`qrose` は Compose 専用、CIFilter は SwiftUI 専用のため）。`shared` には抽象を作らず、`public_id` を渡すだけ。
- **HomeViewModel の状態**: `sealed interface HomeUiState { Loading, Loaded(profile, revealed), Error(throwable) }` を `commonMain` に置き、両 OS の VM から共有する手もあるが、iOS は `@MainActor` で薄く SwiftUI 用 ObservableObject を別途用意するほうが扱いやすい。MVP では **iOS / Android で別 VM**、状態モデル（`HomeUiState`）だけ commonMain で共有。
- **ボトムナビ中央 FAB のオーバーレイ**:
  - iOS: `ZStack` の最前面に配置し、`safeAreaInset(edge: .bottom)` でタブとの高さ調整。
  - Android: `Scaffold(bottomBar = ...)` の bottomBar コンテナを `Box` で組み、内部に `NavigationBar` + 上に `FloatingActionButton` を `Modifier.align(TopCenter).offset(y = (-13).dp)`。
- **A4 / A5 との関係**: ナビゲーション先のルート名と引数だけ A3 で確定（`transactionHistory`, `send`）。それぞれの画面実装は別タスク。A3 では **Coming Soon プレースホルダーで遷移先を埋めて遷移自体は確認可能** にする。
- **テストカバレッジ**: VM レベルの単体テスト（マスクトグル / refresh 状態遷移）は commonTest で書く。UI スナップショットは MVP 範囲外。

## 後続タスクの予告（このタスクでは扱わない）

- **A3b**: アカウントタブ画面（Figma `100:19982`）
- **A5**: 送金 / スキャン本体 + 支払い完了（Figma `129:23777`）
- **チャージ機能**: 別 epic
