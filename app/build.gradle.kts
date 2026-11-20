import org.gradle.api.tasks.testing.Test
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Dedicated, backed-up release key kept outside version control (.tools/ is git-ignored).
// Releases up to 1.6.5 were signed with a throwaway debug key that was later lost, which is
// why they cannot be upgraded in place. Every release from 1.6.6 on uses this key.
val releaseSigningFile = rootProject.file(".tools/signing/signing.properties")
val releaseSigning = Properties().apply {
    if (releaseSigningFile.isFile) releaseSigningFile.inputStream().use(::load)
}

room {
    schemaDirectory("$projectDir/schemas")
}

android {
    namespace = "com.pdh.cardvault"

    // API 36.1 is the highest Android SDK Platform installed in the authorized toolchain.
    //noinspection GradleDependency
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "com.pdh.cardvault"
        minSdk = 30
        // API 37 Platform is not installed and downloading SDK components is out of scope.
        //noinspection OldTargetApi
        targetSdk = 36
        versionCode = 36
        versionName = "1.9.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningFile.isFile) {
            create("release") {
                storeFile = releaseSigningFile.parentFile.resolve(
                    releaseSigning.getProperty("storeFile"),
                )
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = false
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        animationsDisabled = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = true
        // Keep lint deterministic and offline; dependency patch levels are updated explicitly.
        disable += "NewerVersionAvailable"
        disable += "AndroidGradlePluginVersion"
    }
}

configurations.configureEach {
    exclude(group = "androidx.profileinstaller", module = "profileinstaller")
}

dependencies {
    implementation(project(":sync-core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.ext.junit.ktx)
    androidTestImplementation(libs.androidx.test.runner)
}

tasks.withType<Test>().configureEach {
    dependsOn("processDebugMainManifest", "processReleaseMainManifest")
    systemProperty("cardvault.appProjectDir", projectDir.absolutePath)
    systemProperty("cardvault.appBuildDir", layout.buildDirectory.get().asFile.absolutePath)
}

tasks.register("verifySecurityManifests") {
    group = "verification"
    description = "Runs the unit tests that verify source and merged manifest security."
    dependsOn("testDebugUnitTest")
}
