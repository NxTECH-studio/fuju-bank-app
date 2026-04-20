# T4-5: ArtifactRepository + artifactModule

## 概要

`ArtifactApi`（T3-3）をラップし、`Artifact` ドメインモデルへ変換する Repository を追加する。

## 背景・目的

通知・取引履歴から Artifact 詳細を引くための境界。将来的な in-memory キャッシュ追加の足場にする。

## 影響範囲

- モジュール: shared
- ソースセット: commonMain
- 破壊的変更: なし
- 追加依存: なし

## 実装ステップ

1. `shared/src/commonMain/.../domain/model/Artifact.kt`:
   - `data class Artifact(val id: String, val title: String, val creatorUserId: String, val thumbnailUrl: String?)`
2. `shared/src/commonMain/.../data/repository/ArtifactRepository.kt`:
   - `class ArtifactRepository(private val artifactApi: ArtifactApi)`
   - `suspend fun get(artifactId: String): NetworkResult<Artifact>`
3. `shared/src/commonMain/.../di/ArtifactModule.kt`:
   - `val artifactModule = module { single { ArtifactApi(get()) }; single { ArtifactRepository(get()) } }`
4. `commonTest` に変換テストを追加。

## 検証

- [ ] `./gradlew :shared:allTests`

## 依存

- T3-3

## 技術的な補足

- キャッシュなしの直読みまでで OK。キャッシュは別タスクで設計する。
