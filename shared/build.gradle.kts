import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.BOOLEAN
import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.buildKonfig)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    
    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            // domain model (`MintMetadata.targetDate: LocalDate`) で公開しているため、
            // composeApp 等の consumer 側でも型を解決できるよう api スコープで露出する。
            api(libs.kotlinx.datetime)
            implementation(libs.koin.core)
            implementation(libs.multiplatform.settings.no.arg)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.multiplatform.settings.test)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.koin.android)
            implementation(libs.androidx.security.crypto)
        }
        androidUnitTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlin.testJunit)
            implementation(libs.koin.test)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        iosTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "studio.nxtech.fujubank.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}

// Android `assembleRelease` / `bundleRelease` や iOS の Release framework リンク等の
// release 系タスクが起動された場合は BuildKonfig の flavor も release に強制する。
// 現状 default / release で URL は同じ本番値だが、release では `USE_DUMMY_PROFILE=false`
// が強制されるため、`-Pbuildkonfig.flavor=release` の付け忘れで本番 AAB / iOS Framework
// にダミープロフィールが混入する事故を防ぐ目的で残している。BuildKonfig は
// `project.findProperty("buildkonfig.flavor")` で値を読むため、extra プロパティでも
// `-P` と同じ経路で拾われる。
//
// Xcode から起動される `embedAndSignAppleFrameworkForXcode` は内部で $CONFIGURATION を
// 読んで linkDebug / linkRelease にディスパッチするが、トップレベル task 名には
// "Release" を含まないため task 名だけ見ても release を判別できない。よって
// CONFIGURATION env var も併せて見る。これで Xcode の Scheme で Release を選ぶだけで
// release flavor が発火するようになる（Android Studio は Build Variants の release が
// `assembleRelease` を呼ぶので task 名側で拾える）。
if (!project.hasProperty("buildkonfig.flavor")) {
    val taskNames = gradle.startParameter.taskNames
    val xcodeRelease = System.getenv("CONFIGURATION") == "Release" &&
        taskNames.any { it.contains("embedAndSignAppleFramework") }
    val triggersRelease = xcodeRelease || taskNames.any { name ->
        val isAndroidRelease = name.contains("Release") &&
            !name.contains("UnitTest") &&
            !name.contains("AndroidTest")
        val isIosReleaseLink = name.startsWith("linkRelease") ||
            name.contains("ReleaseFrameworkIos")
        isAndroidRelease || isIosReleaseLink
    }
    if (triggersRelease) {
        extra["buildkonfig.flavor"] = "release"
    }
}

// `local.properties` の `useDummyProfile=true` で UI 確認用ダミー UserProfile に切替える。
// バックエンド未起動でも HomeScreen を起動できるようにするデバッグスイッチ。
// Release flavor では強制 false（本番ビルドへの混入防止）。
val useDummyProfile: Boolean = run {
    val file = rootProject.file("local.properties")
    if (!file.exists()) return@run false
    Properties().apply { file.inputStream().use(::load) }
        .getProperty("useDummyProfile")?.toBooleanStrictOrNull() ?: false
}

buildkonfig {
    packageName = "studio.nxtech.fujubank"
    objectName = "BuildKonfig"

    // MVP 段階ではローカルバックエンド検証を行わず、debug / release とも本番 API (`*.fujupay.app`)
    // を向ける。UI 単体確認は `local.properties` の `useDummyProfile=true` で行う。
    defaultConfigs {
        buildConfigField(STRING, "BANK_API_BASE_URL", "https://api.fujupay.app")
        buildConfigField(STRING, "CABLE_URL", "wss://api.fujupay.app/cable")
        buildConfigField(STRING, "AUTHCORE_BASE_URL", "https://auth.fujupay.app")
        buildConfigField(BOOLEAN, "USE_DUMMY_PROFILE", useDummyProfile.toString())
    }

    // Release flavor では `useDummyProfile` を強制 false にし、本番ビルドへのダミー混入を防ぐ。
    // URL は default と同じだが、フラグ上書きのため全フィールドを再宣言する。
    defaultConfigs("release") {
        buildConfigField(STRING, "BANK_API_BASE_URL", "https://api.fujupay.app")
        buildConfigField(STRING, "CABLE_URL", "wss://api.fujupay.app/cable")
        buildConfigField(STRING, "AUTHCORE_BASE_URL", "https://auth.fujupay.app")
        buildConfigField(BOOLEAN, "USE_DUMMY_PROFILE", "false")
    }
}
