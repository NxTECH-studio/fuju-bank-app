# T1-1: HttpClientFactory（expect / actual）

## 概要

Ktor `HttpClient` の生成を expect/actual で抽象化し、Android では OkHttp engine、iOS では Darwin engine を採用する。JSON / Logging / Timeout / Auth ヘッダ注入の共通プラグイン設定もここに集約する。

## 背景・目的

API クライアント（T3-*）が共通の HttpClient を使えるようにする基盤。以降のタスクは `HttpClient` を DI 経由で受け取るだけで済むようにする。

## 影響範囲

- モジュール: shared
- ソースセット: commonMain / androidMain / iosMain
- 破壊的変更: なし
- 追加依存: なし（T0-1 で完了済み）

## 実装ステップ

1. `shared/src/commonMain/kotlin/com/example/fuju_bank_app/network/HttpClientFactory.kt`（expect）:
   - `expect fun createHttpClient(config: HttpClientConfig): HttpClient`（もしくは `expect class HttpClientEngineFactory` 的な最小 expect）
   - `data class HttpClientConfig(val baseUrl: String, val enableLogging: Boolean, val authTokenProvider: suspend () -> String?)`
   - `fun HttpClientConfig.install(client: HttpClient)` でプラグインを install する共通関数も commonMain に用意。
2. `shared/src/androidMain/kotlin/com/example/fuju_bank_app/network/HttpClientFactory.android.kt`（actual）:
   - `actual fun createHttpClient(config: HttpClientConfig): HttpClient = HttpClient(OkHttp) { ... }`
3. `shared/src/iosMain/kotlin/com/example/fuju_bank_app/network/HttpClientFactory.ios.kt`（actual）:
   - `actual fun createHttpClient(config: HttpClientConfig): HttpClient = HttpClient(Darwin) { ... }`
4. 共通 install 内容:
   - `ContentNegotiation` + `Json { ignoreUnknownKeys = true; explicitNulls = false }`
   - `Logging`（level = HEADERS、BODY はデバッグビルドのみ）
   - `HttpTimeout`（request/connect/socket）
   - `defaultRequest { url(baseUrl); headers.append("Accept", "application/json") }`
   - `Auth { bearer { loadTokens { ... config.authTokenProvider() ... } } }`
5. `network/NetworkMarker.kt`（T0-2 で作成した marker）は削除。

## 検証

- [ ] `./gradlew :shared:build`
- [ ] `./gradlew :shared:allTests`
- [ ] `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`

## 依存

- T0-1, T0-2

## 技術的な補足

- `authTokenProvider` は T1-2 の `TokenStorage` / T4-1 の `AuthRepository` を想定した suspending lambda。T1-1 段階ではインターフェースだけ露出し、実値は後続タスクで注入。
- `HttpTimeout` は request=30s / connect=10s / socket=30s 程度が KMP では無難。
- デバッグ時のみ body ログを出すため、`enableLogging` は build flavor ではなく `HttpClientConfig` のフラグで切り替える設計にする。
