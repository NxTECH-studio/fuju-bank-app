# KMP 固有のトラブルシューティング

> このファイルはテンプレートです。Kotlin Multiplatform / Compose Multiplatform / Gradle / Xcode 周辺でよく遭遇する問題と対処法を追記していきます。

## よくあるハマりどころ（テンプレ）

### Gradle sync / ビルドが遅い・失敗する

**症状**: `./gradlew build` が途中で失敗 / 進まない / 謎のクラスパスエラー

**対処候補**:

1. `./gradlew --stop` で Gradle デーモンを止める
2. `./gradlew clean` → 再ビルド
3. `~/.gradle/caches/` をクリア（最終手段）
4. `gradle.properties` の `org.gradle.jvmargs` / `kotlin.daemon.jvmargs` のメモリ割当を確認
5. ネットワーク経由の依存解決に失敗していないか（プロキシ / ミラー）

---

### iOS フレームワークのリンクに失敗する

**症状**: `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` 失敗 / Xcode から `Shared` framework が見えない

**対処候補**:

1. `shared/build.gradle.kts` の `baseName` と iosApp 側の framework 参照名が一致しているか
2. `iosApp/` の Xcode プロジェクトの `Build Phases` → `Run Script` で Gradle タスク呼び出しが正しいか
3. ターゲットアーキテクチャ（`iosArm64` / `iosSimulatorArm64` / `iosX64`）が実行環境と合っているか
4. Xcode → File → Packages → `Reset Package Caches`

---

### `expect` / `actual` のミスマッチでコンパイルエラー

**症状**: `Expected function '...' has no actual declaration in module` / `actual declaration has different signature`

**対処候補**:

1. `expect` 側と `actual` 側のシグネチャ（戻り値 / 引数 / 型引数 / visibility / デフォルト引数）を完全一致させる
2. `actual` が対象プラットフォームのソースセット（`androidMain` / `iosMain`）に置かれているか
3. `actual typealias` で十分な場合は関数実装ではなく typealias で済ます

---

### Compose の再コンポーズが過剰 / パフォーマンスが悪い

**症状**: 画面がカクつく / 想定外の頻度で再コンポーズされる

**対処候補**:

1. Layout Inspector / Compose Recomposition Counts で発生箇所を特定
2. ラムダを `remember { { ... } }` で安定化
3. 受け取るデータクラスに `@Stable` / `@Immutable` を付与（不変を保証）
4. `State<T>` を `.value` で読むタイミングが最小スコープになっているか確認
5. `derivedStateOf` で派生状態をメモ化

---

### Swift から Kotlin の suspend 関数が呼べない

**症状**: `shared` の `suspend fun` が Swift 側から直接呼び出せない

**対処候補**:

1. `Flow` に変換してコールバック購読に変える（`collect` をラップ）
2. `KMP-NativeCoroutines` / `SKIE` などのラッパーを導入
3. Kotlin 側で callback ベースのファサードを用意する

---

<!-- 以降、プロジェクト固有のエントリを追加 -->
