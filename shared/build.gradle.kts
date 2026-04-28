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
if (!project.hasProperty("buildkonfig.flavor")) {
    val triggersRelease = gradle.startParameter.taskNames.any { name ->
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
    defaultConfigs {
        buildConfigField(STRING, "BANK_API_BASE_URL", "http://10.0.2.2:3000")
        buildConfigField(STRING, "CABLE_URL", "ws://10.0.2.2:3000/cable")
    }

    // Release ビルドでは本番 API を向ける。`-Pbuildkonfig.flavor=release` で切り替え。
    defaultConfigs("release") {
        buildConfigField(STRING, "BANK_API_BASE_URL", "https://api.fujupay.app")
        buildConfigField(STRING, "CABLE_URL", "wss://api.fujupay.app/cable")
    }

    // iOS シミュレータは Mac 上の localhost に直接アクセスできるため上書きする。
    targetConfigs {
        create("iosArm64") {
            buildConfigField(STRING, "BANK_API_BASE_URL", "http://localhost:3000")
            buildConfigField(STRING, "CABLE_URL", "ws://localhost:3000/cable")
        }
        create("iosSimulatorArm64") {
            buildConfigField(STRING, "BANK_API_BASE_URL", "http://localhost:3000")
            buildConfigField(STRING, "CABLE_URL", "ws://localhost:3000/cable")
        }
    }

    // Release flavor 時は iOS でも本番 URL を使う。
    targetConfigs("release") {
        create("iosArm64") {
            buildConfigField(STRING, "BANK_API_BASE_URL", "https://api.fujupay.app")
            buildConfigField(STRING, "CABLE_URL", "wss://api.fujupay.app/cable")
        }
        create("iosSimulatorArm64") {
            buildConfigField(STRING, "BANK_API_BASE_URL", "https://api.fujupay.app")
            buildConfigField(STRING, "CABLE_URL", "wss://api.fujupay.app/cable")
        }
    }
}
