# A7: Artifact 投稿画面

## メタ情報

- **Phase**: 3
- **並行起動**: ✅ A3〜A6 と並列可能
- **依存**: A2b（SessionStore.Authenticated）
- **同期点**: なし

## 概要

「感情を担保とする」artifact を投稿し mint をトリガする UI。アプリのコア機能の片翼。MVP はテキスト + メタデータのみ、画像・添付は将来。

## 影響範囲

- iOS 新規:
  - `iosApp/iosApp/Features/Artifact/PostArtifactView.swift`
  - `iosApp/iosApp/Features/Artifact/PostArtifactViewModel.swift`
- Android 新規:
  - `composeApp/.../features/artifact/PostArtifactScreen.kt`
  - `composeApp/.../features/artifact/PostArtifactViewModel.kt`
- shared:
  - 既存 `ArtifactRepository.create(...)` を利用

## 実装ステップ

1. **入力 state**:
   - `body: String`（自由記述、文字数制限は backend 仕様に合わせる）
   - `metadata: Map<String, String>?`（任意。MVP は省略可）

2. **submit**:
   - `artifactRepository.create(body, metadata)` を呼ぶ
   - 成功時: 結果（artifact_id / mint された fuju 額）を Snackbar 表示 → A3 ホームに戻る → 残高再取得（A6 経由でも反映される）

3. **UI**:
   - iOS / Android ともに `TextEditor` / `OutlinedTextField` で長文入力
   - 投稿ボタン（送信中 disabled）
   - 文字数カウンター

4. **エラーハンドリング**:
   - `ARTIFACT_VALIDATION_FAILED`（仮） / `RATE_LIMIT_EXCEEDED` を表示

5. **HomeView からの導線**:
   - A3 ホームに「artifact 投稿」FAB or リンクを追加

## 検証チェックリスト

- [ ] 投稿成功で artifact が作成される
- [ ] 結果として mint され、残高が増えること（A6 経由で反映）
- [ ] バリデーションエラーが UI に出る
- [ ] 投稿中の二重 submit 防止
