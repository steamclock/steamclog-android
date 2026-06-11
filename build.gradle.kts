// Top-level build file where you can add configuration options common to all sub-projects/modules.
// Plugin and library versions are defined in gradle/libs.versions.toml.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.sentry) apply false
}

tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}
