# A8: iOS Build Configuration / Scheme 分割

## メタ情報

- **Phase**: 3
- **並行起動**: ✅ いつでも単独着手可能（A1〜A7 と完全並列）
- **依存**: なし
- **同期点**: なし

## 概要

KMP 側は `assembleRelease` / `linkRelease*` で BuildKonfig flavor=release が走るが、Xcode 側で Debug / Release の Bundle ID・App Icon・xcconfig を切る運用が未整備。TestFlight 配布前に必ず必要。

## 影響範囲

- ファイル:
  - `iosApp/Configuration/Config.xcconfig`（既存・拡張 or 分割）
  - `iosApp/iosApp.xcodeproj/project.pbxproj`
  - `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/`（Debug 用 overlay icon を追加）
  - 新規 `iosApp/Configuration/Debug.xcconfig` / `Release.xcconfig`（必要なら）

## 実装ステップ

1. **xcconfig 分割**:
   - `Debug.xcconfig` / `Release.xcconfig` を作成
   - `PRODUCT_BUNDLE_IDENTIFIER`:
     - Debug: `studio.nxtech.fujubank.dev`
     - Release: `studio.nxtech.fujubank`
   - `ASSETCATALOG_COMPILER_APPICON_NAME`:
     - Debug: `AppIcon-Dev`
     - Release: `AppIcon`
   - `MARKETING_VERSION` / `CURRENT_PROJECT_VERSION` の取り回しを決める

2. **Xcode project 設定**:
   - Build Configurations の Debug / Release それぞれに上記 xcconfig を割り当て
   - Scheme の Run = Debug / Archive = Release を確認

3. **Debug 用 App Icon**:
   - 既存 AppIcon に「DEV」バッジをオーバーレイした AppIcon-Dev を作成
   - 取り違え事故防止

4. **KMP framework リンク確認**:
   - Debug scheme で `linkDebugFramework*` / Release scheme で `linkReleaseFramework*` が走ることを Build Phase で確認
   - BuildKonfig が Release flavor になっているか strings で確認

5. **Provisioning Profile / Signing**:
   - 自動 signing で Apple Developer Team を設定
   - TestFlight 配布する場合は Distribution Provisioning も用意

## 検証チェックリスト

- [ ] Debug ビルドで Bundle ID = `studio.nxtech.fujubank.dev`
- [ ] Release ビルドで Bundle ID = `studio.nxtech.fujubank`
- [ ] Debug アプリのアイコンに DEV バッジ
- [ ] 同じ端末に Debug と Release を並列インストール可能
- [ ] Release Archive で生成された ipa の strings に `https://api.fujupay.app` が含まれる
