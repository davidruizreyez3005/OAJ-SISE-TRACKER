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

android {
    namespace = "mx.sisetracker"
    compileSdk = 37

    defaultConfig {
        applicationId = "mx.sisetracker"
        minSdk = 26
        targetSdk = 37
        // CI passes the tag and run number, so each released APK installs over the last.
        versionCode = providers.environmentVariable("SISE_VERSION_CODE").map { it.toInt() }.getOrElse(1)
        versionName = providers.environmentVariable("SISE_VERSION_NAME").getOrElse("0.1.0")
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
        release {
            signingConfig = signingConfigs.findByName("release")
            // R8 stays off until a minified build has been checked on a device.
            isMinifyEnabled = false
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
