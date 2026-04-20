# T3-3: ArtifactApi

## 概要

`GET /artifacts/:id` を呼ぶ API クライアントを実装する。

## 背景・目的

通知・取引履歴から Artifact 詳細を引くために必要。単独エンドポイントなので小さく切る。

## 影響範囲

- モジュール: shared
- ソースセット: commonMain
- 破壊的変更: なし
- 追加依存: なし

## 実装ステップ

1. `shared/src/commonMain/.../data/remote/api/ArtifactApi.kt`:
   - `class ArtifactApi(private val client: HttpClient)`
   - `suspend fun get(artifactId: String): NetworkResult<ArtifactResponse>`
2. `commonTest` に MockEngine テストを追加（正常系 + 404）。

## 検証

- [ ] `./gradlew :shared:allTests`

## 依存

- T1-1, T1-3, T2-4

## 技術的な補足

- Repository 側（T4-5）で `Flow<ArtifactResponse>` のキャッシュ付きラッパーを提供する想定だが、本タスクはキャッシュなしの直読みまで。
