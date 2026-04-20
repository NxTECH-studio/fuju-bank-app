# T3-1: AuthApi（AuthCore login / refresh）

## 概要

AuthCore の login / refresh エンドポイントを呼び出す API クライアントを実装する。`HttpClient` の `Auth { bearer { refreshTokens { ... } } }` プラグインと連携する refresh ロジックの実体もここで提供する。

## 背景・目的

全 API の認証前提を成立させる。refresh 時の 401 無限ループ防止、MFA_REQUIRED 時の早期返しなどをこのクラスに閉じ込める。

## 影響範囲

- モジュール: shared
- ソースセット: commonMain
- 破壊的変更: なし
- 追加依存: なし

## 実装ステップ

1. `shared/src/commonMain/.../data/remote/api/AuthApi.kt`:
   - `class AuthApi(private val client: HttpClient, private val authCoreBaseUrl: String)`
   - `suspend fun login(email: String, password: String): NetworkResult<TokenResponse>`
   - `suspend fun refresh(refreshToken: String): NetworkResult<TokenResponse>`
   - 実装は `runCatchingNetwork { client.post("$authCoreBaseUrl/sessions") { setBody(LoginRequest(...)) }.body() }` 形式。
2. `HttpClientFactory` の Auth プラグインで使う `refreshTokens` ブロック用の補助関数を `network/` に追加（`AuthApi` を参照するので T1-1 からの逆依存を避けるため、関数インターフェースで受け渡す）。
3. `commonTest` に MockEngine を使ったユニットテストを追加（login 成功 / 401 エラー / MFA_REQUIRED の 3 パターン）。

## 検証

- [ ] `./gradlew :shared:allTests`
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`

## 依存

- T1-1, T1-2, T1-3, T2-1

## 技術的な補足

- AuthCore のベース URL は銀行 API と別ホストの可能性があるため、`AuthApi` 専用に baseUrl を受ける（`HttpClient` の defaultRequest ではなく明示指定）。
- refresh のリトライは Ktor の `Auth` プラグイン任せにできるが、MFA_REQUIRED は refresh では解決しないためプラグイン側で再試行ループに入らないようにする（エラーコードで早期 return）。
- commonTest の MockEngine は `io.ktor:ktor-client-mock` の追加依存が必要な場合 T0-1 にフィードバックする（本タスクでは追加しない）。
