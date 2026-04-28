import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
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
            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.coroutines.test)
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
// これを入れないと `-Pbuildkonfig.flavor=release` の付け忘れで本番 AAB / iOS Framework
// に debug URL (`http://10.0.2.2:3000`) が埋め込まれる事故が起きうる。BuildKonfig は
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

buildkonfig {
    packageName = "studio.nxtech.fujubank"
    objectName = "BuildKonfig"

    // デフォルト（debug 相当）: Android エミュレータからホストの localhost を叩く 10.0.2.2 を使う。
    // AUTHCORE は別リポジトリ (`fuju-system-authentication`) のサービスでローカルは :8080。
    defaultConfigs {
        buildConfigField(STRING, "BANK_API_BASE_URL", "http://10.0.2.2:3000")
        buildConfigField(STRING, "CABLE_URL", "ws://10.0.2.2:3000/cable")
        buildConfigField(STRING, "AUTHCORE_BASE_URL", "http://10.0.2.2:8080")
    }

    // Release ビルドでは本番 API を向ける。`-Pbuildkonfig.flavor=release` で切り替え。
    // AUTHCORE_BASE_URL の release 値は backend B4 確定までの暫定。確定後に追従 PR を 1 本当てる。
    defaultConfigs("release") {
        buildConfigField(STRING, "BANK_API_BASE_URL", "https://api.fujupay.app")
        buildConfigField(STRING, "CABLE_URL", "wss://api.fujupay.app/cable")
        buildConfigField(STRING, "AUTHCORE_BASE_URL", "https://authcore.fujupay.app")
    }

    // iOS シミュレータは Mac 上の localhost に直接アクセスできるため上書きする。
    targetConfigs {
        create("iosArm64") {
            buildConfigField(STRING, "BANK_API_BASE_URL", "http://localhost:3000")
            buildConfigField(STRING, "CABLE_URL", "ws://localhost:3000/cable")
            buildConfigField(STRING, "AUTHCORE_BASE_URL", "http://localhost:8080")
        }
        create("iosSimulatorArm64") {
            buildConfigField(STRING, "BANK_API_BASE_URL", "http://localhost:3000")
            buildConfigField(STRING, "CABLE_URL", "ws://localhost:3000/cable")
            buildConfigField(STRING, "AUTHCORE_BASE_URL", "http://localhost:8080")
        }
    }

    // Release flavor 時は iOS でも本番 URL を使う。
    targetConfigs("release") {
        create("iosArm64") {
            buildConfigField(STRING, "BANK_API_BASE_URL", "https://api.fujupay.app")
            buildConfigField(STRING, "CABLE_URL", "wss://api.fujupay.app/cable")
            buildConfigField(STRING, "AUTHCORE_BASE_URL", "https://authcore.fujupay.app")
        }
        create("iosSimulatorArm64") {
            buildConfigField(STRING, "BANK_API_BASE_URL", "https://api.fujupay.app")
            buildConfigField(STRING, "CABLE_URL", "wss://api.fujupay.app/cable")
            buildConfigField(STRING, "AUTHCORE_BASE_URL", "https://authcore.fujupay.app")
        }
    }
}
