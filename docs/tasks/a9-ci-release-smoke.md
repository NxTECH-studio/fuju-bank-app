# A9: CI で release build スモーク

## メタ情報

- **Phase**: 3
- **並行起動**: ✅ いつでも単独着手可能（A1〜A8 と完全並列）
- **依存**: なし（A1 が先に merge されていると release 値検証がしっかりできる）
- **同期点**: なし

## 概要

Release flavor で本番 URL が確実に埋め込まれているかを CI で担保する。`-Pbuildkonfig.flavor=release` 付け忘れリグレッションを防ぐ。

## 影響範囲

- 新規 `.github/workflows/release-smoke.yml`
- 既存 CI (`setup-github-actions-ci.md` で立ち上げたもの) と整合させる

## 実装ステップ

1. **workflow 雛形**:
   ```yaml
   name: release-smoke
   on:
     pull_request:
     push:
       branches: [main, develop]
   jobs:
     android-release:
       runs-on: ubuntu-latest
       steps:
         - uses: actions/checkout@v4
         - uses: actions/setup-java@v4
           with: { distribution: temurin, java-version: 17 }
         - name: assembleRelease (unsigned)
           run: ./gradlew :composeApp:assembleRelease
         - name: assert prod URL embedded
           run: |
             apk=$(find composeApp/build/outputs/apk/release -name '*.apk' | head -1)
             unzip -p "$apk" classes.dex | strings | grep -F 'api.fujupay.app' || (echo "prod URL missing" && exit 1)
     ios-release:
       runs-on: macos-14
       steps:
         - uses: actions/checkout@v4
         - name: link Release framework
           run: ./gradlew :shared:linkReleaseFrameworkIosArm64
         - name: assert prod URL embedded
           run: |
             fw=$(find shared/build/bin/iosArm64/releaseFramework -type f | xargs file | grep 'Mach-O' | awk -F: '{print $1}' | head -1)
             strings "$fw" | grep -F 'api.fujupay.app' || (echo "prod URL missing" && exit 1)
   ```

2. **assert 内容**:
   - `api.fujupay.app` が含まれること
   - `10.0.2.2` / `localhost` が **含まれないこと**（debug 値が混入していないか）

3. **A1 との接続**:
   - A1 が merge 後はさらに `authcore.fujupay.app` の存在もチェック

4. **既存 CI との分離**:
   - test job は既存に任せ、本 workflow は release ビルドだけを扱う

## 検証チェックリスト

- [ ] PR で workflow がトリガされる
- [ ] release ビルドが緑
- [ ] わざと debug URL を埋めて push したら fail する
- [ ] Android / iOS どちらも検証される
