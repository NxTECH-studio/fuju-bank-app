# T4-1: AuthRepository + authModule

## 概要

`AuthApi`（T3-1）と `TokenStorage`（T1-2）を組み合わせてログイン / ログアウト / トークンリフレッシュを司る Repository と、対応する Koin module を追加する。

## 背景・目的

UI 層から「ログインできた / できなかった」「現在認証済みか」「MFA が必要になった」を扱える抽象を提供する。

## 影響範囲

- モジュール: shared
- ソースセット: commonMain
- 破壊的変更: なし
- 追加依存: なし

## 実装ステップ

1. `shared/src/commonMain/.../data/repository/AuthRepository.kt`:
   - `class AuthRepository(private val authApi: AuthApi, private val tokenStorage: TokenStorage)`
   - `suspend fun login(email: String, password: String): NetworkResult<Unit>` — 成功時に `tokenStorage.save(...)`、MFA_REQUIRED 時は `mfaRequiredEvents.emit(Unit)` して失敗として返す。
   - `suspend fun logout()` — `tokenStorage.clear()`
   - `suspend fun isAuthenticated(): Boolean` — `tokenStorage.getAccessToken() != null`
   - `val mfaRequiredEvents: SharedFlow<Unit>` — MFA_REQUIRED 発火用
2. `shared/src/commonMain/.../di/AuthModule.kt`:
   - `val authModule = module { single { TokenStorageFactory(get()).create() }; single { AuthApi(get(), AUTHCORE_BASE_URL) }; single { AuthRepository(get(), get()) } }`
3. `commonTest` に MockEngine + 偽 TokenStorage でのユニットテストを追加。

## 検証

- [ ] `./gradlew :shared:allTests`

## 依存

- T3-1, T1-2

## 技術的な補足

- `AUTHCORE_BASE_URL` は BuildConfig 的な仕組みが無いため、commonMain の `object NetworkConstants` に暫定ハードコード。env 切替は後続タスクで。
- `mfaRequiredEvents` は `MutableSharedFlow(replay = 0, extraBufferCapacity = 1)`。
