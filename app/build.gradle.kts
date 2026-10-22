plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // Provides auto-uploading of proguard mappings to Sentry
    alias(libs.plugins.sentry)
}

/**
 * Sentry has it's own Timber integration, which is automatically added with the
 * "io.sentry.android.gradle" plugin; we do NOT want to enable this as it will result in
 * errors being reported twice (with different messages)
 * https://docs.sentry.io/platforms/android/configuration/integrations/timber/
 */
configurations.configureEach {
    exclude(group = "io.sentry", module = "sentry-android-timber")
}

// The repo no longer commits a Sentry auth token (sentry.properties), so only upload ProGuard
// mappings when one is supplied, e.g. SENTRY_AUTH_TOKEN with SENTRY_ORG and SENTRY_PROJECT.
// Without this, every minified build (JitPack's included) fails at the upload task.
sentry {
    autoUploadProguardMapping.set(providers.environmentVariable("SENTRY_AUTH_TOKEN").isPresent)
}

android {
    compileSdk = libs.versions.compileSdk.get().toInt()

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.steamclock.steamclogsample"
        minSdk = 23
        targetSdk = libs.versions.compileSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = true // Always minify so that we can test proguard
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    namespace = "com.steamclock.steamclogsample"
}

dependencies {
    implementation(project(":steamclog"))
    // Since Sentry is a dependency of steamclog, we do not have to import it again
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.material)
    // Timber is an api dependency of steamclog (#150), so the sample's direct Timber calls compile
    // without declaring Timber here, as in a consumer app.
    testImplementation(libs.junit)
}
