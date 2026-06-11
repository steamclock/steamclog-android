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

android {
    compileSdk = 35

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
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isDebuggable = true
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
    testImplementation(libs.junit)
}
