// AGP 9 compiles Kotlin itself ("built-in Kotlin"), so :app doesn't apply
// org.jetbrains.kotlin.android. Declaring the Kotlin plugins here puts the
// catalog's Kotlin Gradle plugin version on the shared build classpath, which
// AGP then uses instead of its bundled minimum.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}
