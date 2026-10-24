plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Release builds are signed only when these are set (the release workflow sets
// them from repository secrets); otherwise assembleRelease leaves the APK unsigned.
val releaseKeystore = providers.environmentVariable("SISE_KEYSTORE_PATH").orNull

// The versionCode is the number of commits up to the build, so a newer APK,
// from CI or a local build, always installs as an update of an older one.
// Needs the full history (CI checks out with fetch-depth 0). SISE_VERSION_CODE
// overrides it.
val commitCount: Int = runCatching {
    providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
        .standardOutput.asText.get().trim().toInt()
}.getOrDefault(1)

android {
    namespace = "mx.sisetracker"
    compileSdk = 37

    defaultConfig {
        applicationId = "mx.sisetracker"
        minSdk = 26
        targetSdk = 37
        versionCode = providers.environmentVariable("SISE_VERSION_CODE").map { it.toInt() }.getOrElse(commitCount)
        // The release workflow passes the tag's version.
        versionName = providers.environmentVariable("SISE_VERSION_NAME").getOrElse("0.2.0")
        manifestPlaceholders["appLabel"] = "@string/app_name"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("SISE_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("SISE_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("SISE_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        // A separate app next to the team's release build, with its own id,
        // name and data: a debug APK is signed with whatever machine built it,
        // so it must never have to replace the real app.
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["appLabel"] = "@string/app_name_debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            // R8: about a sixth of the debug APK's size, and smoother Compose.
            // Names are kept (see proguard-rules.pro).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Jsoup (via :sise-core) needs core library desugaring with NIO on Android.
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        // Robolectric reads the app's resources and manifest.
        unitTests.isIncludeAndroidResources = true
    }

    sourceSets {
        // Unit tests read the same saved portal responses as :sise-core's.
        named("test") {
            resources.directories.add("../sise-core/src/test/resources")
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)

    implementation(project(":sise-core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.okhttp)
    implementation(libs.okhttp.coroutines)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.work.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
}
