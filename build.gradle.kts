buildscript {
    // Pins the Kotlin version used by AGP's built-in Kotlin support.
    dependencies { classpath(libs.kotlin.gradle.plugin) }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
