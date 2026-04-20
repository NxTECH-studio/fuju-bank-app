# T2-4: ArtifactDto

## 概要

`GET /artifacts/:id` のレスポンス DTO を定義する。

## 背景・目的

通知や取引履歴から Artifact の詳細（タイトル、作家、サムネイル URL 等）を引くための型。

## 影響範囲

- モジュール: shared
- ソースセット: commonMain
- 破壊的変更: なし
- 追加依存: なし

## 実装ステップ

1. `shared/src/commonMain/.../data/remote/dto/ArtifactDto.kt`:
   - `@Serializable data class ArtifactResponse(val id: String, val title: String, @SerialName("creator_user_id") val creatorUserId: String, @SerialName("thumbnail_url") val thumbnailUrl: String?, ...)`
2. `commonTest` にデコードテストを追加。

## 検証

- [ ] `./gradlew :shared:allTests`

## 依存

- T0-1, T0-2, T1-3

## 技術的な補足

- backend 側 Artifact モデルの正確なフィールドは Rails 側を確認。未確定フィールドは nullable で受けておく。
